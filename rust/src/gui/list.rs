use chrono::{Duration, Utc};
use clap::ValueEnum;
use eframe::egui;
use std::collections::BTreeMap;

use crate::models::{ExecutionSet, LiftExecution, LiftRegion, Muscle, SetMetric};
use crate::models::lift_execution::format_execution_sets;
use crate::weight::{Weight, WeightUnit};

use super::{
    GuiApp, MetricMode, WeightMode, combo_box_width, execution_form::execution_form,
    main_lift_options,
};

impl GuiApp {
    pub(super) fn tab_list(&mut self, ui: &mut egui::Ui) {
        let filter_all = |_: &LiftExecution| true;
        self.render_executions_tab(ui, "Recorded Lifts", true, "no records", &filter_all);
    }

    pub(super) fn tab_last_week(&mut self, ui: &mut egui::Ui) {
        self.render_last_week_report(ui);
    }

    fn render_executions_tab<F>(
        &mut self,
        ui: &mut egui::Ui,
        heading: &str,
        show_filters: bool,
        empty_message: &str,
        exec_filter: &F,
    ) where
        F: Fn(&LiftExecution) -> bool,
    {
        ui.heading(heading);
        if show_filters {
            self.lift_filter_ui(ui);
        }
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
                        ui.label("Type:");
                        let main_types = main_lift_options();
                        let main_opts: Vec<String> =
                            main_types.iter().map(|(_, s)| s.to_string()).collect();
                        let main_width = combo_box_width(ui, &main_opts);
                        egui::ComboBox::from_id_source(format!("edit_main_{}", i))
                            .width(main_width)
                            .selected_text(
                                self.edit_lift_main
                                    .map(|m| m.to_string())
                                    .unwrap_or_else(|| "None".to_string()),
                            )
                            .show_ui(ui, |ui| {
                                for (variant, label) in &main_types {
                                    ui.selectable_value(&mut self.edit_lift_main, *variant, *label);
                                }
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
                        let mut muscle_opts: Vec<Muscle> = Muscle::value_variants().to_vec();
                        muscle_opts.sort_by(|a, b| a.to_string().cmp(&b.to_string()));
                        let mut muscle_strings: Vec<String> =
                            muscle_opts.iter().map(|m| m.to_string()).collect();
                        muscle_strings.push("Select muscle".into());
                        let muscle_width = combo_box_width(ui, &muscle_strings);
                        egui::ComboBox::from_id_source(format!("edit_muscle_select_{}", i))
                            .width(muscle_width)
                            .selected_text(
                                self.edit_muscle_select
                                    .map(|m| m.to_string())
                                    .unwrap_or_else(|| "Select muscle".into()),
                            )
                            .show_ui(ui, |ui| {
                                for m in &muscle_opts {
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
                            ui.label(format!("Type: {}", m));
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
                        if ui.button("Delete Lift").clicked() {
                            self.lift_to_delete = Some(i);
                        }
                    });
                    if !self.lifts[i].notes.is_empty() {
                        ui.label(format!("Notes: {}", self.lifts[i].notes));
                    }
                }
                let mut has_visible_exec = false;
                for (j, exec) in executions.iter().enumerate() {
                    if !(exec_filter)(exec) {
                        continue;
                    }
                    has_visible_exec = true;
                    ui.horizontal(|ui| {
                        ui.label(exec.to_string());
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
                                    Weight::Accommodating { raw, .. } => {
                                        // Treat accommodating resistance as a simple raw weight for editing
                                        self.edit_weight_mode = WeightMode::Weight;
                                        self.edit_weight_unit = WeightUnit::Pounds;
                                        self.edit_weight_value = format!("{}", raw);
                                        self.edit_weight_left_value.clear();
                                        self.edit_weight_right_value.clear();
                                        self.edit_band_value.clear();
                                        self.edit_band_select = None;
                                    }
                                    Weight::None => {
                                        self.edit_weight_mode = WeightMode::None;
                                        self.edit_weight_value.clear();
                                        self.edit_weight_left_value.clear();
                                        self.edit_weight_right_value.clear();
                                        self.edit_band_value.clear();
                                        self.edit_band_select = None;
                                    }
                                }
                                self.edit_sets = exec.sets.len().to_string();
                                match first.metric {
                                    SetMetric::Reps(r) => {
                                        self.edit_metric_mode = MetricMode::Reps;
                                        self.edit_reps = r.to_string();
                                    }
                                    SetMetric::TimeSecs(s) => {
                                        self.edit_metric_mode = MetricMode::Time;
                                        self.edit_reps = s.to_string();
                                    }
                                    SetMetric::DistanceFeet(d) => {
                                        self.edit_metric_mode = MetricMode::Distance;
                                        self.edit_reps = d.to_string();
                                    }
                                }
                                self.edit_date = exec.date;
                                self.edit_rpe =
                                    first.rpe.map(|r| r.to_string()).unwrap_or_default();
                                self.edit_notes = exec.notes.clone();
                                self.edit_warmup = exec.warmup;
                            }
                        }
                        if let Some(id) = exec.id {
                            if ui.button("Delete").clicked() {
                                if let Err(e) = self.db.delete_lift_execution(id) {
                                    self.error = Some(e.to_string());
                                } else {
                                    self.editing_exec = None;
                                    self.error = None;
                                    self.needs_lift_refresh = true;
                                }
                            }
                        }
                    });
                    if self.editing_exec == Some((i, j)) {
                        execution_form(
                            ui,
                            "edit",
                            &mut self.edit_weight_mode,
                            &mut self.edit_weight_unit,
                            &mut self.edit_weight_value,
                            &mut self.edit_weight_left_value,
                            &mut self.edit_weight_right_value,
                            &mut self.edit_band_value,
                            &mut self.edit_band_select,
                            &mut self.chain_value,
                            &mut self.accom_mode,
                            &mut self.edit_metric_mode,
                            &mut self.edit_warmup,
                            &mut self.edit_date,
                            &mut self.edit_notes,
                            |ui, metric_mode| {
                                let metric_label = match metric_mode {
                                    MetricMode::Reps => "Reps:",
                                    MetricMode::Time => "Seconds:",
                                    MetricMode::Distance => "Feet:",
                                };
                                ui.horizontal(|ui| {
                                    ui.label(metric_label);
                                    ui.text_edit_singleline(&mut self.edit_reps);
                                });
                                ui.horizontal(|ui| {
                                    ui.label("Sets:");
                                    ui.text_edit_singleline(&mut self.edit_sets);
                                });
                                ui.horizontal(|ui| {
                                    ui.label("RPE:");
                                    ui.text_edit_singleline(&mut self.edit_rpe);
                                });
                            },
                        );
                        ui.horizontal(|ui| {
                            if ui.button("Save").clicked() {
                                self.save_exec_edit();
                            }
                            if ui.button("Cancel").clicked() {
                                self.editing_exec = None;
                                self.edit_notes.clear();
                                self.edit_band_value.clear();
                                self.edit_band_select = None;
                                self.edit_warmup = false;
                            }
                        });
                    }
                }
                if !has_visible_exec {
                    ui.label(empty_message);
                }
            });
            if self.lift_to_delete == Some(i) {
                egui::Window::new("Confirm Delete")
                    .collapsible(false)
                    .show(ui.ctx(), |ui| {
                        ui.label("Delete lift and all execution data?");
                        ui.horizontal(|ui| {
                            if ui.button("Delete").clicked() {
                                if let Err(e) = self.db.delete_lift(&name) {
                                    self.error = Some(e.to_string());
                                } else {
                                    self.error = None;
                                    self.editing_exec = None;
                                    self.needs_lift_refresh = true;
                                }
                                self.lift_to_delete = None;
                            }
                            if ui.button("Cancel").clicked() {
                                self.lift_to_delete = None;
                            }
                        });
                    });
            }
        }
        if self.needs_lift_refresh {
            self.refresh_lifts();
            self.needs_lift_refresh = false;
        }
        if let Some(err) = &self.error {
            ui.colored_label(egui::Color32::RED, err);
        }
    }

    fn render_last_week_report(&mut self, ui: &mut egui::Ui) {
        let today = Utc::now().date_naive();
        let start = today - Duration::days(6);
        let mut days: BTreeMap<_, Vec<(usize, usize)>> = BTreeMap::new();

        for (lift_idx, lift) in self.lifts.iter().enumerate() {
            for (exec_idx, exec) in lift.executions.iter().enumerate() {
                if exec.date < start || exec.date > today {
                    continue;
                }
                days.entry(exec.date)
                    .or_default()
                    .push((lift_idx, exec_idx));
            }
        }

        ui.heading("Last Week Report");
        ui.label(format!(
            "Showing {} through {}",
            start.format("%a %b %-d"),
            today.format("%a %b %-d")
        ));

        if days.is_empty() {
            ui.add_space(8.0);
            ui.label("no executions in the last 7 days");
        } else {
            let mut day_entries: Vec<_> = days.into_iter().collect();
            day_entries.sort_by(|a, b| b.0.cmp(&a.0));

            for (idx, (date, mut entries)) in day_entries.into_iter().enumerate() {
                if idx > 0 {
                    ui.add_space(12.0);
                }
                entries.sort_by(|a, b| {
                    let name_a = &self.lifts[a.0].name;
                    let name_b = &self.lifts[b.0].name;
                    name_a.cmp(name_b).then_with(|| a.1.cmp(&b.1))
                });

                ui.group(|ui| {
                    ui.heading(date.format("%A, %B %-d").to_string());
                    ui.add_space(4.0);
                    for (lift_idx, exec_idx) in entries {
                        let lift = &self.lifts[lift_idx];
                        let exec = &lift.executions[exec_idx];
                        ui.label(format!(
                            "{}: {}",
                            lift.name,
                            Self::execution_summary(exec)
                        ));
                    }
                });
            }
        }

        if self.needs_lift_refresh {
            self.refresh_lifts();
            self.needs_lift_refresh = false;
        }
        if let Some(err) = &self.error {
            ui.add_space(8.0);
            ui.colored_label(egui::Color32::RED, err);
        }
    }

    fn execution_summary(exec: &LiftExecution) -> String {
        let warmup = if exec.warmup { " (warm-up)" } else { "" };
        let notes = if exec.notes.is_empty() {
            String::new()
        } else {
            format!(" – {}", exec.notes)
        };
        format!("{}{}{}", format_execution_sets(&exec.sets), warmup, notes)
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
            self.needs_lift_refresh = true;
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
                WeightMode::Accommodating => {
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
                WeightMode::None => Weight::None,
            };
            let metric_val: i32 = match self.edit_reps.parse() {
                Ok(r) => r,
                Err(_) => {
                    self.error = Some("Invalid value".into());
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
            let metric = match self.edit_metric_mode {
                MetricMode::Reps => SetMetric::Reps(metric_val),
                MetricMode::Time => SetMetric::TimeSecs(metric_val),
                MetricMode::Distance => SetMetric::DistanceFeet(metric_val),
            };
            let set = ExecutionSet {
                metric,
                weight: weight.clone(),
                rpe,
            };
            let sets_vec = vec![set; sets as usize];
            let new_exec = LiftExecution {
                id: exec.id,
                date,
                sets: sets_vec,
                warmup: self.edit_warmup,
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
                    self.edit_warmup = false;
                    self.error = None;
                    self.needs_lift_refresh = true;
                }
            }
        }
    }
}
