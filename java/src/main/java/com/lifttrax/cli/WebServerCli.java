package com.lifttrax.cli;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WebServerCli {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public static void main(String[] args) throws Exception {
        String dbPath = args.length > 0 ? args[0] : "lifts.db";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        SqliteDb db = new SqliteDb(dbPath);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                db.close();
            } catch (Exception ignored) {
            }
        }));

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", exchange -> handleIndex(exchange, db));
        server.createContext("/lift", exchange -> handleLift(exchange, db));
        server.setExecutor(null);
        server.start();

        System.out.println("LiftTrax web UI started.");
        System.out.printf(Locale.ROOT, "Open http://localhost:%d or http://<your-ip>:%d from any device on your network.%n", port, port);
    }

    private static void handleIndex(HttpExchange exchange, SqliteDb db) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            String search = query.getOrDefault("q", "").trim().toLowerCase(Locale.ROOT);

            List<Lift> lifts = new ArrayList<>(db.listLifts());
            lifts.sort(Comparator.comparing(Lift::name));

            StringBuilder body = new StringBuilder();
            body.append("<h1>LiftTrax</h1>")
                    .append("<form method='get' action='/'><input type='text' name='q' placeholder='Search lifts' value='")
                    .append(escapeHtml(search))
                    .append("' /><button type='submit'>Search</button></form>")
                    .append("<ul>");

            for (Lift lift : lifts) {
                if (!search.isEmpty() && !lift.name().toLowerCase(Locale.ROOT).contains(search)) {
                    continue;
                }
                body.append("<li><a href='/lift?name=")
                        .append(urlEncode(lift.name()))
                        .append("'>")
                        .append(escapeHtml(lift.name()))
                        .append("</a> — ")
                        .append(escapeHtml(lift.region().toString()))
                        .append(" / ")
                        .append(escapeHtml(formatMainType(lift)))
                        .append("</li>");
            }

            body.append("</ul>");
            sendHtml(exchange, wrapPage("LiftTrax Lifts", body.toString()));
        } catch (Exception e) {
            sendHtml(exchange, wrapPage("Error", "<h1>Error</h1><pre>" + escapeHtml(e.getMessage()) + "</pre>"));
        }
    }

    private static void handleLift(HttpExchange exchange, SqliteDb db) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI());
        String name = query.get("name");
        if (name == null || name.isBlank()) {
            sendHtml(exchange, wrapPage("Missing lift", "<h1>Missing lift name</h1><p><a href='/'>Back</a></p>"));
            return;
        }

        try {
            Lift lift = db.getLift(name);
            List<LiftExecution> executions = db.getExecutions(name);

            StringBuilder body = new StringBuilder();
            body.append("<p><a href='/'>← Back to all lifts</a></p>")
                    .append("<h1>").append(escapeHtml(lift.name())).append("</h1>")
                    .append("<p><strong>Region:</strong> ").append(escapeHtml(lift.region().toString())).append("</p>")
                    .append("<p><strong>Main type:</strong> ").append(escapeHtml(formatMainType(lift))).append("</p>")
                    .append("<p><strong>Muscles:</strong> ").append(escapeHtml(joinList(lift.muscles().stream().map(Object::toString).toList()))).append("</p>")
                    .append("<p><strong>Notes:</strong> ").append(escapeHtml(lift.notes() == null ? "" : lift.notes())).append("</p>")
                    .append("<h2>Executions</h2>");

            if (executions.isEmpty()) {
                body.append("<p>No executions recorded.</p>");
            } else {
                body.append("<table><thead><tr><th>Date</th><th>Sets</th><th>Notes</th></tr></thead><tbody>");
                for (LiftExecution execution : executions) {
                    body.append("<tr><td>")
                            .append(escapeHtml(DATE_FORMAT.format(execution.date())))
                            .append("</td><td>")
                            .append(escapeHtml(formatSets(execution.sets())))
                            .append("</td><td>")
                            .append(escapeHtml(execution.notes() == null ? "" : execution.notes()))
                            .append("</td></tr>");
                }
                body.append("</tbody></table>");
            }

            sendHtml(exchange, wrapPage(lift.name(), body.toString()));
        } catch (Exception e) {
            sendHtml(exchange, wrapPage("Error", "<h1>Error loading lift</h1><pre>" + escapeHtml(e.getMessage()) + "</pre><p><a href='/'>Back</a></p>"));
        }
    }

    private static String formatSets(List<ExecutionSet> sets) {
        List<String> parts = new ArrayList<>();
        for (ExecutionSet set : sets) {
            String item = formatMetric(set.metric()) + " @ " + set.weight();
            if (set.rpe() != null) {
                item += " rpe " + String.format(Locale.ROOT, "%.1f", set.rpe());
            }
            parts.add(item);
        }
        return joinList(parts);
    }


    private static String formatMetric(com.lifttrax.models.SetMetric metric) {
        if (metric instanceof com.lifttrax.models.SetMetric.Reps reps) {
            return reps.reps() + " reps";
        }
        if (metric instanceof com.lifttrax.models.SetMetric.RepsLr repsLr) {
            return repsLr.left() + "L/" + repsLr.right() + "R reps";
        }
        if (metric instanceof com.lifttrax.models.SetMetric.RepsRange range) {
            return range.min() + "-" + range.max() + " reps";
        }
        if (metric instanceof com.lifttrax.models.SetMetric.TimeSecs timeSecs) {
            return timeSecs.seconds() + " sec";
        }
        if (metric instanceof com.lifttrax.models.SetMetric.DistanceFeet distanceFeet) {
            return distanceFeet.feet() + " ft";
        }
        return "unknown";
    }

    private static String formatMainType(Lift lift) {
        if (lift == null || lift.main() == null) {
            return "Unknown";
        }
        return lift.main().toDbValue();
    }

    private static String joinList(List<String> values) {
        return String.join(", ", values);
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> result = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return result;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = urlDecode(parts[0]);
            String value = parts.length == 2 ? urlDecode(parts[1]) : "";
            result.put(key, value);
        }
        return result;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String wrapPage(String title, String body) {
        return """
                <!DOCTYPE html>
                <html lang='en'>
                <head>
                  <meta charset='utf-8'/>
                  <meta name='viewport' content='width=device-width, initial-scale=1'/>
                  <title>%s</title>
                  <style>
                    body { font-family: sans-serif; margin: 2rem auto; max-width: 980px; padding: 0 1rem; background: #111827; color: #f9fafb; }
                    a { color: #60a5fa; }
                    input, button { padding: 0.5rem; border-radius: 0.35rem; border: 1px solid #374151; background: #1f2937; color: #f9fafb; }
                    button { cursor: pointer; }
                    table { border-collapse: collapse; width: 100%%; margin-top: 1rem; }
                    th, td { border: 1px solid #374151; padding: 0.45rem; vertical-align: top; text-align: left; }
                    code { background: #1f2937; padding: 0.15rem 0.35rem; border-radius: 0.25rem; }
                    li { margin-bottom: 0.35rem; }
                  </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(escapeHtml(title), body);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
