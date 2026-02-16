package com.lifttrax.workout;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WaveMarkdownWriter {
    private WaveMarkdownWriter() {}

    public static List<String> createMarkdown(List<Map<DayOfWeek, Workout>> wave) {
        List<String> out = new ArrayList<>();
        DayOfWeek[] order = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
        };

        for (int i = 0; i < wave.size(); i++) {
            out.add("# Week " + (i + 1));
            Map<DayOfWeek, Workout> week = wave.get(i);
            for (DayOfWeek day : order) {
                Workout workout = week.get(day);
                if (workout == null) {
                    continue;
                }
                out.add("## " + title(day));
                for (WorkoutLift lift : workout.lifts()) {
                    out.add("### " + lift.name());
                    if (lift.kind() instanceof WorkoutLiftKind.SingleKind sk) {
                        out.add("- " + sk.singleLift().lift().name());
                    } else if (lift.kind() instanceof WorkoutLiftKind.CircuitKind ck) {
                        for (SingleLift sl : ck.circuitLift().circuitLifts()) {
                            out.add("- " + sl.lift().name());
                        }
                    }
                }
                out.add("");
            }
            out.add("");
        }
        return out;
    }

    private static String title(DayOfWeek day) {
        return day.name().charAt(0) + day.name().substring(1).toLowerCase();
    }
}
