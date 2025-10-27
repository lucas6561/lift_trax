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
    pub lower_reload: Vec<ReloadLowerLifts>,
    pub upper_reload: Vec<ReloadUpperLifts>,
}

#[derive(Clone)]
pub struct ReloadLowerLifts {
    pub squat: Lift,
    pub deadlift: Lift,
}

#[derive(Clone)]
pub struct ReloadUpperLifts {
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

    let lower_reload_defaults =
        reload_indices_for_defaults(weeks, &lower_squat_indices, &lower_deadlift_indices);
    let upper_reload_defaults =
        reload_indices_for_defaults(weeks, &upper_bench_indices, &upper_ohp_indices);
    let reload_weeks = reload_week_numbers(weeks);

    let (tx, rx) = mpsc::channel();

    let app_lower_squat_options = lower_squat_options.clone();
    let app_lower_deadlift_options = lower_deadlift_options.clone();
    let app_upper_bench_options = upper_bench_options.clone();
    let app_upper_ohp_options = upper_ohp_options.clone();
    let app_lower_squat_indices = lower_squat_indices.clone();
    let app_lower_deadlift_indices = lower_deadlift_indices.clone();
    let app_upper_bench_indices = upper_bench_indices.clone();
    let app_upper_ohp_indices = upper_ohp_indices.clone();
    let app_lower_reload_defaults = lower_reload_defaults.clone();
    let app_upper_reload_defaults = upper_reload_defaults.clone();
    let app_reload_weeks = reload_weeks.clone();
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
                    app_lower_squat_options.clone(),
                    app_lower_squat_indices,
                ),
                Column::new(
                    "Deadlift Weeks",
                    "lower_deadlift",
                    1,
                    app_lower_deadlift_options.clone(),
                    app_lower_deadlift_indices,
                ),
                Column::new(
                    "Bench Press Weeks",
                    "upper_bench",
                    0,
                    app_upper_bench_options.clone(),
                    app_upper_bench_indices,
                ),
                Column::new(
                    "Overhead Press Weeks",
                    "upper_ohp",
                    1,
                    app_upper_ohp_options.clone(),
                    app_upper_ohp_indices,
                ),
                ReloadColumn::new(
                    "Lower Reload Weeks",
                    "lower_reload",
                    app_reload_weeks.clone(),
                    "Squat",
                    app_lower_squat_options.clone(),
                    "Deadlift",
                    app_lower_deadlift_options.clone(),
                    app_lower_reload_defaults,
                ),
                ReloadColumn::new(
                    "Upper Reload Weeks",
                    "upper_reload",
                    app_reload_weeks,
                    "Bench Press",
                    app_upper_bench_options.clone(),
                    "Overhead Press",
                    app_upper_ohp_options.clone(),
                    app_upper_reload_defaults,
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
            lower_reload: lower_reload_defaults,
            upper_reload: upper_reload_defaults,
        },
    };

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

    let lower_reload = build_reload_lower_plan(
        &selections.lower_reload,
        &lower_squat_options,
        &lower_deadlift_options,
    );
    let upper_reload = build_reload_upper_plan(
        &selections.upper_reload,
        &upper_bench_options,
        &upper_ohp_options,
    );

    Ok(MaxEffortPlan {
        lower: lower_plan,
        upper: upper_plan,
        lower_reload,
        upper_reload,
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

fn reload_week_numbers(weeks: usize) -> Vec<usize> {
    (1..=weeks).filter(|week| week % 7 == 0).collect::<Vec<_>>()
}

fn reload_indices_for_defaults(
    weeks: usize,
    primary: &[usize],
    secondary: &[usize],
) -> Vec<ReloadSelectionIndices> {
    let mut selections = Vec::new();
    if weeks < 7 {
        return selections;
    }

    let reload_weeks = weeks / 7;
    for reload_idx in 0..reload_weeks {
        let week_number = (reload_idx + 1) * 7 - 1; // zero-based index
        let (primary_week, secondary_week) = if week_number % 2 == 0 {
            (week_number / 2, (week_number - 1) / 2)
        } else {
            ((week_number - 1) / 2, week_number / 2)
        };

        let primary_idx = primary
            .get(primary_week)
            .copied()
            .or_else(|| primary.first().copied())
            .unwrap_or(0);
        let secondary_idx = secondary
            .get(secondary_week)
            .copied()
            .or_else(|| secondary.first().copied())
            .unwrap_or(0);

        selections.push(ReloadSelectionIndices {
            primary: primary_idx,
            secondary: secondary_idx,
        });
    }

    selections
}

fn build_reload_lower_plan(
    selections: &[ReloadSelectionIndices],
    squat_options: &[Lift],
    deadlift_options: &[Lift],
) -> Vec<ReloadLowerLifts> {
    selections
        .iter()
        .map(|selection| ReloadLowerLifts {
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

fn build_reload_upper_plan(
    selections: &[ReloadSelectionIndices],
    bench_options: &[Lift],
    ohp_options: &[Lift],
) -> Vec<ReloadUpperLifts> {
    selections
        .iter()
        .map(|selection| ReloadUpperLifts {
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
    let lower_reload = derive_lower_reload_from_plan(&default_lower);
    let upper_reload = derive_upper_reload_from_plan(&default_upper);
    MaxEffortPlan {
        lower: default_lower,
        upper: default_upper,
        lower_reload,
        upper_reload,
    }
}

fn derive_lower_reload_from_plan(plan: &[Lift]) -> Vec<ReloadLowerLifts> {
    if plan.len() < 7 {
        return Vec::new();
    }

    let mut reload = Vec::new();
    let reload_weeks = plan.len() / 7;
    for reload_idx in 0..reload_weeks {
        let week_number = (reload_idx + 1) * 7 - 1;
        if week_number == 0 {
            continue;
        }
        let current = plan[week_number].clone();
        let prior = plan[week_number - 1].clone();
        if week_number % 2 == 0 {
            reload.push(ReloadLowerLifts {
                squat: current,
                deadlift: prior,
            });
        } else {
            reload.push(ReloadLowerLifts {
                squat: prior,
                deadlift: current,
            });
        }
    }
    reload
}

fn derive_upper_reload_from_plan(plan: &[Lift]) -> Vec<ReloadUpperLifts> {
    if plan.len() < 7 {
        return Vec::new();
    }

    let mut reload = Vec::new();
    let reload_weeks = plan.len() / 7;
    for reload_idx in 0..reload_weeks {
        let week_number = (reload_idx + 1) * 7 - 1;
        if week_number == 0 {
            continue;
        }
        let current = plan[week_number].clone();
        let prior = plan[week_number - 1].clone();
        if week_number % 2 == 0 {
            reload.push(ReloadUpperLifts {
                bench: current,
                overhead: prior,
            });
        } else {
            reload.push(ReloadUpperLifts {
                bench: prior,
                overhead: current,
            });
        }
    }
    reload
}

struct MaxEffortSelections {
    lower_squat: Vec<usize>,
    lower_deadlift: Vec<usize>,
    upper_bench: Vec<usize>,
    upper_ohp: Vec<usize>,
    lower_reload: Vec<ReloadSelectionIndices>,
    upper_reload: Vec<ReloadSelectionIndices>,
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
        painter.add(Shape::convex_polygon(
            points.to_vec(),
            visuals.fg_stroke.color,
            stroke,
        ));
    }

    response
}

#[derive(Clone)]
struct ReloadSelectionIndices {
    primary: usize,
    secondary: usize,
}

struct ReloadColumn {
    title: &'static str,
    id_prefix: &'static str,
    week_numbers: Vec<usize>,
    primary_label: &'static str,
    primary_options: Vec<Lift>,
    secondary_label: &'static str,
    secondary_options: Vec<Lift>,
    selection: Vec<ReloadSelectionIndices>,
}

impl ReloadColumn {
    fn new(
        title: &'static str,
        id_prefix: &'static str,
        week_numbers: Vec<usize>,
        primary_label: &'static str,
        primary_options: Vec<Lift>,
        secondary_label: &'static str,
        secondary_options: Vec<Lift>,
        selection: Vec<ReloadSelectionIndices>,
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
                    ui.label(format!("Reload Week {week}"));
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
        for idx in 0..self.selection.len() {
            self.render_row(ui, idx);
            ui.add_space(8.0);
        }
    }

    fn selections(&self) -> Vec<ReloadSelectionIndices> {
        self.selection.clone()
    }
}

struct MaxEffortEditorApp {
    lower_squat: Column,
    lower_deadlift: Column,
    upper_bench: Column,
    upper_ohp: Column,
    lower_reload: Option<ReloadColumn>,
    upper_reload: Option<ReloadColumn>,
    sender: Option<Sender<MaxEffortSelections>>,
}

impl MaxEffortEditorApp {
    fn new(
        lower_squat: Column,
        lower_deadlift: Column,
        upper_bench: Column,
        upper_ohp: Column,
        lower_reload: Option<ReloadColumn>,
        upper_reload: Option<ReloadColumn>,
        sender: Sender<MaxEffortSelections>,
    ) -> Self {
        Self {
            lower_squat,
            lower_deadlift,
            upper_bench,
            upper_ohp,
            lower_reload,
            upper_reload,
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

    fn render_reload_section(
        ui: &mut egui::Ui,
        lower: Option<&mut ReloadColumn>,
        upper: Option<&mut ReloadColumn>,
    ) {
        if lower.is_none() && upper.is_none() {
            return;
        }

        ui.heading("Reload Week Max Effort");
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
            Self::render_reload_section(ui, self.lower_reload.as_mut(), self.upper_reload.as_mut());

            ui.add_space(20.0);
            ui.separator();
            ui.add_space(8.0);
            ui.horizontal(|ui| {
                if ui.button("Save & Generate").clicked() {
                    if let Some(sender) = self.sender.take() {
                        let lower_reload = self
                            .lower_reload
                            .as_ref()
                            .map(|c| c.selections())
                            .unwrap_or_default();
                        let upper_reload = self
                            .upper_reload
                            .as_ref()
                            .map(|c| c.selections())
                            .unwrap_or_default();
                        let _ = sender.send(MaxEffortSelections {
                            lower_squat: self.lower_squat.selection.clone(),
                            lower_deadlift: self.lower_deadlift.selection.clone(),
                            upper_bench: self.upper_bench.selection.clone(),
                            upper_ohp: self.upper_ohp.selection.clone(),
                            lower_reload,
                            upper_reload,
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
