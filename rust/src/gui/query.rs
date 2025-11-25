use chrono::{Duration, Utc};
use eframe::egui;

use super::{GuiApp, combo_box_width};

impl GuiApp {
    pub(super) fn tab_query(&mut self, ui: &mut egui::Ui) {
        ui.heading("Query Lift");
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
            egui::ComboBox::from_id_source("query_lift_select")
                .width(width)
                .selected_text(selected)
                .show_ui(ui, |ui| {
                    for (i, lift) in self.lifts.iter().enumerate() {
                        ui.selectable_value(&mut self.selected_lift, Some(i), &lift.name);
                    }
                });
        });
        if let Some(idx) = self.selected_lift {
            let lift = &self.lifts[idx];
            match self.db.lift_stats(&lift.name) {
                Ok(stats) => {
                    ui.heading("Last Year");
                    let one_year_ago = Utc::now().date_naive() - Duration::days(365);
                    let mut any = false;
                    let all_execs = self.db.get_executions(&lift.name);
                    for exec in all_execs.iter().filter(|e| e.date >= one_year_ago) {
                        ui.label(exec.to_string());
                        any = true;
                    }
                    if !any {
                        ui.label("no records");
                    }
                    ui.separator();
                    ui.heading("Best by reps");
                    for (reps, weight) in stats.best_by_reps {
                        ui.label(format!("{} reps: {}", reps, weight));
                    }
                }
                Err(e) => {
                    self.error = Some(e.to_string());
                }
            }
        }
        if let Some(err) = &self.error {
            ui.colored_label(egui::Color32::RED, err);
        }
    }
}
