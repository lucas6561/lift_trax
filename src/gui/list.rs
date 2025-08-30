use clap::ValueEnum;
use eframe::egui;
use egui_extras::DatePickerButton;

use crate::models::{ExecutionSet, LiftExecution, LiftRegion, LiftType, Muscle};
use crate::weight::{BandColor, Weight, WeightUnit};

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
                                ui.selectable_value(
                                    &mut self.edit_lift_main,
                                    Some(LiftType::WarmUp),
                                    "Warm Up",
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
                        let notes = if exec.notes.is_empty() {
                            String::new()
                        } else {
                            format!(" - {}", exec.notes)
                        };
                        let set_desc = exec
                            .sets
                            .iter()
                            .map(|s| {
                                let rpe = s.rpe.map(|r| format!(" RPE {}", r)).unwrap_or_default();
                                format!("{} reps @ {}{}", s.reps, s.weight, rpe)
                            })
                            .collect::<Vec<_>>()
                            .join(", ");
                        ui.horizontal(|ui| {
                            ui.label(format!("{}: {}{}", exec.date, set_desc, notes));
                            if ui.button("Edit").clicked() {
                                self.editing_exec = Some((i, j));
                                if let Some(first) = exec.sets.first() {
                                    match &first.weight {
                                        Weight::Raw(p) => {
                                            self.edit_weight_mode = WeightMode::Weight;
                                            self.edit_weight_unit = WeightUnit::Pounds;
                                            self.edit_weight_value = format!("{}", p);
                                            self.edit_weight_left_value.clear();
                                            self.edit_weight_right_value.clear();
                                            self.edit_band_value.clear();
                                            self.edit_band_select = None;
                                        }
                                        Weight::RawLr { left, right } => {
                                            self.edit_weight_mode = WeightMode::WeightLr;
                                            self.edit_weight_unit = WeightUnit::Pounds;
                                            self.edit_weight_left_value = format!("{}", left);
                                            self.edit_weight_right_value = format!("{}", right);
                                            self.edit_weight_value.clear();
                                            self.edit_band_value.clear();
                                            self.edit_band_select = None;
                                        }
                                        Weight::Bands(bands) => {
                                            self.edit_weight_mode = WeightMode::Bands;
                                            self.edit_band_value = bands.clone();
                                            self.edit_band_select = None;
                                            self.edit_weight_value.clear();
                                            self.edit_weight_left_value.clear();
                                            self.edit_weight_right_value.clear();
                                        }
                                    }
                                    self.edit_sets = exec.sets.len().to_string();
                                    self.edit_reps = first.reps.to_string();
                                    self.edit_date = exec.date;
                                    self.edit_rpe = first.rpe.map(|r| r.to_string()).unwrap_or_default();
                                    self.edit_notes = exec.notes.clone();
                                }
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
                                        let text = if self.edit_band_value.is_empty() {
                                            "None".to_string()
                                        } else {
                                            self.edit_band_value
                                                .iter()
                                                .map(|b| b.to_string())
                                                .collect::<Vec<_>>()
                                                .join("+")
                                        };
                                        ui.label(text);
                                        egui::ComboBox::from_id_source(format!(
                                            "edit_band_select_{}_{}",
                                            i, j
                                        ))
                                        .selected_text(
                                            self.edit_band_select
                                                .map(|b| b.to_string())
                                                .unwrap_or_else(|| "Select".into()),
                                        )
                                        .show_ui(
                                            ui,
                                            |ui| {
                                                for color in [
                                                    BandColor::Orange,
                                                    BandColor::Red,
                                                    BandColor::Blue,
                                                    BandColor::Green,
                                                    BandColor::Black,
                                                    BandColor::Purple,
                                                ] {
                                                    ui.selectable_value(
                                                        &mut self.edit_band_select,
                                                        Some(color),
                                                        color.to_string(),
                                                    );
                                                }
                                            },
                                        );
                                        if ui.button("Add").clicked() {
                                            if let Some(color) = self.edit_band_select {
                                                self.edit_band_value.push(color);
                                            }
                                        }
                                        if ui.button("Clear").clicked() {
                                            self.edit_band_value.clear();
                                            self.edit_band_select = None;
                                        }
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
                                ui.label("Date:");
                                ui.add(DatePickerButton::new(&mut self.edit_date).id_source("edit_date"));
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
                                    self.edit_band_value.clear();
                                    self.edit_band_select = None;
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
                WeightMode::Bands => {
                    if self.edit_band_value.is_empty() {
                        self.error = Some("No bands selected".into());
                        return;
                    }
                    Weight::Bands(self.edit_band_value.clone())
                }
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
            let date = self.edit_date;
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
            let set = ExecutionSet {
                reps,
                weight: weight.clone(),
                rpe,
            };
            let sets_vec = vec![set; sets as usize];
            let new_exec = LiftExecution {
                id: exec.id,
                date,
                sets: sets_vec,
                notes: self.edit_notes.clone(),
            };
            if let Some(id) = exec.id {
                if let Err(e) = self.db.update_lift_execution(id, &new_exec) {
                    self.error = Some(e.to_string());
                } else {
                    self.editing_exec = None;
                    self.edit_notes.clear();
                    self.edit_band_value.clear();
                    self.edit_band_select = None;
                    self.error = None;
                    self.refresh_lifts();
                }
            }
        }
    }
}
