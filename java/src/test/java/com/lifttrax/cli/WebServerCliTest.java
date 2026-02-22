package com.lifttrax.cli;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.SetMetric;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebServerCliTest {

    @Test
    void escapeHtmlEscapesSpecialCharacters() throws Exception {
        Method method = WebServerCli.class.getDeclaredMethod("escapeHtml", String.class);
        method.setAccessible(true);

        String escaped = (String) method.invoke(null, "<tag>'\"&");

        assertEquals("&lt;tag&gt;&#39;&quot;&amp;", escaped);
    }

    @SuppressWarnings("unchecked")
    @Test
    void parseQueryDecodesParams() throws Exception {
        Method method = WebServerCli.class.getDeclaredMethod("parseQuery", URI.class);
        method.setAccessible(true);

        Map<String, String> parsed = (Map<String, String>) method.invoke(null, URI.create("/lift?name=Back+Squat&q=front%20squat"));

        assertEquals("Back Squat", parsed.get("name"));
        assertEquals("front squat", parsed.get("q"));
    }

    @Test
    void formatSetsIncludesMetricWeightAndRpe() throws Exception {
        Method method = WebServerCli.class.getDeclaredMethod("formatSets", List.class);
        method.setAccessible(true);

        String formatted = (String) method.invoke(null, List.of(
                new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.5f),
                new ExecutionSet(new SetMetric.Reps(3), "245 lb", null)
        ));

        assertTrue(formatted.contains("5 reps @ 225 lb rpe 8.5"));
        assertTrue(formatted.contains("3 reps @ 245 lb"));
    }
    @Test
    void formatMainTypeHandlesNullMain() throws Exception {
        Method method = WebServerCli.class.getDeclaredMethod("formatMainType", Lift.class);
        method.setAccessible(true);

        Lift lift = new Lift("Mystery Lift", null, null, List.of(), "");
        String formatted = (String) method.invoke(null, lift);

        assertEquals("Unknown", formatted);
    }

}
