use std::collections::BTreeMap;

use eframe::egui;

use crate::weight::Weight;

use super::GuiApp;

impl GuiApp {
    pub(super) fn tab_query(&mut self, ui: &mut egui::Ui) {
        ui.heading("Query Lift");
        ui.horizontal(|ui| {
            ui.label("Lift:");
            let selected = self
                .selected_lift
                .and_then(|i| self.lifts.get(i))
                .map(|l| l.name.as_str())
                .unwrap_or("Select lift");
            egui::ComboBox::from_id_source("query_lift_select")
                .selected_text(selected)
                .show_ui(ui, |ui| {
                    for (i, lift) in self.lifts.iter().enumerate() {
                        ui.selectable_value(&mut self.selected_lift, Some(i), &lift.name);
                    }
                });
        });
        if let Some(idx) = self.selected_lift {
            let lift = &self.lifts[idx];
            if let Some(last) = lift.executions.first() {
                let rpe = last.rpe.map(|r| format!(" RPE {}", r)).unwrap_or_default();
                ui.label(format!(
                    "Last: {} sets x {} reps @ {}{} on {}",
                    last.sets, last.reps, last.weight, rpe, last.date
                ));
            } else {
                ui.label("no records");
            }
            ui.separator();
            ui.heading("Best by reps");
            let mut best: BTreeMap<i32, Weight> = BTreeMap::new();
            for exec in &lift.executions {
                if let Weight::Raw(p) = exec.weight {
                    best.entry(exec.reps)
                        .and_modify(|w| {
                            if let Weight::Raw(bp) = w {
                                if p > *bp {
                                    *w = exec.weight.clone();
                                }
                            }
                        })
                        .or_insert_with(|| exec.weight.clone());
                }
            }
            for (reps, weight) in best {
                ui.label(format!("{} reps: {}", reps, weight));
            }
        }
        if let Some(err) = &self.error {
            ui.colored_label(egui::Color32::RED, err);
        }
    }
}

