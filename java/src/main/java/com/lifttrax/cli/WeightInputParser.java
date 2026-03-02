package com.lifttrax.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts weight text entered by a user into structured values for the HTML form.
 * <p>
 * This class is intentionally small and focused so that it has one clear job:
 * understand weight formats such as {@code 225 lb}, {@code 45|45 lb},
 * {@code red+blue}, or {@code 225 lb+40c}.
 */
final class WeightInputParser {
    static final List<String> BAND_COLORS = List.of("orange", "red", "blue", "green", "black", "purple");

    // Example: "225 lb" or "100 kg"
    private static final Pattern SIMPLE_WEIGHT_PATTERN = Pattern.compile(
            "^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(lb|kg)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    // Example: "45|45 lb" (left plate load | right plate load)
    private static final Pattern LEFT_RIGHT_WEIGHT_PATTERN = Pattern.compile(
            "^\\s*([0-9]+(?:\\.[0-9]+)?)(?:\\s*(lb|kg))?\\s*\\|\\s*([0-9]+(?:\\.[0-9]+)?)(?:\\s*(lb|kg))?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    // Example: "225 lb+40c" (bar weight + chain weight)
    private static final Pattern ACCOMMODATING_CHAIN_PATTERN = Pattern.compile(
            "^\\s*([0-9]+(?:\\.[0-9]+)?(?:\\s*(?:lb|kg))?)\\s*\\+\\s*([0-9]+(?:\\.[0-9]+)?)c\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    // Example: "225 lb+red+blue" (bar weight + named bands)
    private static final Pattern ACCOMMODATING_BAND_PATTERN = Pattern.compile(
            "^\\s*([0-9]+(?:\\.[0-9]+)?(?:\\s*(?:lb|kg))?)\\s*\\+\\s*([a-z+]+)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private WeightInputParser() {
    }

    /**
     * Parses free-form weight text into a structured object used by the Add Execution form.
     * We test format families from most-specific to most-general, then fall back to custom text.
     */
    static WeightPrefill parseWeightPrefill(String rawWeightText) {
        String text = rawWeightText == null ? "" : rawWeightText.trim();
        if (text.isBlank() || "none".equalsIgnoreCase(text)) {
            return WeightPrefill.none();
        }

        Matcher simpleWeight = SIMPLE_WEIGHT_PATTERN.matcher(text);
        if (simpleWeight.matches()) {
            return WeightPrefill.standardWeight(simpleWeight.group(1), normalizeUnit(simpleWeight.group(2)), text);
        }

        Matcher leftRightWeight = LEFT_RIGHT_WEIGHT_PATTERN.matcher(text);
        if (leftRightWeight.matches()) {
            String unit = leftRightWeight.group(2) != null ? leftRightWeight.group(2) : leftRightWeight.group(4);
            return WeightPrefill.leftRightWeight(leftRightWeight.group(1), leftRightWeight.group(3), normalizeUnit(unit), text);
        }

        Matcher accommodatingChains = ACCOMMODATING_CHAIN_PATTERN.matcher(text);
        if (accommodatingChains.matches()) {
            ParsedNumeric barWeight = parseNumericWithUnit(accommodatingChains.group(1));
            return WeightPrefill.accommodatingWithChains(barWeight.value(), barWeight.unit(), accommodatingChains.group(2), text);
        }

        Matcher accommodatingBands = ACCOMMODATING_BAND_PATTERN.matcher(text);
        if (accommodatingBands.matches()) {
            ParsedNumeric barWeight = parseNumericWithUnit(accommodatingBands.group(1));
            List<String> bandColors = parseBandList(accommodatingBands.group(2));
            if (!bandColors.isEmpty()) {
                return WeightPrefill.accommodatingWithBands(barWeight.value(), barWeight.unit(), bandColors, text);
            }
        }

        List<String> straightBandColors = parseBandList(text);
        if (!straightBandColors.isEmpty()) {
            return WeightPrefill.bandsOnly(straightBandColors, text);
        }

        return WeightPrefill.custom(text);
    }

    private static ParsedNumeric parseNumericWithUnit(String text) {
        Matcher simple = SIMPLE_WEIGHT_PATTERN.matcher(text == null ? "" : text.trim());
        if (simple.matches()) {
            return new ParsedNumeric(simple.group(1), normalizeUnit(simple.group(2)));
        }
        return new ParsedNumeric(text == null ? "" : text.trim(), "lb");
    }

    private static String normalizeUnit(String unit) {
        return unit == null ? "lb" : unit.toLowerCase(Locale.ROOT);
    }

    /**
     * Parses strings like "red+blue" and rejects unknown colors to avoid accidental bad data.
     */
    private static List<String> parseBandList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] pieces = text.toLowerCase(Locale.ROOT).split("\\+");
        List<String> normalized = new ArrayList<>();
        for (String piece : pieces) {
            String cleaned = piece.trim();
            if (cleaned.isBlank()) {
                continue;
            }
            if (!BAND_COLORS.contains(cleaned)) {
                return List.of();
            }
            normalized.add(cleaned);
        }
        return normalized;
    }

    private record ParsedNumeric(String value, String unit) {
    }

    /**
     * Holds parsed values that map directly to Add Execution form fields.
     */
    record WeightPrefill(
            String mode,
            String weightValue,
            String weightUnit,
            String leftValue,
            String rightValue,
            String lrUnit,
            List<String> bands,
            String accomBar,
            String accomUnit,
            String accomMode,
            String accomChain,
            List<String> accomBands,
            String customWeight
    ) {
        static WeightPrefill none() {
            return new WeightPrefill("none", "", "lb", "", "", "lb", List.of(), "", "lb", "chains", "", List.of(), "");
        }

        static WeightPrefill standardWeight(String value, String unit, String original) {
            return new WeightPrefill("weight", value, unit, "", "", "lb", List.of(), "", "lb", "chains", "", List.of(), original);
        }

        static WeightPrefill leftRightWeight(String left, String right, String unit, String original) {
            return new WeightPrefill("lr", "", "lb", left, right, unit, List.of(), "", "lb", "chains", "", List.of(), original);
        }

        static WeightPrefill accommodatingWithChains(String bar, String unit, String chain, String original) {
            return new WeightPrefill("accom", "", "lb", "", "", "lb", List.of(), bar, unit, "chains", chain, List.of(), original);
        }

        static WeightPrefill accommodatingWithBands(String bar, String unit, List<String> bandColors, String original) {
            return new WeightPrefill("accom", "", "lb", "", "", "lb", List.of(), bar, unit, "bands", "", bandColors, original);
        }

        static WeightPrefill bandsOnly(List<String> bandColors, String original) {
            return new WeightPrefill("bands", "", "lb", "", "", "lb", bandColors, "", "lb", "chains", "", List.of(), original);
        }

        static WeightPrefill custom(String original) {
            return new WeightPrefill("custom", "", "lb", "", "", "lb", List.of(), "", "lb", "chains", "", List.of(), original);
        }
    }
}
