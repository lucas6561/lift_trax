use chrono::{NaiveDate, Utc};
use eframe::{Frame, NativeOptions, egui};

use crate::weight::{BandColor, WeightUnit};
use crate::{
    database::Database,
    models::{ExecutionSet, Lift, LiftRegion, LiftType, Muscle},
};

mod add;
mod execution_form;
mod list;
mod query;

pub fn run_gui(db: Box<dyn Database>) -> Result<(), Box<dyn std::error::Error>> {
    let app = GuiApp::new(db);
    let options = NativeOptions::default();
    eframe::run_native("Lift Trax", options, Box::new(|_cc| Box::new(app)))?;
    Ok(())
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Tab {
    Add,
    Query,
    List,
}

struct GuiApp {
    db: Box<dyn Database>,
    current_tab: Tab,
    weight_value: String,
    weight_left_value: String,
    weight_right_value: String,
    band_value: Vec<BandColor>,
    band_select: Option<BandColor>,
    chain_value: String,
    accom_mode: AccommodatingMode,
    weight_unit: WeightUnit,
    weight_mode: WeightMode,
    metric_mode: MetricMode,
    set_mode: SetMode,
    reps: String,
    sets: String,
    date: NaiveDate,
    rpe: String,
    warmup: bool,
    notes: String,
    detailed_sets: Vec<ExecutionSet>,
    selected_lift: Option<usize>,
    show_new_lift: bool,
    new_lift_name: String,
    new_lift_region: LiftRegion,
    new_lift_main: Option<LiftType>,
    new_lift_muscles: Vec<Muscle>,
    new_muscle_select: Option<Muscle>,
    new_lift_notes: String,
    editing_lift: Option<usize>,
    edit_lift_name: String,
    edit_lift_region: LiftRegion,
    edit_lift_main: Option<LiftType>,
    edit_lift_muscles: Vec<Muscle>,
    edit_muscle_select: Option<Muscle>,
    edit_lift_notes: String,
    lift_to_delete: Option<usize>,
    needs_lift_refresh: bool,
    editing_exec: Option<(usize, usize)>,
    edit_weight_value: String,
    edit_weight_left_value: String,
    edit_weight_right_value: String,
    edit_band_value: Vec<BandColor>,
    edit_band_select: Option<BandColor>,
    edit_weight_unit: WeightUnit,
    edit_weight_mode: WeightMode,
    edit_metric_mode: MetricMode,
    edit_reps: String,
    edit_sets: String,
    edit_date: NaiveDate,
    edit_rpe: String,
    edit_warmup: bool,
    edit_notes: String,
    lifts: Vec<Lift>,
    error: Option<String>,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum WeightMode {
    Weight,
    WeightLr,
    Bands,
    Accommodating,
    None,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum AccommodatingMode {
    Chains,
    Bands,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum SetMode {
    Simple,
    Detailed,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum MetricMode {
    Reps,
    Time,
    Distance,
}

impl GuiApp {
    fn new(db: Box<dyn Database>) -> Self {
        let mut app = Self {
            db,
            current_tab: Tab::Add,
            weight_value: String::new(),
            weight_left_value: String::new(),
            weight_right_value: String::new(),
            band_value: Vec::new(),
            band_select: None,
            chain_value: String::new(),
            accom_mode: AccommodatingMode::Chains,
            weight_unit: WeightUnit::Pounds,
            weight_mode: WeightMode::Weight,
            metric_mode: MetricMode::Reps,
            set_mode: SetMode::Simple,
            reps: String::new(),
            sets: String::new(),
            date: Utc::now().date_naive(),
            rpe: String::new(),
            warmup: false,
            notes: String::new(),
            detailed_sets: Vec::new(),
            selected_lift: None,
            show_new_lift: false,
            new_lift_name: String::new(),
            new_lift_region: LiftRegion::UPPER,
            new_lift_main: None,
            new_lift_muscles: Vec::new(),
            new_muscle_select: None,
            new_lift_notes: String::new(),
            editing_lift: None,
            edit_lift_name: String::new(),
            edit_lift_region: LiftRegion::UPPER,
            edit_lift_main: None,
            edit_lift_muscles: Vec::new(),
            edit_muscle_select: None,
            edit_lift_notes: String::new(),
            lift_to_delete: None,
            needs_lift_refresh: false,
            editing_exec: None,
            edit_weight_value: String::new(),
            edit_weight_left_value: String::new(),
            edit_weight_right_value: String::new(),
            edit_band_value: Vec::new(),
            edit_band_select: None,
            edit_weight_unit: WeightUnit::Pounds,
            edit_weight_mode: WeightMode::Weight,
            edit_metric_mode: MetricMode::Reps,
            edit_reps: String::new(),
            edit_sets: String::new(),
            edit_date: Utc::now().date_naive(),
            edit_rpe: String::new(),
            edit_warmup: false,
            edit_notes: String::new(),
            lifts: Vec::new(),
            error: None,
        };
        app.refresh_lifts();
        app
    }

    fn refresh_lifts(&mut self) {
        match self.db.list_lifts(None) {
            Ok(mut l) => {
                l.sort_by(|a, b| a.name.cmp(&b.name));
                let selected_name = self
                    .selected_lift
                    .and_then(|i| self.lifts.get(i))
                    .map(|l| l.name.clone());
                self.lifts = l;
                self.selected_lift = selected_name
                    .and_then(|name| self.lifts.iter().position(|l| l.name == name))
                    .or_else(|| if self.lifts.is_empty() { None } else { Some(0) });
            }
            Err(e) => self.error = Some(e.to_string()),
        }
    }
}

impl eframe::App for GuiApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut Frame) {
        egui::TopBottomPanel::top("tabs").show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.selectable_value(&mut self.current_tab, Tab::Add, "Add Execution");
                ui.selectable_value(&mut self.current_tab, Tab::Query, "Query");
                ui.selectable_value(&mut self.current_tab, Tab::List, "Executions");
            });
        });
        egui::CentralPanel::default().show(ctx, |ui| {
            egui::ScrollArea::vertical().show(ui, |ui| match self.current_tab {
                Tab::Add => self.tab_add(ui, ctx),
                Tab::Query => self.tab_query(ui),
                Tab::List => self.tab_list(ui),
            });
        });
    }
}

pub(super) fn main_lift_options() -> Vec<(Option<LiftType>, &'static str)> {
    let mut opts = vec![
        (None, "None"),
        (Some(LiftType::Accessory), "Accessory"),
        (Some(LiftType::BenchPress), "Bench Press"),
        (Some(LiftType::Conditioning), "Conditioning"),
        (Some(LiftType::Deadlift), "Deadlift"),
        (Some(LiftType::OverheadPress), "Overhead Press"),
        (Some(LiftType::Squat), "Squat"),
        (Some(LiftType::Mobility), "Mobility"),
    ];
    opts.sort_by(|a, b| a.1.cmp(b.1));
    opts
}

pub(super) fn combo_box_width(ui: &egui::Ui, texts: &[String]) -> f32 {
    let font_id = egui::TextStyle::Button.resolve(ui.style());
    let icon_width = ui.spacing().icon_width;
    let padding = ui.spacing().button_padding.x * 2.0;
    let ctx = ui.ctx().clone();
    ctx.fonts(|f| {
        let max = texts
            .iter()
            .map(|t| {
                f.layout_no_wrap(t.clone(), font_id.clone(), egui::Color32::WHITE)
                    .rect
                    .width()
            })
            .fold(0.0_f32, f32::max);
        max + icon_width + padding
    })
}
