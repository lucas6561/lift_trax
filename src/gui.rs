use chrono::{NaiveDate, Utc};
use eframe::{Frame, NativeOptions, egui};

use crate::weight::{Weight, WeightUnit};
use crate::{
    database::Database,
    models::{Lift, LiftExecution},
};

/// Run the GUI application using the provided database implementation.
pub fn run_gui(db: Box<dyn Database>) -> Result<(), Box<dyn std::error::Error>> {
    let app = GuiApp::new(db);
    let options = NativeOptions::default();
    eframe::run_native("Lift Trax", options, Box::new(|_cc| Box::new(app)))?;
    Ok(())
}

struct GuiApp {
    db: Box<dyn Database>,
    // form fields
    weight: String,
    reps: String,
    sets: String,
    date: String,
    rpe: String,
    selected_lift: Option<usize>,
    show_new_lift: bool,
    new_lift_name: String,
    new_lift_muscles: String,
    // data display
    lifts: Vec<Lift>,
    // error message
    error: Option<String>,
}

impl GuiApp {
    fn new(db: Box<dyn Database>) -> Self {
        let mut app = Self {
            db,
            weight: String::new(),
            reps: String::new(),
            sets: String::new(),
            date: String::new(),
            rpe: String::new(),
            selected_lift: None,
            show_new_lift: false,
            new_lift_name: String::new(),
            new_lift_muscles: String::new(),
            lifts: Vec::new(),
            error: None,
        };
        app.refresh_lifts();
        app
    }

    fn refresh_lifts(&mut self) {
        match self.db.list_lifts(None) {
            Ok(l) => {
                self.lifts = l;
                if self.lifts.is_empty() {
                    self.selected_lift = None;
                } else if self.selected_lift.map_or(true, |i| i >= self.lifts.len()) {
                    self.selected_lift = Some(0);
                }
            }
            Err(e) => self.error = Some(e.to_string()),
        }
    }

    fn add_execution(&mut self) {
        let weight: Weight = match self.weight.parse::<f64>() {
            Ok(w) => Weight::new(WeightUnit::POUNDS, w),
            Err(_) => {
                self.error = Some("Invalid weight".into());
                return;
            }
        };
        let reps: i32 = match self.reps.parse() {
            Ok(r) => r,
            Err(_) => {
                self.error = Some("Invalid reps".into());
                return;
            }
        };
        let sets: i32 = match self.sets.parse() {
            Ok(s) => s,
            Err(_) => {
                self.error = Some("Invalid sets".into());
                return;
            }
        };
        let date = if self.date.trim().is_empty() {
            Utc::now().date_naive()
        } else {
            match NaiveDate::parse_from_str(&self.date, "%Y-%m-%d") {
                Ok(d) => d,
                Err(_) => {
                    self.error = Some("Invalid date".into());
                    return;
                }
            }
        };
        let rpe = if self.rpe.trim().is_empty() {
            None
        } else {
            match self.rpe.parse::<f32>() {
                Ok(r) => Some(r),
                Err(_) => {
                    self.error = Some("Invalid RPE".into());
                    return;
                }
            }
        };
        let exec = LiftExecution {
            date,
            sets,
            reps,
            weight,
            rpe,
        };
        if let Some(idx) = self.selected_lift {
            let lift = &self.lifts[idx];
            if let Err(e) = self.db.add_lift_execution(&lift.name, &lift.muscles, &exec) {
                self.error = Some(e.to_string());
            } else {
                self.weight.clear();
                self.reps.clear();
                self.sets.clear();
                self.date.clear();
                self.rpe.clear();
                self.error = None;
                self.refresh_lifts();
            }
        } else {
            self.error = Some("No lift selected".into());
        }
    }

    fn create_lift(&mut self) {
        let name = self.new_lift_name.trim();
        if name.is_empty() {
            self.error = Some("Lift name required".into());
            return;
        }
        let muscles: Vec<String> = if self.new_lift_muscles.trim().is_empty() {
            Vec::new()
        } else {
            self.new_lift_muscles
                .split(',')
                .map(|s| s.trim().to_string())
                .collect()
        };
        let name_owned = name.to_string();
        match self.db.add_lift(&name_owned, &muscles) {
            Ok(_) => {
                self.show_new_lift = false;
                self.new_lift_name.clear();
                self.new_lift_muscles.clear();
                self.error = None;
                self.refresh_lifts();
                if let Some(idx) = self.lifts.iter().position(|l| l.name == name_owned) {
                    self.selected_lift = Some(idx);
                }
            }
            Err(e) => self.error = Some(e.to_string()),
        }
    }
}

impl eframe::App for GuiApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut Frame) {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.heading("Add Lift Execution");
            ui.horizontal(|ui| {
                ui.label("Lift:");
                let selected = self
                    .selected_lift
                    .and_then(|i| self.lifts.get(i))
                    .map(|l| l.name.as_str())
                    .unwrap_or("Select lift");
                egui::ComboBox::from_id_source("lift_select")
                    .selected_text(selected)
                    .show_ui(ui, |ui| {
                        for (i, lift) in self.lifts.iter().enumerate() {
                            ui.selectable_value(&mut self.selected_lift, Some(i), &lift.name);
                        }
                    });
                if ui.button("New Lift").clicked() {
                    self.show_new_lift = true;
                }
            });
            ui.horizontal(|ui| {
                ui.label("Weight:");
                ui.text_edit_singleline(&mut self.weight);
            });
            ui.horizontal(|ui| {
                ui.label("Reps:");
                ui.text_edit_singleline(&mut self.reps);
            });
            ui.horizontal(|ui| {
                ui.label("Sets:");
                ui.text_edit_singleline(&mut self.sets);
            });
            ui.horizontal(|ui| {
                ui.label("Date (YYYY-MM-DD):");
                ui.text_edit_singleline(&mut self.date);
            });
            ui.horizontal(|ui| {
                ui.label("RPE:");
                ui.text_edit_singleline(&mut self.rpe);
            });
            if ui.button("Add").clicked() {
                self.add_execution();
            }
            if let Some(err) = &self.error {
                ui.colored_label(egui::Color32::RED, err);
            }
            if self.show_new_lift {
                ui.separator();
                ui.heading("Create New Lift");
                ui.horizontal(|ui| {
                    ui.label("Name:");
                    ui.text_edit_singleline(&mut self.new_lift_name);
                });
                ui.horizontal(|ui| {
                    ui.label("Muscles (comma-separated):");
                    ui.text_edit_singleline(&mut self.new_lift_muscles);
                });
                ui.horizontal(|ui| {
                    if ui.button("Create").clicked() {
                        self.create_lift();
                    }
                    if ui.button("Cancel").clicked() {
                        self.show_new_lift = false;
                    }
                });
            }
            ui.separator();
            ui.heading("Recorded Lifts");
            for lift in &self.lifts {
                let title = if lift.muscles.is_empty() {
                    lift.name.clone()
                } else {
                    format!("{} ({})", lift.name, lift.muscles.join(", "))
                };
                ui.collapsing(title, |ui| {
                    if lift.executions.is_empty() {
                        ui.label("no records");
                    } else {
                        for exec in &lift.executions {
                            let rpe = exec.rpe.map(|r| format!(" RPE {}", r)).unwrap_or_default();
                            ui.label(format!(
                                "{}: {} sets x {} reps @ {} {}",
                                exec.date, exec.sets, exec.reps, exec.weight, rpe
                            ));
                        }
                    }
                });
            }
        });
    }
}
