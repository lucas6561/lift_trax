use std::{
    env,
    sync::mpsc::{self, Sender, TryRecvError},
};

use eframe::{
    App, Frame, NativeOptions,
    egui::{self, CursorIcon, Response, Shape, Stroke, Vec2},
};

use crate::{database::DbResult, models::Lift};

pub struct MaxEffortPlan {
    pub lower: Vec<Lift>,
    pub upper: Vec<Lift>,
    pub lower_deload: Vec<DeloadLowerLifts>,
    pub upper_deload: Vec<DeloadUpperLifts>,
}

#[derive(Clone)]
pub struct DeloadLowerLifts {
    pub squat: Lift,
    pub deadlift: Lift,
}

#[derive(Clone)]
pub struct DeloadUpperLifts {
    pub bench: Lift,
    pub overhead: Lift,
}

pub fn edit_max_effort_plan(
    lower_squat_options: Vec<Lift>,
    lower_deadlift_options: Vec<Lift>,
    upper_bench_options: Vec<Lift>,
    upper_ohp_options: Vec<Lift>,
    default_lower: Vec<Lift>,
    default_upper: Vec<Lift>,
) -> DbResult<MaxEffortPlan> {
    if lower_squat_options.is_empty()
        || lower_deadlift_options.is_empty()
        || upper_bench_options.is_empty()
        || upper_ohp_options.is_empty()
    {
        return Ok(plan_from_defaults(default_lower, default_upper));
    }

    if default_lower.is_empty() || default_upper.is_empty() {
        return Ok(plan_from_defaults(default_lower, default_upper));
    }

    if cfg!(test) {
        return Ok(plan_from_defaults(default_lower, default_upper));
    }

    if env::var("CARGO_TEST").is_ok() || env::var("LIFT_TRAX_HEADLESS").is_ok() {
        return Ok(plan_from_defaults(default_lower, default_upper));
    }

    let weeks = default_lower.len();

    let lower_squat_defaults = split_defaults_by_parity(&default_lower, 0);
    let lower_deadlift_defaults = split_defaults_by_parity(&default_lower, 1);
    let upper_bench_defaults = split_defaults_by_parity(&default_upper, 0);
    let upper_ohp_defaults = split_defaults_by_parity(&default_upper, 1);

    let lower_squat_indices = indices_for_defaults(&lower_squat_options, &lower_squat_defaults);
    let lower_deadlift_indices =
        indices_for_defaults(&lower_deadlift_options, &lower_deadlift_defaults);
    let upper_bench_indices = indices_for_defaults(&upper_bench_options, &upper_bench_defaults);
    let upper_ohp_indices = indices_for_defaults(&upper_ohp_options, &upper_ohp_defaults);

    let lower_deload_pairs = derive_lower_deload_from_plan(&default_lower);
    let upper_deload_pairs = derive_upper_deload_from_plan(&default_upper);

    let lower_deload_defaults = deload_lower_indices_from_pairs(
        &lower_deload_pairs,
        &lower_squat_options,
        &lower_deadlift_options,
    );
    let upper_deload_defaults = deload_upper_indices_from_pairs(
        &upper_deload_pairs,
        &upper_bench_options,
        &upper_ohp_options,
    );
    let deload_weeks = deload_week_numbers(weeks);

    let (tx, rx) = mpsc::channel();

    let app_lower_squat_options = lower_squat_options.clone();
    let app_lower_deadlift_options = lower_deadlift_options.clone();
    let app_upper_bench_options = upper_bench_options.clone();
    let app_upper_ohp_options = upper_ohp_options.clone();
    let app_lower_squat_indices = lower_squat_indices.clone();
    let app_lower_deadlift_indices = lower_deadlift_indices.clone();
    let app_upper_bench_indices = upper_bench_indices.clone();
    let app_upper_ohp_indices = upper_ohp_indices.clone();
    let app_lower_deload_defaults = lower_deload_defaults.clone();
    let app_upper_deload_defaults = upper_deload_defaults.clone();
    let app_deload_weeks = deload_weeks.clone();
    let sender = tx.clone();

    if let Err(err) = eframe::run_native(
        "Max Effort Planner",
        NativeOptions::default(),
        Box::new(move |_cc| {
            Box::new(MaxEffortEditorApp::new(
                Column::new(
                    "Squat Weeks",
                    "lower_squat",
                    column_week_numbers(weeks, 0),
                    app_lower_squat_options.clone(),
                    app_lower_squat_indices,
                ),
                Column::new(
                    "Deadlift Weeks",
                    "lower_deadlift",
                    column_week_numbers(weeks, 1),
                    app_lower_deadlift_options.clone(),
                    app_lower_deadlift_indices,
                ),
                Column::new(
                    "Bench Press Weeks",
                    "upper_bench",
                    column_week_numbers(weeks, 0),
                    app_upper_bench_options.clone(),
                    app_upper_bench_indices,
                ),
                Column::new(
                    "Overhead Press Weeks",
                    "upper_ohp",
                    column_week_numbers(weeks, 1),
                    app_upper_ohp_options.clone(),
                    app_upper_ohp_indices,
                ),
                DeloadColumn::new(
                    "Lower Deload Weeks",
                    "lower_deload",
                    app_deload_weeks.clone(),
                    "Squat",
                    app_lower_squat_options.clone(),
                    "Deadlift",
                    app_lower_deadlift_options.clone(),
                    app_lower_deload_defaults,
                ),
                DeloadColumn::new(
                    "Upper Deload Weeks",
                    "upper_deload",
                    app_deload_weeks,
                    "Bench Press",
                    app_upper_bench_options.clone(),
                    "Overhead Press",
                    app_upper_ohp_options.clone(),
                    app_upper_deload_defaults,
                ),
                sender.clone(),
            ))
        }),
    ) {
        eprintln!("Failed to launch max effort editor: {err}");
        return Ok(plan_from_defaults(default_lower, default_upper));
    }

    let selections = match rx.try_recv() {
        Ok(indices) => indices,
        Err(TryRecvError::Empty) | Err(TryRecvError::Disconnected) => MaxEffortSelections {
            lower_squat: lower_squat_indices,
            lower_deadlift: lower_deadlift_indices,
            upper_bench: upper_bench_indices,
            upper_ohp: upper_ohp_indices,
            lower_deload: lower_deload_defaults,
            upper_deload: upper_deload_defaults,
        },
    };

    let lower_plan = build_plan(
        &default_lower,
        &selections.lower_squat,
        &selections.lower_deadlift,
        &lower_squat_options,
        &lower_deadlift_options,
    );
    let upper_plan = build_plan(
        &default_upper,
        &selections.upper_bench,
        &selections.upper_ohp,
        &upper_bench_options,
        &upper_ohp_options,
    );

    let lower_deload = build_deload_lower_plan(
        &selections.lower_deload,
        &lower_squat_options,
        &lower_deadlift_options,
    );
    let upper_deload = build_deload_upper_plan(
        &selections.upper_deload,
        &upper_bench_options,
        &upper_ohp_options,
    );

    Ok(MaxEffortPlan {
        lower: lower_plan,
        upper: upper_plan,
        lower_deload,
        upper_deload,
    })
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
        .filter_map(|(idx, lift)| {
            let is_matching_parity = idx % 2 == parity;
            let is_deload_week = (idx + 1) % 7 == 0;
            if is_matching_parity && !is_deload_week {
                Some(lift.clone())
            } else {
                None
            }
        })
        .collect()
}

fn column_week_numbers(total_weeks: usize, parity: usize) -> Vec<usize> {
    (0..total_weeks)
        .filter(|week| week % 2 == parity && (week + 1) % 7 != 0)
        .map(|week| week + 1)
        .collect()
}

fn build_plan(
    defaults: &[Lift],
    primary_selection: &[usize],
    secondary_selection: &[usize],
    primary_options: &[Lift],
    secondary_options: &[Lift],
) -> Vec<Lift> {
    let weeks = defaults.len();
    let mut plan = Vec::with_capacity(weeks);
    let mut primary_idx = 0usize;
    let mut secondary_idx = 0usize;

    for (week, default) in defaults.iter().enumerate() {
        if (week + 1) % 7 == 0 {
            plan.push(default.clone());
            continue;
        }

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

fn deload_week_numbers(weeks: usize) -> Vec<usize> {
    (1..=weeks).filter(|week| week % 7 == 0).collect::<Vec<_>>()
}

fn deload_lower_indices_from_pairs(
    pairs: &[DeloadLowerLifts],
    squat_options: &[Lift],
    deadlift_options: &[Lift],
) -> Vec<DeloadSelectionIndices> {
    pairs
        .iter()
        .map(|pair| DeloadSelectionIndices {
            primary: squat_options
                .iter()
                .position(|opt| opt.name == pair.squat.name)
                .unwrap_or(0),
            secondary: deadlift_options
                .iter()
                .position(|opt| opt.name == pair.deadlift.name)
                .unwrap_or(0),
        })
        .collect()
}

fn deload_upper_indices_from_pairs(
    pairs: &[DeloadUpperLifts],
    bench_options: &[Lift],
    ohp_options: &[Lift],
) -> Vec<DeloadSelectionIndices> {
    pairs
        .iter()
        .map(|pair| DeloadSelectionIndices {
            primary: bench_options
                .iter()
                .position(|opt| opt.name == pair.bench.name)
                .unwrap_or(0),
            secondary: ohp_options
                .iter()
                .position(|opt| opt.name == pair.overhead.name)
                .unwrap_or(0),
        })
        .collect()
}

fn build_deload_lower_plan(
    selections: &[DeloadSelectionIndices],
    squat_options: &[Lift],
    deadlift_options: &[Lift],
) -> Vec<DeloadLowerLifts> {
    selections
        .iter()
        .map(|selection| DeloadLowerLifts {
            squat: squat_options
                .get(selection.primary)
                .or_else(|| squat_options.first())
                .expect("squat options were validated to be non-empty")
                .clone(),
            deadlift: deadlift_options
                .get(selection.secondary)
                .or_else(|| deadlift_options.first())
                .expect("deadlift options were validated to be non-empty")
                .clone(),
        })
        .collect()
}

fn build_deload_upper_plan(
    selections: &[DeloadSelectionIndices],
    bench_options: &[Lift],
    ohp_options: &[Lift],
) -> Vec<DeloadUpperLifts> {
    selections
        .iter()
        .map(|selection| DeloadUpperLifts {
            bench: bench_options
                .get(selection.primary)
                .or_else(|| bench_options.first())
                .expect("bench options were validated to be non-empty")
                .clone(),
            overhead: ohp_options
                .get(selection.secondary)
                .or_else(|| ohp_options.first())
                .expect("overhead options were validated to be non-empty")
                .clone(),
        })
        .collect()
}

fn plan_from_defaults(default_lower: Vec<Lift>, default_upper: Vec<Lift>) -> MaxEffortPlan {
    let lower_deload = derive_lower_deload_from_plan(&default_lower);
    let upper_deload = derive_upper_deload_from_plan(&default_upper);
    MaxEffortPlan {
        lower: default_lower,
        upper: default_upper,
        lower_deload,
        upper_deload,
    }
}

fn derive_lower_deload_from_plan(plan: &[Lift]) -> Vec<DeloadLowerLifts> {
    if plan.len() < 7 {
        return Vec::new();
    }

    let mut deload = Vec::new();
    let deload_weeks = plan.len() / 7;
    for deload_idx in 0..deload_weeks {
        let week_number = (deload_idx + 1) * 7 - 1;
        if week_number == 0 {
            continue;
        }
        let current = plan[week_number].clone();
        let prior = plan[week_number - 1].clone();
        if week_number % 2 == 0 {
            deload.push(DeloadLowerLifts {
                squat: current,
                deadlift: prior,
            });
        } else {
            deload.push(DeloadLowerLifts {
                squat: prior,
                deadlift: current,
            });
        }
    }
    deload
}

fn derive_upper_deload_from_plan(plan: &[Lift]) -> Vec<DeloadUpperLifts> {
    if plan.len() < 7 {
        return Vec::new();
    }

    let mut deload = Vec::new();
    let deload_weeks = plan.len() / 7;
    for deload_idx in 0..deload_weeks {
        let week_number = (deload_idx + 1) * 7 - 1;
        if week_number == 0 {
            continue;
        }
        let current = plan[week_number].clone();
        let prior = plan[week_number - 1].clone();
        if week_number % 2 == 0 {
            deload.push(DeloadUpperLifts {
                bench: current,
                overhead: prior,
            });
        } else {
            deload.push(DeloadUpperLifts {
                bench: prior,
                overhead: current,
            });
        }
    }
    deload
}

struct MaxEffortSelections {
    lower_squat: Vec<usize>,
    lower_deadlift: Vec<usize>,
    upper_bench: Vec<usize>,
    upper_ohp: Vec<usize>,
    lower_deload: Vec<DeloadSelectionIndices>,
    upper_deload: Vec<DeloadSelectionIndices>,
}

struct Column {
    title: &'static str,
    id_prefix: &'static str,
    week_numbers: Vec<usize>,
    options: Vec<Lift>,
    selection: Vec<usize>,
}

impl Column {
    fn new(
        title: &'static str,
        id_prefix: &'static str,
        week_numbers: Vec<usize>,
        options: Vec<Lift>,
        selection: Vec<usize>,
    ) -> Self {
        Self {
            title,
            id_prefix,
            week_numbers,
            options,
            selection,
        }
    }

    fn week_number(&self, idx: usize) -> usize {
        self.week_numbers
            .get(idx)
            .copied()
            .unwrap_or(self.week_numbers.last().copied().unwrap_or(1))
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
                if triangle_button(ui, TriangleDirection::Down, idx + 1 < selection_len).clicked() {
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
        let rows = self.selection.len().min(self.week_numbers.len());
        for idx in 0..rows {
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
        painter.add(Shape::convex_polygon(
            points.to_vec(),
            visuals.fg_stroke.color,
            stroke,
        ));
    }

    response
}

#[derive(Clone)]
struct DeloadSelectionIndices {
    primary: usize,
    secondary: usize,
}

struct DeloadColumn {
    title: &'static str,
    id_prefix: &'static str,
    week_numbers: Vec<usize>,
    primary_label: &'static str,
    primary_options: Vec<Lift>,
    secondary_label: &'static str,
    secondary_options: Vec<Lift>,
    selection: Vec<DeloadSelectionIndices>,
}

impl DeloadColumn {
    fn new(
        title: &'static str,
        id_prefix: &'static str,
        week_numbers: Vec<usize>,
        primary_label: &'static str,
        primary_options: Vec<Lift>,
        secondary_label: &'static str,
        secondary_options: Vec<Lift>,
        selection: Vec<DeloadSelectionIndices>,
    ) -> Option<Self> {
        if week_numbers.is_empty() {
            return None;
        }
        Some(Self {
            title,
            id_prefix,
            week_numbers,
            primary_label,
            primary_options,
            secondary_label,
            secondary_options,
            selection,
        })
    }

    fn render_row(&mut self, ui: &mut egui::Ui, idx: usize) {
        let week = self.week_numbers[idx];
        let mut primary = self.selection[idx].primary;
        let mut secondary = self.selection[idx].secondary;

        ui.push_id((self.id_prefix, idx), |ui| {
            ui.group(|ui| {
                ui.vertical(|ui| {
                    ui.label(format!("Deload Week {week}"));
                    ui.horizontal(|ui| {
                        ui.label(self.primary_label);
                        let current = self
                            .primary_options
                            .get(primary)
                            .map(|lift| lift.name.clone())
                            .unwrap_or_else(|| "Unknown".to_string());
                        egui::ComboBox::from_id_source("primary_combo")
                            .selected_text(current)
                            .show_ui(ui, |combo_ui| {
                                for (opt_idx, lift) in self.primary_options.iter().enumerate() {
                                    combo_ui.selectable_value(
                                        &mut primary,
                                        opt_idx,
                                        lift.name.clone(),
                                    );
                                }
                            });
                    });
                    ui.horizontal(|ui| {
                        ui.label(self.secondary_label);
                        let current = self
                            .secondary_options
                            .get(secondary)
                            .map(|lift| lift.name.clone())
                            .unwrap_or_else(|| "Unknown".to_string());
                        egui::ComboBox::from_id_source("secondary_combo")
                            .selected_text(current)
                            .show_ui(ui, |combo_ui| {
                                for (opt_idx, lift) in self.secondary_options.iter().enumerate() {
                                    combo_ui.selectable_value(
                                        &mut secondary,
                                        opt_idx,
                                        lift.name.clone(),
                                    );
                                }
                            });
                    });
                });
            });
        });

        self.selection[idx].primary = primary;
        self.selection[idx].secondary = secondary;
    }

    fn render(&mut self, ui: &mut egui::Ui) {
        if self.selection.is_empty() {
            return;
        }
        ui.heading(self.title);
        let rows = self.selection.len().min(self.week_numbers.len());
        for idx in 0..rows {
            self.render_row(ui, idx);
            ui.add_space(8.0);
        }
    }

    fn selections(&self) -> Vec<DeloadSelectionIndices> {
        self.selection.clone()
    }
}

struct MaxEffortEditorApp {
    lower_squat: Column,
    lower_deadlift: Column,
    upper_bench: Column,
    upper_ohp: Column,
    lower_deload: Option<DeloadColumn>,
    upper_deload: Option<DeloadColumn>,
    sender: Option<Sender<MaxEffortSelections>>,
}

impl MaxEffortEditorApp {
    fn new(
        lower_squat: Column,
        lower_deadlift: Column,
        upper_bench: Column,
        upper_ohp: Column,
        lower_deload: Option<DeloadColumn>,
        upper_deload: Option<DeloadColumn>,
        sender: Sender<MaxEffortSelections>,
    ) -> Self {
        Self {
            lower_squat,
            lower_deadlift,
            upper_bench,
            upper_ohp,
            lower_deload,
            upper_deload,
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

    fn render_deload_section(
        ui: &mut egui::Ui,
        lower: Option<&mut DeloadColumn>,
        upper: Option<&mut DeloadColumn>,
    ) {
        if lower.is_none() && upper.is_none() {
            return;
        }

        ui.heading("Deload Week Max Effort");
        ui.columns(2, |columns| {
            if let Some(lower_col) = lower {
                columns[0].vertical(|ui| lower_col.render(ui));
            }
            if let Some(upper_col) = upper {
                columns[1].vertical(|ui| upper_col.render(ui));
            }
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

            ui.add_space(16.0);
            Self::render_deload_section(ui, self.lower_deload.as_mut(), self.upper_deload.as_mut());

            ui.add_space(20.0);
            ui.separator();
            ui.add_space(8.0);
            ui.horizontal(|ui| {
                if ui.button("Save & Generate").clicked() {
                    if let Some(sender) = self.sender.take() {
                        let lower_deload = self
                            .lower_deload
                            .as_ref()
                            .map(|c| c.selections())
                            .unwrap_or_default();
                        let upper_deload = self
                            .upper_deload
                            .as_ref()
                            .map(|c| c.selections())
                            .unwrap_or_default();
                        let _ = sender.send(MaxEffortSelections {
                            lower_squat: self.lower_squat.selection.clone(),
                            lower_deadlift: self.lower_deadlift.selection.clone(),
                            upper_bench: self.upper_bench.selection.clone(),
                            upper_ohp: self.upper_ohp.selection.clone(),
                            lower_deload,
                            upper_deload,
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
