use chrono::NaiveDate;
use eframe::egui;
use egui_extras::DatePickerButton;

use crate::weight::{BandColor, WeightUnit};

use super::{AccommodatingMode, MetricMode, WeightMode, combo_box_width};

pub(super) fn execution_form<F>(
    ui: &mut egui::Ui,
    id_prefix: &'static str,
    weight_mode: &mut WeightMode,
    weight_unit: &mut WeightUnit,
    weight_value: &mut String,
    weight_left_value: &mut String,
    weight_right_value: &mut String,
    band_value: &mut Vec<BandColor>,
    band_select: &mut Option<BandColor>,
    chain_value: &mut String,
    accom_mode: &mut AccommodatingMode,
    metric_mode: &mut MetricMode,
    warmup: &mut bool,
    date: &mut NaiveDate,
    notes: &mut String,
    mut sets_ui: F,
) where
    F: FnMut(&mut egui::Ui, MetricMode),
{
    ui.horizontal(|ui| {
        ui.label("Input:");
        ui.selectable_value(weight_mode, WeightMode::Weight, "Weight");
        ui.selectable_value(weight_mode, WeightMode::WeightLr, "L/R Weight");
        ui.selectable_value(weight_mode, WeightMode::Bands, "Bands");
        ui.selectable_value(weight_mode, WeightMode::Accommodating, "Accom");
        ui.selectable_value(weight_mode, WeightMode::None, "None");
    });
    match *weight_mode {
        WeightMode::Weight => {
            ui.horizontal(|ui| {
                ui.label("Weight:");
                ui.text_edit_singleline(weight_value);
                let unit_width = combo_box_width(ui, &vec!["kg".into(), "lb".into()]);
                egui::ComboBox::from_id_source(format!("{id_prefix}_unit"))
                    .width(unit_width)
                    .selected_text(match weight_unit {
                        WeightUnit::Kilograms => "kg",
                        WeightUnit::Pounds => "lb",
                    })
                    .show_ui(ui, |ui| {
                        ui.selectable_value(weight_unit, WeightUnit::Kilograms, "kg");
                        ui.selectable_value(weight_unit, WeightUnit::Pounds, "lb");
                    });
            });
        }
        WeightMode::WeightLr => {
            ui.horizontal(|ui| {
                ui.label("Weight:");
                ui.label("L:");
                ui.text_edit_singleline(weight_left_value);
                ui.label("R:");
                ui.text_edit_singleline(weight_right_value);
                let unit_width = combo_box_width(ui, &vec!["kg".into(), "lb".into()]);
                egui::ComboBox::from_id_source(format!("{id_prefix}_unit"))
                    .width(unit_width)
                    .selected_text(match weight_unit {
                        WeightUnit::Kilograms => "kg",
                        WeightUnit::Pounds => "lb",
                    })
                    .show_ui(ui, |ui| {
                        ui.selectable_value(weight_unit, WeightUnit::Kilograms, "kg");
                        ui.selectable_value(weight_unit, WeightUnit::Pounds, "lb");
                    });
            });
        }
        WeightMode::Bands => {
            ui.horizontal(|ui| {
                ui.label("Bands:");
                let text = if band_value.is_empty() {
                    "None".to_string()
                } else {
                    band_value
                        .iter()
                        .map(|b| b.to_string())
                        .collect::<Vec<_>>()
                        .join("+")
                };
                ui.label(text);
                let mut colors = vec![
                    BandColor::Orange,
                    BandColor::Red,
                    BandColor::Blue,
                    BandColor::Green,
                    BandColor::Black,
                    BandColor::Purple,
                ];
                colors.sort_by(|a, b| a.to_string().cmp(&b.to_string()));
                let mut color_strings: Vec<String> =
                    colors.iter().map(|c| c.to_string()).collect();
                color_strings.push("Select".into());
                let band_width = combo_box_width(ui, &color_strings);
                egui::ComboBox::from_id_source(format!("{id_prefix}_band_select"))
                    .width(band_width)
                    .selected_text(
                        band_select
                            .map(|b| b.to_string())
                            .unwrap_or_else(|| "Select".into()),
                    )
                    .show_ui(ui, |ui| {
                        for color in &colors {
                            ui.selectable_value(band_select, Some(*color), color.to_string());
                        }
                    });
                if ui.button("Add").clicked() {
                    if let Some(color) = *band_select {
                        band_value.push(color);
                    }
                }
                if ui.button("Clear").clicked() {
                    band_value.clear();
                    *band_select = None;
                }
            });
        }
        WeightMode::Accommodating => {
            ui.horizontal(|ui| {
                ui.label("Bar:");
                ui.text_edit_singleline(weight_value);
                let unit_width = combo_box_width(ui, &vec!["kg".into(), "lb".into()]);
                egui::ComboBox::from_id_source(format!("{id_prefix}_unit"))
                    .width(unit_width)
                    .selected_text(match weight_unit {
                        WeightUnit::Kilograms => "kg",
                        WeightUnit::Pounds => "lb",
                    })
                    .show_ui(ui, |ui| {
                        ui.selectable_value(weight_unit, WeightUnit::Kilograms, "kg");
                        ui.selectable_value(weight_unit, WeightUnit::Pounds, "lb");
                    });
            });
            ui.horizontal(|ui| {
                ui.label("Resistance:");
                ui.selectable_value(accom_mode, AccommodatingMode::Chains, "Chains");
                ui.selectable_value(accom_mode, AccommodatingMode::Bands, "Bands");
            });
            match *accom_mode {
                AccommodatingMode::Chains => {
                    ui.horizontal(|ui| {
                        ui.label("Chain:");
                        ui.text_edit_singleline(chain_value);
                    });
                }
                AccommodatingMode::Bands => {
                    ui.horizontal(|ui| {
                        ui.label("Bands:");
                        let text = if band_value.is_empty() {
                            "None".to_string()
                        } else {
                            band_value
                                .iter()
                                .map(|b| b.to_string())
                                .collect::<Vec<_>>()
                                .join("+")
                        };
                        ui.label(text);
                        let mut colors = vec![
                            BandColor::Orange,
                            BandColor::Red,
                            BandColor::Blue,
                            BandColor::Green,
                            BandColor::Black,
                            BandColor::Purple,
                        ];
                        colors.sort_by(|a, b| a.to_string().cmp(&b.to_string()));
                        let mut color_strings: Vec<String> =
                            colors.iter().map(|c| c.to_string()).collect();
                        color_strings.push("Select".into());
                        let band_width = combo_box_width(ui, &color_strings);
                        egui::ComboBox::from_id_source(format!("{id_prefix}_accom_band_select"))
                            .width(band_width)
                            .selected_text(
                                band_select
                                    .map(|b| b.to_string())
                                    .unwrap_or_else(|| "Select".into()),
                            )
                            .show_ui(ui, |ui| {
                                for color in &colors {
                                    ui.selectable_value(band_select, Some(*color), color.to_string());
                                }
                            });
                        if ui.button("Add").clicked() {
                            if let Some(color) = *band_select {
                                band_value.push(color);
                            }
                        }
                        if ui.button("Clear").clicked() {
                            band_value.clear();
                            *band_select = None;
                        }
                    });
                }
            }
        }
        WeightMode::None => {}
    }
    ui.horizontal(|ui| {
        ui.label("Metric:");
        ui.selectable_value(metric_mode, MetricMode::Reps, "Reps");
        ui.selectable_value(metric_mode, MetricMode::Time, "Seconds");
        ui.selectable_value(metric_mode, MetricMode::Distance, "Feet");
    });
    sets_ui(ui, *metric_mode);
    ui.checkbox(warmup, "Warm-up");
    ui.horizontal(|ui| {
        ui.label("Date:");
    ui.add(DatePickerButton::new(date).id_source(id_prefix));
    });
    ui.horizontal(|ui| {
        ui.label("Notes:");
        ui.text_edit_singleline(notes);
    });
}
