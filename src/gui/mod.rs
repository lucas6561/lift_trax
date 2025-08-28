use eframe::{Frame, NativeOptions, egui};

use crate::weight::WeightUnit;
use crate::{
    database::Database,
    models::{Lift, LiftRegion, LiftType, Muscle},
};

mod add;
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
    band_value: String,
    weight_unit: WeightUnit,
    weight_mode: WeightMode,
    reps: String,
    sets: String,
    date: String,
    rpe: String,
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
    editing_exec: Option<(usize, usize)>,
    edit_weight_value: String,
    edit_band_value: String,
    edit_weight_unit: WeightUnit,
    edit_weight_mode: WeightMode,
    edit_reps: String,
    edit_sets: String,
    edit_date: String,
    edit_rpe: String,
    lifts: Vec<Lift>,
    error: Option<String>,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum WeightMode {
    Weight,
    Bands,
}

impl GuiApp {
    fn new(db: Box<dyn Database>) -> Self {
        let mut app = Self {
            db,
            current_tab: Tab::Add,
            weight_value: String::new(),
            band_value: String::new(),
            weight_unit: WeightUnit::Pounds,
            weight_mode: WeightMode::Weight,
            reps: String::new(),
            sets: String::new(),
            date: String::new(),
            rpe: String::new(),
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
            editing_exec: None,
            edit_weight_value: String::new(),
            edit_band_value: String::new(),
            edit_weight_unit: WeightUnit::Pounds,
            edit_weight_mode: WeightMode::Weight,
            edit_reps: String::new(),
            edit_sets: String::new(),
            edit_date: String::new(),
            edit_rpe: String::new(),
            lifts: Vec::new(),
            error: None,
        };
        app.refresh_lifts();
        app
    }

    fn refresh_lifts(&mut self) {
        match self.db.list_lifts(None) {
            Ok(l) => {
                self.lifts = l;
                if self.lifts.is_empty() {
                    self.selected_lift = None;
                } else if self.selected_lift.map_or(true, |i| i >= self.lifts.len()) {
                    self.selected_lift = Some(0);
                }
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
        egui::CentralPanel::default().show(ctx, |ui| match self.current_tab {
            Tab::Add => self.tab_add(ui, ctx),
            Tab::Query => self.tab_query(ui),
            Tab::List => self.tab_list(ui),
        });
    }
}
