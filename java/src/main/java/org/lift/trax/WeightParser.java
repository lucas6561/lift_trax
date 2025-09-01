package org.lift.trax;

/**
 * Utility for parsing weight strings into a rough pound value. This mirrors a
 * subset of the weight parsing logic from the Rust implementation so that we
 * can compute best-by-rep statistics from textual weight entries.
 */
public class WeightParser {
    private static final double POUNDS_PER_KILOGRAM = 2.20462;

    /**
     * Parse a weight string and return the approximate total weight in pounds.
     * Non-numeric components (e.g., bands) contribute no additional weight.
     */
    public static double toLbs(String text) {
        if (text == null || text.isBlank()) return 0.0;
        text = text.trim();
        if (text.equalsIgnoreCase("none")) return 0.0;
        // Left/right weights: sum both sides
        if (text.contains("|")) {
            String[] parts = text.split("\\|");
            if (parts.length == 2) {
                return toLbs(parts[0]) + toLbs(parts[1]);
            }
        }
        // Accommodating resistance or multiple components separated by '+'
        if (text.contains("+")) {
            String[] parts = text.split("\\+");
            double base = toLbs(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.endsWith("c") || part.endsWith("C")) {
                    base += toLbs(part.substring(0, part.length() - 1));
                }
                // Bands add no explicit numeric weight
            }
            return base;
        }
        // Simple numeric weight with optional unit
        String lower = text.toLowerCase();
        try {
            if (lower.endsWith("kg")) {
                double kg = Double.parseDouble(lower.substring(0, lower.length() - 2).trim());
                return kg * POUNDS_PER_KILOGRAM;
            }
            if (lower.endsWith("lb")) {
                return Double.parseDouble(lower.substring(0, lower.length() - 2).trim());
            }
            return Double.parseDouble(lower);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
