use std::{
    env,
    sync::mpsc::{self, Sender, TryRecvError},
};

use eframe::{
    App, Frame, NativeOptions,
    egui::{self, ComboBox},
};

use crate::{database::DbResult, models::Lift};

#[derive(Clone)]
pub(crate) struct DynamicLiftChoices {
    pub(crate) squat: Lift,
    pub(crate) deadlift: Lift,
    pub(crate) bench: Lift,
    pub(crate) overhead: Lift,
}

pub(crate) fn edit_dynamic_lifts(
    squat_options: Vec<Lift>,
    deadlift_options: Vec<Lift>,
    bench_options: Vec<Lift>,
    overhead_options: Vec<Lift>,
    defaults: DynamicLiftChoices,
) -> DbResult<DynamicLiftChoices> {
    if squat_options.is_empty()
        || deadlift_options.is_empty()
        || bench_options.is_empty()
        || overhead_options.is_empty()
    {
        return Ok(defaults);
    }

    if cfg!(test) {
        return Ok(defaults);
    }

    if env::var("CARGO_TEST").is_ok() || env::var("LIFT_TRAX_HEADLESS").is_ok() {
        return Ok(defaults);
    }

    let squat_idx = index_for_default(&squat_options, &defaults.squat);
    let deadlift_idx = index_for_default(&deadlift_options, &defaults.deadlift);
    let bench_idx = index_for_default(&bench_options, &defaults.bench);
    let overhead_idx = index_for_default(&overhead_options, &defaults.overhead);

    let (tx, rx) = mpsc::channel();

    let app = DynamicLiftSelectorApp::new(
        Selector::new("Squat", squat_options.clone(), squat_idx),
        Selector::new("Deadlift", deadlift_options.clone(), deadlift_idx),
        Selector::new("Bench Press", bench_options.clone(), bench_idx),
        Selector::new("Overhead Press", overhead_options.clone(), overhead_idx),
        tx.clone(),
    );

    if let Err(err) = eframe::run_native(
        "Dynamic Effort Lifts",
        NativeOptions::default(),
        Box::new(|_cc| Box::new(app)),
    ) {
        eprintln!("Failed to launch dynamic lift selector: {err}");
        return Ok(defaults);
    }

    let selection = match rx.try_recv() {
        Ok(selection) => selection,
        Err(TryRecvError::Empty) | Err(TryRecvError::Disconnected) => DynamicLiftIndices {
            squat: squat_idx,
            deadlift: deadlift_idx,
            bench: bench_idx,
            overhead: overhead_idx,
        },
    };

    Ok(DynamicLiftChoices {
        squat: pick(&squat_options, selection.squat),
        deadlift: pick(&deadlift_options, selection.deadlift),
        bench: pick(&bench_options, selection.bench),
        overhead: pick(&overhead_options, selection.overhead),
    })
}

fn index_for_default(options: &[Lift], default: &Lift) -> usize {
    options
        .iter()
        .position(|lift| lift.name == default.name)
        .unwrap_or(0)
}

fn pick(options: &[Lift], index: usize) -> Lift {
    options
        .get(index)
        .or_else(|| options.first())
        .expect("options must be non-empty")
        .clone()
}

struct Selector {
    title: &'static str,
    options: Vec<Lift>,
    selected: usize,
}

impl Selector {
    fn new(title: &'static str, options: Vec<Lift>, selected: usize) -> Self {
        Self {
            title,
            options,
            selected,
        }
    }
}

struct DynamicLiftIndices {
    squat: usize,
    deadlift: usize,
    bench: usize,
    overhead: usize,
}

struct DynamicLiftSelectorApp {
    squat: Selector,
    deadlift: Selector,
    bench: Selector,
    overhead: Selector,
    sender: Option<Sender<DynamicLiftIndices>>,
}

impl DynamicLiftSelectorApp {
    fn new(
        squat: Selector,
        deadlift: Selector,
        bench: Selector,
        overhead: Selector,
        sender: Sender<DynamicLiftIndices>,
    ) -> Self {
        Self {
            squat,
            deadlift,
            bench,
            overhead,
            sender: Some(sender),
        }
    }
}

impl App for DynamicLiftSelectorApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut Frame) {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.heading("Customize Dynamic Effort Lifts");
            ui.label("Pick the variation you'll use for each dynamic effort day in this block.");
            ui.add_space(12.0);

            let render_selector = |ui: &mut egui::Ui, selector: &mut Selector| {
                ui.push_id(selector.title, |ui| {
                    ui.horizontal(|ui| {
                        ui.label(selector.title);
                        let current = selector
                            .options
                            .get(selector.selected)
                            .map(|lift| lift.name.clone())
                            .unwrap_or_else(|| "Unknown".to_string());

                        ComboBox::from_id_source("combo")
                            .selected_text(current)
                            .show_ui(ui, |combo_ui| {
                                for (idx, lift) in selector.options.iter().enumerate() {
                                    combo_ui.selectable_value(
                                        &mut selector.selected,
                                        idx,
                                        lift.name.clone(),
                                    );
                                }
                            });
                    });
                });
            };

            render_selector(ui, &mut self.squat);
            render_selector(ui, &mut self.deadlift);
            render_selector(ui, &mut self.bench);
            render_selector(ui, &mut self.overhead);

            ui.add_space(16.0);
            ui.separator();
            ui.add_space(8.0);
            ui.horizontal(|ui| {
                if ui.button("Save & Generate").clicked() {
                    if let Some(sender) = self.sender.take() {
                        let _ = sender.send(DynamicLiftIndices {
                            squat: self.squat.selected,
                            deadlift: self.deadlift.selected,
                            bench: self.bench.selected,
                            overhead: self.overhead.selected,
                        });
                    }
                    ui.ctx().send_viewport_cmd(egui::ViewportCommand::Close);
                }
                if ui.button("Use Defaults").clicked() {
                    ui.ctx().send_viewport_cmd(egui::ViewportCommand::Close);
                }
            });
            ui.add_space(4.0);
            ui.label("Close the window at any time to keep the defaults.");
        });
    }
}
