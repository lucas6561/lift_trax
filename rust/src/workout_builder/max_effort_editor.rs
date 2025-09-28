use std::{
    env,
    sync::mpsc::{self, Sender, TryRecvError},
};

use eframe::{
    App, Frame, NativeOptions,
    egui::{self, CursorIcon, Response, Shape, Stroke, Vec2},
};

use crate::{database::DbResult, models::Lift};

pub fn edit_max_effort_plan(
    lower_squat_options: Vec<Lift>,
    lower_deadlift_options: Vec<Lift>,
    upper_bench_options: Vec<Lift>,
    upper_ohp_options: Vec<Lift>,
    default_lower: Vec<Lift>,
    default_upper: Vec<Lift>,
) -> DbResult<(Vec<Lift>, Vec<Lift>)> {
    if lower_squat_options.is_empty()
        || lower_deadlift_options.is_empty()
        || upper_bench_options.is_empty()
        || upper_ohp_options.is_empty()
    {
        return Ok((default_lower, default_upper));
    }

    if default_lower.is_empty() || default_upper.is_empty() {
        return Ok((default_lower, default_upper));
    }

    if cfg!(test) {
        return Ok((default_lower, default_upper));
    }

    if env::var("CARGO_TEST").is_ok() || env::var("LIFT_TRAX_HEADLESS").is_ok() {
        return Ok((default_lower, default_upper));
    }

    let lower_squat_defaults = split_defaults_by_parity(&default_lower, 0);
    let lower_deadlift_defaults = split_defaults_by_parity(&default_lower, 1);
    let upper_bench_defaults = split_defaults_by_parity(&default_upper, 0);
    let upper_ohp_defaults = split_defaults_by_parity(&default_upper, 1);

    let lower_squat_indices = indices_for_defaults(&lower_squat_options, &lower_squat_defaults);
    let lower_deadlift_indices =
        indices_for_defaults(&lower_deadlift_options, &lower_deadlift_defaults);
    let upper_bench_indices = indices_for_defaults(&upper_bench_options, &upper_bench_defaults);
    let upper_ohp_indices = indices_for_defaults(&upper_ohp_options, &upper_ohp_defaults);

    let (tx, rx) = mpsc::channel();

    let app_lower_squat_options = lower_squat_options.clone();
    let app_lower_deadlift_options = lower_deadlift_options.clone();
    let app_upper_bench_options = upper_bench_options.clone();
    let app_upper_ohp_options = upper_ohp_options.clone();
    let app_lower_squat_indices = lower_squat_indices.clone();
    let app_lower_deadlift_indices = lower_deadlift_indices.clone();
    let app_upper_bench_indices = upper_bench_indices.clone();
    let app_upper_ohp_indices = upper_ohp_indices.clone();
    let sender = tx.clone();

    if let Err(err) = eframe::run_native(
        "Max Effort Planner",
        NativeOptions::default(),
        Box::new(move |_cc| {
            Box::new(MaxEffortEditorApp::new(
                Column::new(
                    "Squat Weeks",
                    "lower_squat",
                    0,
                    app_lower_squat_options,
                    app_lower_squat_indices,
                ),
                Column::new(
                    "Deadlift Weeks",
                    "lower_deadlift",
                    1,
                    app_lower_deadlift_options,
                    app_lower_deadlift_indices,
                ),
                Column::new(
                    "Bench Press Weeks",
                    "upper_bench",
                    0,
                    app_upper_bench_options,
                    app_upper_bench_indices,
                ),
                Column::new(
                    "Overhead Press Weeks",
                    "upper_ohp",
                    1,
                    app_upper_ohp_options,
                    app_upper_ohp_indices,
                ),
                sender.clone(),
            ))
        }),
    ) {
        eprintln!("Failed to launch max effort editor: {err}");
        return Ok((default_lower, default_upper));
    }

    let selections = match rx.try_recv() {
        Ok(indices) => indices,
        Err(TryRecvError::Empty) | Err(TryRecvError::Disconnected) => MaxEffortSelections {
            lower_squat: lower_squat_indices,
            lower_deadlift: lower_deadlift_indices,
            upper_bench: upper_bench_indices,
            upper_ohp: upper_ohp_indices,
        },
    };

    let weeks = default_lower.len();
    let lower_plan = build_plan(
        weeks,
        &selections.lower_squat,
        &selections.lower_deadlift,
        &lower_squat_options,
        &lower_deadlift_options,
    );
    let upper_plan = build_plan(
        weeks,
        &selections.upper_bench,
        &selections.upper_ohp,
        &upper_bench_options,
        &upper_ohp_options,
    );

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

fn split_defaults_by_parity(defaults: &[Lift], parity: usize) -> Vec<Lift> {
    defaults
        .iter()
        .enumerate()
        .filter_map(|(idx, lift)| (idx % 2 == parity).then(|| lift.clone()))
        .collect()
}

fn build_plan(
    weeks: usize,
    primary_selection: &[usize],
    secondary_selection: &[usize],
    primary_options: &[Lift],
    secondary_options: &[Lift],
) -> Vec<Lift> {
    let mut plan = Vec::with_capacity(weeks);
    let mut primary_idx = 0usize;
    let mut secondary_idx = 0usize;

    for week in 0..weeks {
        if week % 2 == 0 {
            let selection = primary_selection
                .get(primary_idx)
                .copied()
                .unwrap_or_else(|| *primary_selection.first().unwrap_or(&0));
            let lift = primary_options
                .get(selection)
                .or_else(|| primary_options.first())
                .expect("primary options were validated to be non-empty")
                .clone();
            plan.push(lift);
            primary_idx += 1;
        } else {
            let selection = secondary_selection
                .get(secondary_idx)
                .copied()
                .unwrap_or_else(|| *secondary_selection.first().unwrap_or(&0));
            let lift = secondary_options
                .get(selection)
                .or_else(|| secondary_options.first())
                .expect("secondary options were validated to be non-empty")
                .clone();
            plan.push(lift);
            secondary_idx += 1;
        }
    }

    plan
}

struct MaxEffortSelections {
    lower_squat: Vec<usize>,
    lower_deadlift: Vec<usize>,
    upper_bench: Vec<usize>,
    upper_ohp: Vec<usize>,
}

struct Column {
    title: &'static str,
    id_prefix: &'static str,
    first_week_index: usize,
    options: Vec<Lift>,
    selection: Vec<usize>,
}

impl Column {
    fn new(
        title: &'static str,
        id_prefix: &'static str,
        first_week_index: usize,
        options: Vec<Lift>,
        selection: Vec<usize>,
    ) -> Self {
        Self {
            title,
            id_prefix,
            first_week_index,
            options,
            selection,
        }
    }

    fn week_number(&self, idx: usize) -> usize {
        self.first_week_index + idx * 2 + 1
    }

    fn selection_row(&mut self, ui: &mut egui::Ui, idx: usize) {
        let week_number = self.week_number(idx);
        let label = format!("Week {}:", week_number);
        let mut value = self.selection[idx];
        let mut move_up = false;
        let mut move_down = false;
        let selection_len = self.selection.len();

        ui.push_id((self.id_prefix, idx), |ui| {
            ui.horizontal(|ui| {
                ui.label(&label);
                let current_name = self
                    .options
                    .get(value)
                    .map(|lift| lift.name.clone())
                    .unwrap_or_else(|| "Unknown".to_string());

                egui::ComboBox::from_id_source("combo")
                    .selected_text(current_name)
                    .show_ui(ui, |combo_ui| {
                        for (opt_idx, lift) in self.options.iter().enumerate() {
                            combo_ui.selectable_value(&mut value, opt_idx, lift.name.clone());
                        }
                    });

                if triangle_button(ui, TriangleDirection::Up, idx > 0).clicked() {
                    move_up = true;
                }
                if triangle_button(
                    ui,
                    TriangleDirection::Down,
                    idx + 1 < selection_len,
                )
                .clicked()
                {
                    move_down = true;
                }
            });
        });

        self.selection[idx] = value;
        if move_up {
            self.selection.swap(idx, idx - 1);
        }
        if move_down {
            self.selection.swap(idx, idx + 1);
        }
    }

    fn render(&mut self, ui: &mut egui::Ui) {
        ui.heading(self.title);
        if self.selection.is_empty() {
            ui.label("No lifts available for this column.");
            return;
        }
        for idx in 0..self.selection.len() {
            self.selection_row(ui, idx);
        }
    }
}

#[derive(Copy, Clone)]
enum TriangleDirection {
    Up,
    Down,
}

fn triangle_button(ui: &mut egui::Ui, direction: TriangleDirection, enabled: bool) -> Response {
    let size = Vec2::splat(20.0);
    let sense = if enabled {
        egui::Sense::click()
    } else {
        egui::Sense::hover()
    };
    let (rect, response) = ui.allocate_exact_size(size, sense);
    let response = if enabled {
        response.on_hover_cursor(CursorIcon::PointingHand)
    } else {
        response
    };

    if ui.is_rect_visible(rect) {
        let visuals = if enabled {
            ui.style().interact(&response)
        } else {
            &ui.style().visuals.widgets.inactive
        };

        let rounding = visuals.rounding;
        let painter = ui.painter();
        painter.rect(rect, rounding, visuals.bg_fill, visuals.bg_stroke);

        let stroke = Stroke::new(2.0, visuals.fg_stroke.color);
        let inset_x = rect.width() * 0.3;
        let inset_y = rect.height() * 0.3;
        let points = match direction {
            TriangleDirection::Up => [
                egui::pos2(rect.center().x, rect.top() + inset_y),
                egui::pos2(rect.left() + inset_x, rect.bottom() - inset_y),
                egui::pos2(rect.right() - inset_x, rect.bottom() - inset_y),
            ],
            TriangleDirection::Down => [
                egui::pos2(rect.left() + inset_x, rect.top() + inset_y),
                egui::pos2(rect.right() - inset_x, rect.top() + inset_y),
                egui::pos2(rect.center().x, rect.bottom() - inset_y),
            ],
        };
        painter.add(Shape::convex_polygon(points.to_vec(), visuals.fg_stroke.color, stroke));
    }

    response
}

struct MaxEffortEditorApp {
    lower_squat: Column,
    lower_deadlift: Column,
    upper_bench: Column,
    upper_ohp: Column,
    sender: Option<Sender<MaxEffortSelections>>,
}

impl MaxEffortEditorApp {
    fn new(
        lower_squat: Column,
        lower_deadlift: Column,
        upper_bench: Column,
        upper_ohp: Column,
        sender: Sender<MaxEffortSelections>,
    ) -> Self {
        Self {
            lower_squat,
            lower_deadlift,
            upper_bench,
            upper_ohp,
            sender: Some(sender),
        }
    }

    fn render_section(ui: &mut egui::Ui, title: &str, first: &mut Column, second: &mut Column) {
        ui.heading(title);
        ui.columns(2, |columns| {
            columns[0].vertical(|ui| first.render(ui));
            columns[1].vertical(|ui| second.render(ui));
        });
    }
}

impl App for MaxEffortEditorApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut Frame) {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.heading("Customize Max Effort Rotation");
            ui.label(
                "Adjust the lift used for each week's max effort day. Each column represents the lifts that rotate every other week—use the triangle buttons to reorder within a column.",
            );
            ui.add_space(12.0);

            Self::render_section(
                ui,
                "Lower Body Max Effort",
                &mut self.lower_squat,
                &mut self.lower_deadlift,
            );
            ui.add_space(16.0);
            Self::render_section(
                ui,
                "Upper Body Max Effort",
                &mut self.upper_bench,
                &mut self.upper_ohp,
            );

            ui.add_space(20.0);
            ui.separator();
            ui.add_space(8.0);
            ui.horizontal(|ui| {
                if ui.button("Save & Generate").clicked() {
                    if let Some(sender) = self.sender.take() {
                        let _ = sender.send(MaxEffortSelections {
                            lower_squat: self.lower_squat.selection.clone(),
                            lower_deadlift: self.lower_deadlift.selection.clone(),
                            upper_bench: self.upper_bench.selection.clone(),
                            upper_ohp: self.upper_ohp.selection.clone(),
                        });
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
