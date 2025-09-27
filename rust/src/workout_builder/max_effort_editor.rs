use std::{
    env,
    sync::mpsc::{self, Sender, TryRecvError},
};

use eframe::{App, Frame, NativeOptions, egui};

use crate::{database::DbResult, models::Lift};

pub fn edit_max_effort_plan(
    lower_options: Vec<Lift>,
    upper_options: Vec<Lift>,
    default_lower: Vec<Lift>,
    default_upper: Vec<Lift>,
) -> DbResult<(Vec<Lift>, Vec<Lift>)> {
    if lower_options.is_empty() || upper_options.is_empty() {
        return Ok((default_lower, default_upper));
    }

    if cfg!(test) {
        return Ok((default_lower, default_upper));
    }

    if env::var("CARGO_TEST").is_ok() || env::var("LIFT_TRAX_HEADLESS").is_ok() {
        return Ok((default_lower, default_upper));
    }

    let lower_indices = indices_for_defaults(&lower_options, &default_lower);
    let upper_indices = indices_for_defaults(&upper_options, &default_upper);

    let (tx, rx) = mpsc::channel();
    let app = MaxEffortEditorApp::new(
        lower_options.clone(),
        upper_options.clone(),
        lower_indices.clone(),
        upper_indices.clone(),
        tx,
    );

    if let Err(err) = eframe::run_native(
        "Max Effort Planner",
        NativeOptions::default(),
        Box::new(|_cc| Box::new(app)),
    ) {
        eprintln!("Failed to launch max effort editor: {err}");
        return Ok((default_lower, default_upper));
    }

    let (final_lower_indices, final_upper_indices) = match rx.try_recv() {
        Ok(indices) => indices,
        Err(TryRecvError::Empty) | Err(TryRecvError::Disconnected) => {
            (lower_indices, upper_indices)
        }
    };

    let lower_plan = final_lower_indices
        .into_iter()
        .map(|idx| lower_options[idx].clone())
        .collect();
    let upper_plan = final_upper_indices
        .into_iter()
        .map(|idx| upper_options[idx].clone())
        .collect();

    Ok((lower_plan, upper_plan))
}

fn indices_for_defaults(options: &[Lift], defaults: &[Lift]) -> Vec<usize> {
    defaults
        .iter()
        .map(|lift| {
            options
                .iter()
                .position(|opt| opt.name == lift.name)
                .unwrap_or(0)
        })
        .collect()
}

struct MaxEffortEditorApp {
    lower_options: Vec<Lift>,
    upper_options: Vec<Lift>,
    lower_selection: Vec<usize>,
    upper_selection: Vec<usize>,
    sender: Option<Sender<(Vec<usize>, Vec<usize>)>>,
}

impl MaxEffortEditorApp {
    fn new(
        lower_options: Vec<Lift>,
        upper_options: Vec<Lift>,
        lower_selection: Vec<usize>,
        upper_selection: Vec<usize>,
        sender: Sender<(Vec<usize>, Vec<usize>)>,
    ) -> Self {
        Self {
            lower_options,
            upper_options,
            lower_selection,
            upper_selection,
            sender: Some(sender),
        }
    }

    fn selection_row(
        ui: &mut egui::Ui,
        id_prefix: &str,
        label: &str,
        idx: usize,
        selection: &mut [usize],
        options: &[Lift],
    ) {
        let mut value = selection[idx];
        let mut move_up = false;
        let mut move_down = false;
        let selection_len = selection.len();

        ui.push_id((id_prefix, idx), |ui| {
            ui.horizontal(|ui| {
                ui.label(label);
                let current_name = options
                    .get(value)
                    .map(|lift| lift.name.clone())
                    .unwrap_or_else(|| "Unknown".to_string());

                egui::ComboBox::from_id_source("combo")
                    .selected_text(current_name)
                    .show_ui(ui, |combo_ui| {
                        for (opt_idx, lift) in options.iter().enumerate() {
                            combo_ui.selectable_value(&mut value, opt_idx, lift.name.clone());
                        }
                    });

                if ui.add_enabled(idx > 0, egui::Button::new("↑")).clicked() {
                    move_up = true;
                }
                if ui
                    .add_enabled(idx + 1 < selection_len, egui::Button::new("↓"))
                    .clicked()
                {
                    move_down = true;
                }
            });
        });

        selection[idx] = value;
        if move_up {
            selection.swap(idx, idx - 1);
        }
        if move_down {
            selection.swap(idx, idx + 1);
        }
    }

    fn render_section(
        ui: &mut egui::Ui,
        title: &str,
        id_prefix: &str,
        selection: &mut [usize],
        options: &[Lift],
    ) {
        ui.heading(title);
        if options.is_empty() {
            ui.label("No lifts available for this section.");
            return;
        }
        for week_idx in 0..selection.len() {
            let label = format!("Week {}:", week_idx + 1);
            Self::selection_row(ui, id_prefix, &label, week_idx, selection, options);
        }
    }
}

impl App for MaxEffortEditorApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut Frame) {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.heading("Customize Max Effort Rotation");
            ui.label(
                "Adjust the lift used for each week's max effort day. Use the arrows to reorder the weeks.",
            );
            ui.add_space(12.0);

            Self::render_section(
                ui,
                "Lower Body Max Effort",
                "lower",
                &mut self.lower_selection,
                &self.lower_options,
            );
            ui.add_space(16.0);
            Self::render_section(
                ui,
                "Upper Body Max Effort",
                "upper",
                &mut self.upper_selection,
                &self.upper_options,
            );

            ui.add_space(20.0);
            ui.separator();
            ui.add_space(8.0);
            ui.horizontal(|ui| {
                if ui.button("Save & Generate").clicked() {
                    if let Some(sender) = self.sender.take() {
                        let _ = sender.send((
                            self.lower_selection.clone(),
                            self.upper_selection.clone(),
                        ));
                    }
                    ui.ctx()
                        .send_viewport_cmd(egui::ViewportCommand::Close);
                }
                if ui.button("Use Defaults").clicked() {
                    ui.ctx()
                        .send_viewport_cmd(egui::ViewportCommand::Close);
                }
            });
            ui.add_space(4.0);
            ui.label("Close the window at any time to keep the defaults.");
        });
    }
}
