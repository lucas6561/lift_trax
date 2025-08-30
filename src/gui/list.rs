use chrono::NaiveDate;
use clap::ValueEnum;
use eframe::egui;

use crate::models::{LiftExecution, LiftRegion, LiftType, Muscle};
use crate::weight::{Weight, WeightUnit};

use super::{GuiApp, WeightMode};

impl GuiApp {
    pub(super) fn tab_list(&mut self, ui: &mut egui::Ui) {
        ui.heading("Recorded Lifts");
        for i in 0..self.lifts.len() {
            let name = self.lifts[i].name.clone();
            let region = self.lifts[i].region;
            let main = self.lifts[i].main;
            let muscles = self.lifts[i].muscles.clone();
            let title = if muscles.is_empty() {
                name.clone()
            } else {
                let muscles_str = muscles
                    .iter()
                    .map(|m| m.to_string())
                    .collect::<Vec<_>>()
                    .join(", ");
                format!("{} [{}]", name, muscles_str)
            };
            let executions = self.lifts[i].executions.clone();
            ui.collapsing(title, |ui| {
                if self.editing_lift == Some(i) {
                    ui.horizontal(|ui| {
                        ui.label("Name:");
                        ui.text_edit_singleline(&mut self.edit_lift_name);
                    });
                    ui.horizontal(|ui| {
                        ui.label("Region:");
                        ui.selectable_value(&mut self.edit_lift_region, LiftRegion::UPPER, "Upper");
                        ui.selectable_value(&mut self.edit_lift_region, LiftRegion::LOWER, "Lower");
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
                        ui.label("Notes:");
                        ui.text_edit_singleline(&mut self.edit_lift_notes);
                    });
                    ui.horizontal(|ui| {
                        if ui.button("Save").clicked() {
                            self.save_lift_edit(i);
                        }
                        if ui.button("Cancel").clicked() {
                            self.editing_lift = None;
                            self.edit_lift_muscles.clear();
                            self.edit_muscle_select = None;
                            self.edit_lift_notes.clear();
                        }
                    });
                } else {
                    ui.horizontal(|ui| {
                        ui.label(format!("Region: {:?}", region));
                        if let Some(m) = main {
                            ui.label(format!("Main: {}", m));
                        }
                    });
                    ui.horizontal(|ui| {
                        ui.label("Muscles:");
                        for m in &muscles {
                            ui.label(m.to_string());
                        }
                        if ui.button("Edit Lift").clicked() {
                            self.editing_lift = Some(i);
                            self.edit_lift_name = name.clone();
                            self.edit_lift_region = region;
                            self.edit_lift_main = main;
                            self.edit_lift_muscles = muscles.clone();
                            self.edit_muscle_select = None;
                            self.edit_lift_notes = self.lifts[i].notes.clone();
                        }
                    });
                    if !self.lifts[i].notes.is_empty() {
                        ui.label(format!("Notes: {}", self.lifts[i].notes));
                    }
                }
                if executions.is_empty() {
                    ui.label("no records");
                } else {
                    for (j, exec) in executions.iter().enumerate() {
                        let rpe = exec.rpe.map(|r| format!(" RPE {}", r)).unwrap_or_default();
                        let notes = if exec.notes.is_empty() {
                            String::new()
                        } else {
                            format!(" - {}", exec.notes)
                        };
                        ui.horizontal(|ui| {
                            ui.label(format!(
                                "{}: {} sets x {} reps @ {} {}{}",
                                exec.date, exec.sets, exec.reps, exec.weight, rpe, notes
                            ));
                            if ui.button("Edit").clicked() {
                                self.editing_exec = Some((i, j));
                                match &exec.weight {
                                    Weight::Raw(p) => {
                                        self.edit_weight_mode = WeightMode::Weight;
                                        self.edit_weight_unit = WeightUnit::Pounds;
                                        self.edit_weight_value = format!("{}", p);
                                        self.edit_weight_left_value.clear();
                                        self.edit_weight_right_value.clear();
                                        self.edit_band_value.clear();
                                    }
                                    Weight::RawLr { left, right } => {
                                        self.edit_weight_mode = WeightMode::WeightLr;
                                        self.edit_weight_unit = WeightUnit::Pounds;
                                        self.edit_weight_left_value = format!("{}", left);
                                        self.edit_weight_right_value = format!("{}", right);
                                        self.edit_weight_value.clear();
                                        self.edit_band_value.clear();
                                    }
                                    Weight::Bands(_) => {
                                        self.edit_weight_mode = WeightMode::Bands;
                                        self.edit_band_value = exec.weight.to_string();
                                        self.edit_weight_value.clear();
                                        self.edit_weight_left_value.clear();
                                        self.edit_weight_right_value.clear();
                                    }
                                }
                                self.edit_sets = exec.sets.to_string();
                                self.edit_reps = exec.reps.to_string();
                                self.edit_date = exec.date.to_string();
                                self.edit_rpe = exec.rpe.map(|r| r.to_string()).unwrap_or_default();
                                self.edit_notes = exec.notes.clone();
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
                                    WeightMode::WeightLr,
                                    "L/R Weight",
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
                                WeightMode::WeightLr => {
                                    ui.horizontal(|ui| {
                                        ui.label("Weight:");
                                        ui.label("L:");
                                        ui.text_edit_singleline(&mut self.edit_weight_left_value);
                                        ui.label("R:");
                                        ui.text_edit_singleline(&mut self.edit_weight_right_value);
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
                                ui.label("Notes:");
                                ui.text_edit_singleline(&mut self.edit_notes);
                            });
                            ui.horizontal(|ui| {
                                if ui.button("Save").clicked() {
                                    self.save_exec_edit();
                                }
                                if ui.button("Cancel").clicked() {
                                    self.editing_exec = None;
                                    self.edit_notes.clear();
                                }
                            });
                        }
                    }
                }
            });
        }
        if let Some(err) = &self.error {
            ui.colored_label(egui::Color32::RED, err);
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
            &self.edit_lift_notes,
        ) {
            self.error = Some(e.to_string());
        } else {
            self.editing_lift = None;
            self.edit_lift_muscles.clear();
            self.edit_muscle_select = None;
            self.edit_lift_notes.clear();
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
                WeightMode::WeightLr => {
                    let left: f64 = match self.edit_weight_left_value.parse() {
                        Ok(v) => v,
                        Err(_) => {
                            self.error = Some("Invalid weight".into());
                            return;
                        }
                    };
                    let right: f64 = match self.edit_weight_right_value.parse() {
                        Ok(v) => v,
                        Err(_) => {
                            self.error = Some("Invalid weight".into());
                            return;
                        }
                    };
                    Weight::from_unit_lr(left, right, self.edit_weight_unit)
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
                notes: self.edit_notes.clone(),
            };
            if let Some(id) = exec.id {
                if let Err(e) = self.db.update_lift_execution(id, &new_exec) {
                    self.error = Some(e.to_string());
                } else {
                    self.editing_exec = None;
                    self.edit_notes.clear();
                    self.error = None;
                    self.refresh_lifts();
                }
            }
        }
    }
}
