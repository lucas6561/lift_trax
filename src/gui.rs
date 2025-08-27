use chrono::{NaiveDate, Utc};
use clap::ValueEnum;
use eframe::{Frame, NativeOptions, egui};

use crate::weight::{Weight, WeightUnit};
use crate::{
    database::Database,
    models::{Lift, LiftExecution, LiftRegion, LiftType, Muscle},
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
    weight_value: String,
    band_value: String,
    weight_unit: WeightUnit,
    weight_mode: WeightMode,
    reps: String,
    sets: String,
    date: String,
    rpe: String,
    selected_lift: Option<usize>,
    show_new_lift: bool,
    new_lift_name: String,
    new_lift_region: LiftRegion,
    new_lift_main: Option<LiftType>,
    new_lift_muscles: Vec<Muscle>,
    new_muscle_select: Option<Muscle>,
    editing_lift: Option<usize>,
    edit_lift_name: String,
    edit_lift_region: LiftRegion,
    edit_lift_main: Option<LiftType>,
    edit_lift_muscles: Vec<Muscle>,
    edit_muscle_select: Option<Muscle>,
    editing_exec: Option<(usize, usize)>,
    edit_weight_value: String,
    edit_band_value: String,
    edit_weight_unit: WeightUnit,
    edit_weight_mode: WeightMode,
    edit_reps: String,
    edit_sets: String,
    edit_date: String,
    edit_rpe: String,
    // data display
    lifts: Vec<Lift>,
    // error message
    error: Option<String>,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum WeightMode {
    Weight,
    Bands,
}

impl GuiApp {
    fn new(db: Box<dyn Database>) -> Self {
        let mut app = Self {
            db,
            weight_value: String::new(),
            band_value: String::new(),
            weight_unit: WeightUnit::Pounds,
            weight_mode: WeightMode::Weight,
            reps: String::new(),
            sets: String::new(),
            date: String::new(),
            rpe: String::new(),
            selected_lift: None,
            show_new_lift: false,
            new_lift_name: String::new(),
            new_lift_region: LiftRegion::UPPER,
            new_lift_main: None,
            new_lift_muscles: Vec::new(),
            new_muscle_select: None,
            editing_lift: None,
            edit_lift_name: String::new(),
            edit_lift_region: LiftRegion::UPPER,
            edit_lift_main: None,
            edit_lift_muscles: Vec::new(),
            edit_muscle_select: None,
            editing_exec: None,
            edit_weight_value: String::new(),
            edit_band_value: String::new(),
            edit_weight_unit: WeightUnit::Pounds,
            edit_weight_mode: WeightMode::Weight,
            edit_reps: String::new(),
            edit_sets: String::new(),
            edit_date: String::new(),
            edit_rpe: String::new(),
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
        let weight = match self.weight_mode {
            WeightMode::Weight => {
                let val: f64 = match self.weight_value.parse() {
                    Ok(v) => v,
                    Err(_) => {
                        self.error = Some("Invalid weight".into());
                        return;
                    }
                };
                Weight::from_unit(val, self.weight_unit)
            }
            WeightMode::Bands => match self.band_value.parse::<Weight>() {
                Ok(w) => w,
                Err(_) => {
                    self.error = Some("Invalid bands".into());
                    return;
                }
            },
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
            id: None,
            date,
            sets,
            reps,
            weight,
            rpe,
        };
        if let Some(idx) = self.selected_lift {
            let lift = &self.lifts[idx];
            if let Err(e) = self.db.add_lift_execution(&lift.name, &exec) {
                self.error = Some(e.to_string());
            } else {
                self.weight_value.clear();
                self.band_value.clear();
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
        let name_owned = name.to_string();
        match self.db.add_lift(
            &name_owned,
            self.new_lift_region,
            self.new_lift_main,
            &self.new_lift_muscles,
        ) {
            Ok(_) => {
                self.show_new_lift = false;
                self.new_lift_name.clear();
                self.new_lift_main = None;
                self.new_lift_muscles.clear();
                self.new_muscle_select = None;
                self.error = None;
                self.refresh_lifts();
                if let Some(idx) = self.lifts.iter().position(|l| l.name == name_owned) {
                    self.selected_lift = Some(idx);
                }
            }
            Err(e) => self.error = Some(e.to_string()),
        }
    }

    fn save_lift_edit(&mut self, idx: usize) {
        let current_name = self.lifts[idx].name.clone();
        let name = self.edit_lift_name.trim();
        if name.is_empty() {
            self.error = Some("Lift name required".into());
            return;
        }
        if let Err(e) = self.db.update_lift(
            &current_name,
            name,
            self.edit_lift_region,
            self.edit_lift_main,
            &self.edit_lift_muscles,
        ) {
            self.error = Some(e.to_string());
        } else {
            self.editing_lift = None;
            self.edit_lift_muscles.clear();
            self.edit_muscle_select = None;
            self.error = None;
            self.refresh_lifts();
        }
    }

    fn save_exec_edit(&mut self) {
        if let Some((lift_idx, exec_idx)) = self.editing_exec {
            let lift = &self.lifts[lift_idx];
            let exec = &lift.executions[exec_idx];
            let weight = match self.edit_weight_mode {
                WeightMode::Weight => {
                    let val: f64 = match self.edit_weight_value.parse() {
                        Ok(v) => v,
                        Err(_) => {
                            self.error = Some("Invalid weight".into());
                            return;
                        }
                    };
                    Weight::from_unit(val, self.edit_weight_unit)
                }
                WeightMode::Bands => match self.edit_band_value.parse::<Weight>() {
                    Ok(w) => w,
                    Err(_) => {
                        self.error = Some("Invalid bands".into());
                        return;
                    }
                },
            };
            let reps: i32 = match self.edit_reps.parse() {
                Ok(r) => r,
                Err(_) => {
                    self.error = Some("Invalid reps".into());
                    return;
                }
            };
            let sets: i32 = match self.edit_sets.parse() {
                Ok(s) => s,
                Err(_) => {
                    self.error = Some("Invalid sets".into());
                    return;
                }
            };
            let date = if self.edit_date.trim().is_empty() {
                exec.date
            } else {
                match NaiveDate::parse_from_str(&self.edit_date, "%Y-%m-%d") {
                    Ok(d) => d,
                    Err(_) => {
                        self.error = Some("Invalid date".into());
                        return;
                    }
                }
            };
            let rpe = if self.edit_rpe.trim().is_empty() {
                None
            } else {
                match self.edit_rpe.parse::<f32>() {
                    Ok(r) => Some(r),
                    Err(_) => {
                        self.error = Some("Invalid RPE".into());
                        return;
                    }
                }
            };
            let new_exec = LiftExecution {
                id: exec.id,
                date,
                sets,
                reps,
                weight,
                rpe,
            };
            if let Some(id) = exec.id {
                if let Err(e) = self.db.update_lift_execution(id, &new_exec) {
                    self.error = Some(e.to_string());
                } else {
                    self.editing_exec = None;
                    self.error = None;
                    self.refresh_lifts();
                }
            }
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
                ui.label("Input:");
                ui.selectable_value(&mut self.weight_mode, WeightMode::Weight, "Weight");
                ui.selectable_value(&mut self.weight_mode, WeightMode::Bands, "Bands");
            });
            match self.weight_mode {
                WeightMode::Weight => {
                    ui.horizontal(|ui| {
                        ui.label("Weight:");
                        ui.text_edit_singleline(&mut self.weight_value);
                        egui::ComboBox::from_id_source("weight_unit")
                            .selected_text(match self.weight_unit {
                                WeightUnit::Pounds => "lb",
                                WeightUnit::Kilograms => "kg",
                            })
                            .show_ui(ui, |ui| {
                                ui.selectable_value(
                                    &mut self.weight_unit,
                                    WeightUnit::Pounds,
                                    "lb",
                                );
                                ui.selectable_value(
                                    &mut self.weight_unit,
                                    WeightUnit::Kilograms,
                                    "kg",
                                );
                            });
                    });
                }
                WeightMode::Bands => {
                    ui.horizontal(|ui| {
                        ui.label("Bands:");
                        ui.text_edit_singleline(&mut self.band_value);
                    });
                }
            }
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
                    ui.label("Region:");
                    ui.selectable_value(&mut self.new_lift_region, LiftRegion::UPPER, "Upper");
                    ui.selectable_value(&mut self.new_lift_region, LiftRegion::LOWER, "Lower");
                });
                ui.horizontal(|ui| {
                    ui.label("Main:");
                    egui::ComboBox::from_id_source("main_lift")
                        .selected_text(
                            self.new_lift_main
                                .map(|m| m.to_string())
                                .unwrap_or_else(|| "None".to_string()),
                        )
                        .show_ui(ui, |ui| {
                            ui.selectable_value(&mut self.new_lift_main, None, "None");
                            ui.selectable_value(
                                &mut self.new_lift_main,
                                Some(LiftType::BenchPress),
                                "Bench Press",
                            );
                            ui.selectable_value(
                                &mut self.new_lift_main,
                                Some(LiftType::OverheadPress),
                                "Overhead Press",
                            );
                            ui.selectable_value(
                                &mut self.new_lift_main,
                                Some(LiftType::Squat),
                                "Squat",
                            );
                            ui.selectable_value(
                                &mut self.new_lift_main,
                                Some(LiftType::Deadlift),
                                "Deadlift",
                            );
                            ui.selectable_value(
                                &mut self.new_lift_main,
                                Some(LiftType::Conditioning),
                                "Conditioning",
                            );
                            ui.selectable_value(
                                &mut self.new_lift_main,
                                Some(LiftType::Accessory),
                                "Accessory",
                            );
                        });
                });
                ui.horizontal(|ui| {
                    ui.label("Muscles:");
                    let mut remove_idx: Option<usize> = None;
                    for (i, m) in self.new_lift_muscles.iter().enumerate() {
                        ui.label(m.to_string());
                        if ui.small_button("x").clicked() {
                            remove_idx = Some(i);
                        }
                    }
                    if let Some(i) = remove_idx {
                        self.new_lift_muscles.remove(i);
                    }
                });
                ui.horizontal(|ui| {
                    egui::ComboBox::from_id_source("new_muscle_select")
                        .selected_text(
                            self.new_muscle_select
                                .map(|m| m.to_string())
                                .unwrap_or_else(|| "Select muscle".into()),
                        )
                        .show_ui(ui, |ui| {
                            for m in Muscle::value_variants() {
                                ui.selectable_value(
                                    &mut self.new_muscle_select,
                                    Some(*m),
                                    m.to_string(),
                                );
                            }
                        });
                    if ui.button("Add").clicked() {
                        if let Some(m) = self.new_muscle_select {
                            if !self.new_lift_muscles.contains(&m) {
                                self.new_lift_muscles.push(m);
                            }
                        }
                    }
                });
                ui.horizontal(|ui| {
                    if ui.button("Create").clicked() {
                        self.create_lift();
                    }
                    if ui.button("Cancel").clicked() {
                        self.show_new_lift = false;
                        self.new_lift_name.clear();
                        self.new_lift_main = None;
                        self.new_lift_muscles.clear();
                        self.new_muscle_select = None;
                    }
                });
            }
            ui.separator();
            ui.heading("Recorded Lifts");
            for i in 0..self.lifts.len() {
                let name = self.lifts[i].name.clone();
                let region = self.lifts[i].region;
                let main = self.lifts[i].main;
                let muscles = self.lifts[i].muscles.clone();
                let mut title = format!("{} ({})", name, region);
                if let Some(m) = main {
                    title.push_str(&format!(" [{}]", m));
                }
                if !muscles.is_empty() {
                    let muscles_str = muscles
                        .iter()
                        .map(|m| m.to_string())
                        .collect::<Vec<_>>()
                        .join(", ");
                    title.push_str(&format!(" [{}]", muscles_str));
                }
                let executions = self.lifts[i].executions.clone();
                ui.collapsing(title, |ui| {
                    if self.editing_lift == Some(i) {
                        ui.horizontal(|ui| {
                            ui.label("Name:");
                            ui.text_edit_singleline(&mut self.edit_lift_name);
                        });
                        ui.horizontal(|ui| {
                            ui.label("Region:");
                            ui.selectable_value(
                                &mut self.edit_lift_region,
                                LiftRegion::UPPER,
                                "Upper",
                            );
                            ui.selectable_value(
                                &mut self.edit_lift_region,
                                LiftRegion::LOWER,
                                "Lower",
                            );
                        });
                        ui.horizontal(|ui| {
                            ui.label("Main:");
                            egui::ComboBox::from_id_source(format!("edit_main_{}", i))
                                .selected_text(
                                    self.edit_lift_main
                                        .map(|m| m.to_string())
                                        .unwrap_or_else(|| "None".to_string()),
                                )
                                .show_ui(ui, |ui| {
                                    ui.selectable_value(&mut self.edit_lift_main, None, "None");
                                    ui.selectable_value(
                                        &mut self.edit_lift_main,
                                        Some(LiftType::BenchPress),
                                        "Bench Press",
                                    );
                                    ui.selectable_value(
                                        &mut self.edit_lift_main,
                                        Some(LiftType::OverheadPress),
                                        "Overhead Press",
                                    );
                                    ui.selectable_value(
                                        &mut self.edit_lift_main,
                                        Some(LiftType::Squat),
                                        "Squat",
                                    );
                                    ui.selectable_value(
                                        &mut self.edit_lift_main,
                                        Some(LiftType::Deadlift),
                                        "Deadlift",
                                    );
                                    ui.selectable_value(
                                        &mut self.edit_lift_main,
                                        Some(LiftType::Conditioning),
                                        "Conditioning",
                                    );
                                    ui.selectable_value(
                                        &mut self.edit_lift_main,
                                        Some(LiftType::Accessory),
                                        "Accessory",
                                    );
                                });
                        });
                        ui.horizontal(|ui| {
                            ui.label("Muscles:");
                            let mut remove_idx: Option<usize> = None;
                            for (j, m) in self.edit_lift_muscles.iter().enumerate() {
                                ui.label(m.to_string());
                                if ui.small_button("x").clicked() {
                                    remove_idx = Some(j);
                                }
                            }
                            if let Some(j) = remove_idx {
                                self.edit_lift_muscles.remove(j);
                            }
                        });
                        ui.horizontal(|ui| {
                            egui::ComboBox::from_id_source(format!("edit_muscle_select_{}", i))
                                .selected_text(
                                    self.edit_muscle_select
                                        .map(|m| m.to_string())
                                        .unwrap_or_else(|| "Select muscle".into()),
                                )
                                .show_ui(ui, |ui| {
                                    for m in Muscle::value_variants() {
                                        ui.selectable_value(
                                            &mut self.edit_muscle_select,
                                            Some(*m),
                                            m.to_string(),
                                        );
                                    }
                                });
                            if ui.button("Add").clicked() {
                                if let Some(m) = self.edit_muscle_select {
                                    if !self.edit_lift_muscles.contains(&m) {
                                        self.edit_lift_muscles.push(m);
                                    }
                                }
                            }
                        });
                        ui.horizontal(|ui| {
                            if ui.button("Save").clicked() {
                                self.save_lift_edit(i);
                            }
                            if ui.button("Cancel").clicked() {
                                self.editing_lift = None;
                                self.edit_lift_muscles.clear();
                                self.edit_muscle_select = None;
                            }
                        });
                    } else {
                        if ui.button("Edit Lift").clicked() {
                            self.editing_lift = Some(i);
                            self.edit_lift_name = name.clone();
                            self.edit_lift_region = region;
                            self.edit_lift_main = main;
                            self.edit_lift_muscles = muscles.clone();
                            self.edit_muscle_select = None;
                        }
                    }
                    if executions.is_empty() {
                        ui.label("no records");
                    } else {
                        for (j, exec) in executions.iter().enumerate() {
                            let rpe = exec.rpe.map(|r| format!(" RPE {}", r)).unwrap_or_default();
                            ui.horizontal(|ui| {
                                ui.label(format!(
                                    "{}: {} sets x {} reps @ {} {}",
                                    exec.date, exec.sets, exec.reps, exec.weight, rpe
                                ));
                                if ui.button("Edit").clicked() {
                                    self.editing_exec = Some((i, j));
                                    match &exec.weight {
                                        Weight::Raw(p) => {
                                            self.edit_weight_mode = WeightMode::Weight;
                                            self.edit_weight_unit = WeightUnit::Pounds;
                                            self.edit_weight_value = format!("{}", p);
                                            self.edit_band_value.clear();
                                        }
                                        Weight::Bands(_) => {
                                            self.edit_weight_mode = WeightMode::Bands;
                                            self.edit_band_value = exec.weight.to_string();
                                            self.edit_weight_value.clear();
                                        }
                                    }
                                    self.edit_sets = exec.sets.to_string();
                                    self.edit_reps = exec.reps.to_string();
                                    self.edit_date = exec.date.to_string();
                                    self.edit_rpe =
                                        exec.rpe.map(|r| r.to_string()).unwrap_or_default();
                                }
                            });
                            if self.editing_exec == Some((i, j)) {
                                ui.horizontal(|ui| {
                                    ui.label("Input:");
                                    ui.selectable_value(
                                        &mut self.edit_weight_mode,
                                        WeightMode::Weight,
                                        "Weight",
                                    );
                                    ui.selectable_value(
                                        &mut self.edit_weight_mode,
                                        WeightMode::Bands,
                                        "Bands",
                                    );
                                });
                                match self.edit_weight_mode {
                                    WeightMode::Weight => {
                                        ui.horizontal(|ui| {
                                            ui.label("Weight:");
                                            ui.text_edit_singleline(&mut self.edit_weight_value);
                                            egui::ComboBox::from_id_source(format!(
                                                "edit_unit_{}_{}",
                                                i, j
                                            ))
                                            .selected_text(match self.edit_weight_unit {
                                                WeightUnit::Pounds => "lb",
                                                WeightUnit::Kilograms => "kg",
                                            })
                                            .show_ui(
                                                ui,
                                                |ui| {
                                                    ui.selectable_value(
                                                        &mut self.edit_weight_unit,
                                                        WeightUnit::Pounds,
                                                        "lb",
                                                    );
                                                    ui.selectable_value(
                                                        &mut self.edit_weight_unit,
                                                        WeightUnit::Kilograms,
                                                        "kg",
                                                    );
                                                },
                                            );
                                        });
                                    }
                                    WeightMode::Bands => {
                                        ui.horizontal(|ui| {
                                            ui.label("Bands:");
                                            ui.text_edit_singleline(&mut self.edit_band_value);
                                        });
                                    }
                                }
                                ui.horizontal(|ui| {
                                    ui.label("Reps:");
                                    ui.text_edit_singleline(&mut self.edit_reps);
                                });
                                ui.horizontal(|ui| {
                                    ui.label("Sets:");
                                    ui.text_edit_singleline(&mut self.edit_sets);
                                });
                                ui.horizontal(|ui| {
                                    ui.label("Date (YYYY-MM-DD):");
                                    ui.text_edit_singleline(&mut self.edit_date);
                                });
                                ui.horizontal(|ui| {
                                    ui.label("RPE:");
                                    ui.text_edit_singleline(&mut self.edit_rpe);
                                });
                                ui.horizontal(|ui| {
                                    if ui.button("Save").clicked() {
                                        self.save_exec_edit();
                                    }
                                    if ui.button("Cancel").clicked() {
                                        self.editing_exec = None;
                                    }
                                });
                            }
                        }
                    }
                });
            }
        });
    }
}
