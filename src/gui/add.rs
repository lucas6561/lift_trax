use chrono::Utc;
use egui_extras::DatePickerButton;
use clap::ValueEnum;
use eframe::egui;

use crate::models::{ExecutionSet, LiftExecution, LiftRegion, LiftType, Muscle, SetMetric};
use crate::weight::{BandColor, Weight, WeightUnit};

use super::{GuiApp, SetMode, WeightMode};

impl GuiApp {
    pub(super) fn tab_add(&mut self, ui: &mut egui::Ui, ctx: &egui::Context) {
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
            ui.selectable_value(&mut self.weight_mode, WeightMode::WeightLr, "L/R Weight");
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
                            ui.selectable_value(&mut self.weight_unit, WeightUnit::Pounds, "lb");
                            ui.selectable_value(&mut self.weight_unit, WeightUnit::Kilograms, "kg");
                        });
                });
            }
            WeightMode::WeightLr => {
                ui.horizontal(|ui| {
                    ui.label("Weight:");
                    ui.label("L:");
                    ui.text_edit_singleline(&mut self.weight_left_value);
                    ui.label("R:");
                    ui.text_edit_singleline(&mut self.weight_right_value);
                    egui::ComboBox::from_id_source("weight_unit")
                        .selected_text(match self.weight_unit {
                            WeightUnit::Pounds => "lb",
                            WeightUnit::Kilograms => "kg",
                        })
                        .show_ui(ui, |ui| {
                            ui.selectable_value(&mut self.weight_unit, WeightUnit::Pounds, "lb");
                            ui.selectable_value(&mut self.weight_unit, WeightUnit::Kilograms, "kg");
                        });
                });
            }
            WeightMode::Bands => {
                ui.horizontal(|ui| {
                    ui.label("Bands:");
                    let text = if self.band_value.is_empty() {
                        "None".to_string()
                    } else {
                        self.band_value
                            .iter()
                            .map(|b| b.to_string())
                            .collect::<Vec<_>>()
                            .join("+")
                    };
                    ui.label(text);
                    egui::ComboBox::from_id_source("band_select")
                        .selected_text(
                            self.band_select
                                .map(|b| b.to_string())
                                .unwrap_or_else(|| "Select".into()),
                        )
                        .show_ui(ui, |ui| {
                            for color in [
                                BandColor::Orange,
                                BandColor::Red,
                                BandColor::Blue,
                                BandColor::Green,
                                BandColor::Black,
                                BandColor::Purple,
                            ] {
                                ui.selectable_value(
                                    &mut self.band_select,
                                    Some(color),
                                    color.to_string(),
                                );
                            }
                        });
                    if ui.button("Add").clicked() {
                        if let Some(color) = self.band_select {
                            self.band_value.push(color);
                        }
                    }
                    if ui.button("Clear").clicked() {
                        self.band_value.clear();
                        self.band_select = None;
                    }
                });
            }
        }
        ui.horizontal(|ui| {
            ui.label("Set Input:");
            ui.selectable_value(&mut self.set_mode, SetMode::Simple, "Sets x Reps");
            ui.selectable_value(&mut self.set_mode, SetMode::Detailed, "Individual Sets");
        });
        match self.set_mode {
            SetMode::Simple => {
                ui.horizontal(|ui| {
                    ui.label("Reps:");
                    ui.text_edit_singleline(&mut self.reps);
                });
                ui.horizontal(|ui| {
                    ui.label("Sets:");
                    ui.text_edit_singleline(&mut self.sets);
                });
                ui.horizontal(|ui| {
                    ui.label("RPE:");
                    ui.text_edit_singleline(&mut self.rpe);
                });
            }
            SetMode::Detailed => {
                ui.horizontal(|ui| {
                    ui.label("Reps:");
                    ui.text_edit_singleline(&mut self.reps);
                    ui.label("RPE:");
                    ui.text_edit_singleline(&mut self.rpe);
                    if ui.button("Add Set").clicked() {
                        self.add_detail_set();
                    }
                });
                let mut remove_idx: Option<usize> = None;
                for (i, set) in self.detailed_sets.iter().enumerate() {
                    let rpe = set.rpe.map(|r| format!(" RPE {}", r)).unwrap_or_default();
                    ui.horizontal(|ui| {
                    ui.label(format!(
                            "Set {}: {} @ {}{}",
                            i + 1,
                            set.metric,
                            set.weight,
                            rpe
                        ));
                        if ui.small_button("x").clicked() {
                            remove_idx = Some(i);
                        }
                    });
                }
                if let Some(i) = remove_idx {
                    self.detailed_sets.remove(i);
                }
            }
        }
        ui.horizontal(|ui| {
            ui.label("Date:");
            ui.add(DatePickerButton::new(&mut self.date).id_source("add_date"));
        });
        ui.horizontal(|ui| {
            ui.label("Notes:");
            ui.text_edit_singleline(&mut self.notes);
        });
        if ui.button("Add").clicked() {
            self.add_execution();
        }
        if ctx.input(|i| i.key_pressed(egui::Key::Enter)) && !self.show_new_lift {
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
                ui.label("Notes:");
                ui.text_edit_singleline(&mut self.new_lift_notes);
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
                    self.new_lift_notes.clear();
                }
            });
        }
    }

    fn add_detail_set(&mut self) {
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
            WeightMode::WeightLr => {
                let left: f64 = match self.weight_left_value.parse() {
                    Ok(v) => v,
                    Err(_) => {
                        self.error = Some("Invalid weight".into());
                        return;
                    }
                };
                let right: f64 = match self.weight_right_value.parse() {
                    Ok(v) => v,
                    Err(_) => {
                        self.error = Some("Invalid weight".into());
                        return;
                    }
                };
                Weight::from_unit_lr(left, right, self.weight_unit)
            }
            WeightMode::Bands => {
                if self.band_value.is_empty() {
                    self.error = Some("No bands selected".into());
                    return;
                }
                Weight::Bands(self.band_value.clone())
            }
        };
        let reps: i32 = match self.reps.parse() {
            Ok(r) => r,
            Err(_) => {
                self.error = Some("Invalid reps".into());
                return;
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
        self.detailed_sets.push(ExecutionSet { metric: SetMetric::Reps(reps), weight, rpe });
        self.weight_value.clear();
        self.weight_left_value.clear();
        self.weight_right_value.clear();
        self.band_value.clear();
        self.band_select = None;
        self.reps.clear();
        self.rpe.clear();
    }

    fn add_execution(&mut self) {
        let sets_vec = match self.set_mode {
            SetMode::Simple => {
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
                    WeightMode::WeightLr => {
                        let left: f64 = match self.weight_left_value.parse() {
                            Ok(v) => v,
                            Err(_) => {
                                self.error = Some("Invalid weight".into());
                                return;
                            }
                        };
                        let right: f64 = match self.weight_right_value.parse() {
                            Ok(v) => v,
                            Err(_) => {
                                self.error = Some("Invalid weight".into());
                                return;
                            }
                        };
                        Weight::from_unit_lr(left, right, self.weight_unit)
                    }
                    WeightMode::Bands => {
                        if self.band_value.is_empty() {
                            self.error = Some("No bands selected".into());
                            return;
                        }
                        Weight::Bands(self.band_value.clone())
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
                let set = ExecutionSet {
                    metric: SetMetric::Reps(reps),
                    weight: weight.clone(),
                    rpe,
                };
                vec![set; sets as usize]
            }
            SetMode::Detailed => {
                if self.detailed_sets.is_empty() {
                    self.error = Some("No sets entered".into());
                    return;
                }
                self.detailed_sets.clone()
            }
        };
        let date = self.date;
        let exec = LiftExecution {
            id: None,
            date,
            sets: sets_vec,
            notes: self.notes.clone(),
        };
        if let Some(idx) = self.selected_lift {
            let lift = &self.lifts[idx];
            if let Err(e) = self.db.add_lift_execution(&lift.name, &exec) {
                self.error = Some(e.to_string());
            } else {
                self.weight_value.clear();
                self.weight_left_value.clear();
                self.weight_right_value.clear();
                self.band_value.clear();
                self.band_select = None;
                self.reps.clear();
                self.sets.clear();
                self.date = Utc::now().date_naive();
                self.rpe.clear();
                self.notes.clear();
                self.detailed_sets.clear();
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
            &self.new_lift_notes,
        ) {
            Ok(_) => {
                self.show_new_lift = false;
                self.new_lift_name.clear();
                self.new_lift_main = None;
                self.new_lift_muscles.clear();
                self.new_muscle_select = None;
                self.new_lift_notes.clear();
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
