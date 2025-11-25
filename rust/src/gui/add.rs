use clap::ValueEnum;
use eframe::egui;

use crate::models::{ExecutionSet, LiftExecution, LiftRegion, Muscle, SetMetric};
#[allow(unused_imports)]
use crate::weight::{AccommodatingResist, Weight, WeightUnit};

use super::{
    AccommodatingMode, GuiApp, MetricMode, SetMode, WeightMode, combo_box_width,
    execution_form::execution_form, main_lift_options,
};

impl GuiApp {
    pub(super) fn tab_add(&mut self, ui: &mut egui::Ui, ctx: &egui::Context) {
        ui.heading("Add Lift Execution");
        self.lift_filter_ui(ui);
        ui.horizontal(|ui| {
            ui.label("Lift:");
            let selected = self
                .selected_lift
                .and_then(|i| self.lifts.get(i))
                .map(|l| l.name.as_str())
                .unwrap_or("Select lift");
            let mut options: Vec<String> = self.lifts.iter().map(|l| l.name.clone()).collect();
            options.push("Select lift".into());
            let width = combo_box_width(ui, &options);
            egui::ComboBox::from_id_source("lift_select")
                .width(width)
                .selected_text(selected)
                .show_ui(ui, |ui| {
                    for (i, lift) in self.lifts.iter().enumerate() {
                        ui.selectable_value(&mut self.selected_lift, Some(i), &lift.name);
                    }
                });
            if ui.button("New Lift").clicked() {
                self.show_new_lift = true;
            }
            if ui.button("Load Last Execution").clicked() {
                self.load_last_execution();
            }
        });
        let mut add_set_clicked = false;
        execution_form(
            ui,
            "add",
            &mut self.weight_mode,
            &mut self.weight_unit,
            &mut self.weight_value,
            &mut self.weight_left_value,
            &mut self.weight_right_value,
            &mut self.band_value,
            &mut self.band_select,
            &mut self.chain_value,
            &mut self.accom_mode,
            &mut self.metric_mode,
            &mut self.warmup,
            &mut self.deload,
            &mut self.date,
            &mut self.notes,
            |ui, metric_mode| {
                ui.horizontal(|ui| {
                    ui.label("Set Input:");
                    ui.selectable_value(&mut self.set_mode, SetMode::Simple, "Sets x Value");
                    ui.selectable_value(&mut self.set_mode, SetMode::Detailed, "Individual Sets");
                });
                match self.set_mode {
                    SetMode::Simple => {
                        let metric_label = match metric_mode {
                            MetricMode::Reps => "Reps:",
                            MetricMode::Time => "Seconds:",
                            MetricMode::Distance => "Feet:",
                        };
                        ui.horizontal(|ui| {
                            ui.label("Sets:");
                            ui.text_edit_singleline(&mut self.sets);
                        });
                        ui.horizontal(|ui| {
                            ui.label(metric_label);
                            ui.text_edit_singleline(&mut self.reps);
                        });
                        ui.horizontal(|ui| {
                            ui.label("RPE:");
                            ui.text_edit_singleline(&mut self.rpe);
                        });
                    }
                    SetMode::Detailed => {
                        let metric_label = match metric_mode {
                            MetricMode::Reps => "Reps:",
                            MetricMode::Time => "Seconds:",
                            MetricMode::Distance => "Feet:",
                        };
                        ui.horizontal(|ui| {
                            ui.label(metric_label);
                            ui.text_edit_singleline(&mut self.reps);
                            ui.label("RPE:");
                            ui.text_edit_singleline(&mut self.rpe);
                            if ui.button("Add Set").clicked() {
                                add_set_clicked = true;
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
            },
        );
        if add_set_clicked {
            self.add_detail_set();
        }
        ui.horizontal(|ui| {
            if ui.button("Add").clicked() {
                self.add_execution();
            }
        });
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
                ui.label("Type:");
                let main_types = main_lift_options();
                let main_opts: Vec<String> =
                    main_types.iter().map(|(_, s)| s.to_string()).collect();
                let main_width = combo_box_width(ui, &main_opts);
                egui::ComboBox::from_id_source("main_lift")
                    .width(main_width)
                    .selected_text(
                        self.new_lift_main
                            .map(|m| m.to_string())
                            .unwrap_or_else(|| "None".to_string()),
                    )
                    .show_ui(ui, |ui| {
                        for (variant, label) in &main_types {
                            ui.selectable_value(&mut self.new_lift_main, *variant, *label);
                        }
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
                let mut muscle_opts: Vec<Muscle> = Muscle::value_variants().to_vec();
                muscle_opts.sort_by(|a, b| a.to_string().cmp(&b.to_string()));
                let mut muscle_strings: Vec<String> =
                    muscle_opts.iter().map(|m| m.to_string()).collect();
                muscle_strings.push("Select muscle".into());
                let muscle_width = combo_box_width(ui, &muscle_strings);
                egui::ComboBox::from_id_source("new_muscle_select")
                    .width(muscle_width)
                    .selected_text(
                        self.new_muscle_select
                            .map(|m| m.to_string())
                            .unwrap_or_else(|| "Select muscle".into()),
                    )
                    .show_ui(ui, |ui| {
                        for m in &muscle_opts {
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
            WeightMode::Accommodating => {
                let raw: f64 = match self.weight_value.parse() {
                    Ok(v) => v,
                    Err(_) => {
                        self.error = Some("Invalid weight".into());
                        return;
                    }
                };
                let raw_lbs = match Weight::from_unit(raw, self.weight_unit) {
                    Weight::Raw(p) => p,
                    _ => unreachable!(),
                };
                let resistance = match self.accom_mode {
                    AccommodatingMode::Chains => {
                        let chain: f64 = match self.chain_value.parse() {
                            Ok(v) => v,
                            Err(_) => {
                                self.error = Some("Invalid chain weight".into());
                                return;
                            }
                        };
                        let chain_lbs = match Weight::from_unit(chain, self.weight_unit) {
                            Weight::Raw(p) => p,
                            _ => unreachable!(),
                        };
                        AccommodatingResist::Chains(chain_lbs)
                    }
                    AccommodatingMode::Bands => {
                        if self.band_value.is_empty() {
                            self.error = Some("No bands selected".into());
                            return;
                        }
                        AccommodatingResist::Bands(self.band_value.clone())
                    }
                };
                Weight::Accommodating {
                    raw: raw_lbs,
                    resistance,
                }
            }
            WeightMode::None => Weight::None,
        };
        let metric_val: i32 = match self.reps.parse() {
            Ok(r) => r,
            Err(_) => {
                self.error = Some("Invalid value".into());
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
        let metric = match self.metric_mode {
            MetricMode::Reps => SetMetric::Reps(metric_val),
            MetricMode::Time => SetMetric::TimeSecs(metric_val),
            MetricMode::Distance => SetMetric::DistanceFeet(metric_val),
        };
        self.detailed_sets.push(ExecutionSet {
            metric,
            weight,
            rpe,
        });
        self.weight_value.clear();
        self.weight_left_value.clear();
        self.weight_right_value.clear();
        self.band_value.clear();
        self.band_select = None;
        self.chain_value.clear();
        self.accom_mode = AccommodatingMode::Chains;
        self.reps.clear();
        self.rpe.clear();
    }

    fn load_last_execution(&mut self) {
        let Some(lift_idx) = self.selected_lift else {
            self.error = Some("No lift selected".into());
            return;
        };
        let Some(lift) = self.lifts.get(lift_idx) else {
            self.error = Some("No previous executions for this lift".into());
            return;
        };
        let executions = self.db.get_executions(&lift.name);
        let exec = executions
            .iter()
            .find(|exec| exec.warmup == self.warmup && exec.deload == self.deload)
            .cloned();
        let Some(exec) = exec else {
            self.error =
                Some("No previous executions for this lift with the selected options".into());
            return;
        };
        self.warmup = exec.warmup;
        self.deload = exec.deload;
        self.notes = exec.notes.clone();
        let sets = exec.sets.clone();
        if let Some(first) = sets.first() {
            self.apply_weight_to_add_form(&first.weight);
            let (metric_mode, metric_value) = Self::metric_mode_and_value(&first.metric);
            self.metric_mode = metric_mode;
            self.reps = metric_value.map(|v| v.to_string()).unwrap_or_default();
            self.rpe = first.rpe.map(|r| r.to_string()).unwrap_or_default();
            let uniform_sets = metric_value.is_some() && sets.iter().all(|set| set == first);
            if uniform_sets {
                self.set_mode = SetMode::Simple;
                self.sets = sets.len().to_string();
                self.detailed_sets.clear();
            } else {
                self.set_mode = SetMode::Detailed;
                self.detailed_sets = sets.clone();
                self.sets.clear();
            }
        } else {
            self.set_mode = SetMode::Simple;
            self.apply_weight_to_add_form(&Weight::None);
            self.metric_mode = MetricMode::Reps;
            self.reps.clear();
            self.sets.clear();
            self.rpe.clear();
            self.detailed_sets.clear();
        }
        self.error = None;
    }

    fn apply_weight_to_add_form(&mut self, weight: &Weight) {
        match weight {
            Weight::Raw(p) => {
                self.weight_mode = WeightMode::Weight;
                self.weight_unit = WeightUnit::Pounds;
                self.weight_value = format!("{}", p);
                self.weight_left_value.clear();
                self.weight_right_value.clear();
                self.band_value.clear();
                self.band_select = None;
                self.chain_value.clear();
                self.accom_mode = AccommodatingMode::Chains;
            }
            Weight::RawLr { left, right } => {
                self.weight_mode = WeightMode::WeightLr;
                self.weight_unit = WeightUnit::Pounds;
                self.weight_left_value = format!("{}", left);
                self.weight_right_value = format!("{}", right);
                self.weight_value.clear();
                self.band_value.clear();
                self.band_select = None;
                self.chain_value.clear();
                self.accom_mode = AccommodatingMode::Chains;
            }
            Weight::Bands(bands) => {
                self.weight_mode = WeightMode::Bands;
                self.weight_value.clear();
                self.weight_left_value.clear();
                self.weight_right_value.clear();
                self.band_value = bands.clone();
                self.band_select = None;
                self.chain_value.clear();
                self.accom_mode = AccommodatingMode::Chains;
            }
            Weight::Accommodating { raw, resistance } => {
                self.weight_mode = WeightMode::Accommodating;
                self.weight_unit = WeightUnit::Pounds;
                self.weight_value = format!("{}", raw);
                self.weight_left_value.clear();
                self.weight_right_value.clear();
                self.band_select = None;
                match resistance {
                    AccommodatingResist::Chains(c) => {
                        self.accom_mode = AccommodatingMode::Chains;
                        self.chain_value = format!("{}", c);
                        self.band_value.clear();
                    }
                    AccommodatingResist::Bands(bands) => {
                        self.accom_mode = AccommodatingMode::Bands;
                        self.chain_value.clear();
                        self.band_value = bands.clone();
                    }
                }
            }
            Weight::None => {
                self.weight_mode = WeightMode::None;
                self.weight_value.clear();
                self.weight_left_value.clear();
                self.weight_right_value.clear();
                self.band_value.clear();
                self.band_select = None;
                self.chain_value.clear();
                self.accom_mode = AccommodatingMode::Chains;
            }
        }
    }

    fn metric_mode_and_value(metric: &SetMetric) -> (MetricMode, Option<i32>) {
        match metric {
            SetMetric::Reps(v) => (MetricMode::Reps, Some(*v)),
            SetMetric::TimeSecs(v) => (MetricMode::Time, Some(*v)),
            SetMetric::DistanceFeet(v) => (MetricMode::Distance, Some(*v)),
            SetMetric::RepsRange { .. } => (MetricMode::Reps, None),
        }
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
                    WeightMode::Accommodating => {
                        let raw: f64 = match self.weight_value.parse() {
                            Ok(v) => v,
                            Err(_) => {
                                self.error = Some("Invalid weight".into());
                                return;
                            }
                        };
                        let raw_lbs = match Weight::from_unit(raw, self.weight_unit) {
                            Weight::Raw(p) => p,
                            _ => unreachable!(),
                        };
                        let resistance = match self.accom_mode {
                            AccommodatingMode::Chains => {
                                let chain: f64 = match self.chain_value.parse() {
                                    Ok(v) => v,
                                    Err(_) => {
                                        self.error = Some("Invalid chain weight".into());
                                        return;
                                    }
                                };
                                let chain_lbs = match Weight::from_unit(chain, self.weight_unit) {
                                    Weight::Raw(p) => p,
                                    _ => unreachable!(),
                                };
                                AccommodatingResist::Chains(chain_lbs)
                            }
                            AccommodatingMode::Bands => {
                                if self.band_value.is_empty() {
                                    self.error = Some("No bands selected".into());
                                    return;
                                }
                                AccommodatingResist::Bands(self.band_value.clone())
                            }
                        };
                        Weight::Accommodating {
                            raw: raw_lbs,
                            resistance,
                        }
                    }
                    WeightMode::None => Weight::None,
                };
                let metric_val: i32 = match self.reps.parse() {
                    Ok(r) => r,
                    Err(_) => {
                        self.error = Some("Invalid value".into());
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
                let metric = match self.metric_mode {
                    MetricMode::Reps => SetMetric::Reps(metric_val),
                    MetricMode::Time => SetMetric::TimeSecs(metric_val),
                    MetricMode::Distance => SetMetric::DistanceFeet(metric_val),
                };
                let set = ExecutionSet {
                    metric,
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
            warmup: self.warmup,
            deload: self.deload,
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
                self.chain_value.clear();
                self.accom_mode = AccommodatingMode::Chains;
                self.reps.clear();
                self.sets.clear();
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
        let main = match self.new_lift_main {
            Some(m) => m,
            None => {
                self.error = Some("Lift type required".into());
                return;
            }
        };
        let name_owned = name.to_string();
        match self.db.add_lift(
            &name_owned,
            self.new_lift_region,
            main,
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
