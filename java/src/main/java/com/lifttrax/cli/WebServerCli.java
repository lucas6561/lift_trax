package com.lifttrax.cli;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.SetMetric;

public class WebServerCli {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        server.createContext("/add-execution", exchange -> handleAddExecution(exchange, db));
        server.createContext("/load-last-execution", exchange -> handleLoadLastExecution(exchange, db));
        server.createContext("/add-lift", exchange -> handleAddLift(exchange, db));
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
            String queryLift = query.getOrDefault("queryLift", "").trim();
            String activeTab = query.getOrDefault("tab", "add-execution").trim();
            String statusMessage = query.getOrDefault("status", "").trim();
            String statusType = query.getOrDefault("statusType", "").trim();
            WebUiRenderer.AddExecutionPrefill prefill = parsePrefill(query);

            List<Lift> lifts;
            try {
                lifts = new ArrayList<>(db.listLifts());
                lifts.sort(Comparator.comparing(Lift::name));
            } catch (Exception listError) {
                lifts = List.of();
                statusType = "error";
                statusMessage = "No lifts table found. Initialize or provide a populated database.";
            }

            String body = WebUiRenderer.renderIndexBody(db, lifts, search, queryLift, activeTab, statusMessage, statusType, prefill);
            sendHtml(exchange, WebHtml.wrapPage("LiftTrax Lifts", body));
        } catch (Exception e) {
            sendHtml(exchange, WebHtml.wrapPage("Error", "<h1>Error</h1><pre>" + WebHtml.escapeHtml(e.getMessage()) + "</pre>"));
        }
    }

    private static void handleAddExecution(HttpExchange exchange, SqliteDb db) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        try {
            Map<String, String> form = parseForm(exchange.getRequestBody());
            String liftName = form.getOrDefault("lift", "").trim();
            if (liftName.isBlank()) {
                redirect(exchange, "/?tab=add-execution&statusType=error&status=Lift%20is%20required");
                return;
            }

            LocalDate date = Optional.ofNullable(form.get("date"))
                    .filter(value -> !value.isBlank())
                    .map(LocalDate::parse)
                    .orElse(LocalDate.now());

            int setCount = parsePositiveInt(form.getOrDefault("setCount", "1"), "Set count");
            String weight = form.getOrDefault("weight", "").trim();
            Float rpe = parseOptionalFloat(form.get("rpe"));

            List<ExecutionSet> sets = parseDetailedSets(form.getOrDefault("detailedSets", "[]"));
            if (sets.isEmpty()) {
                SetMetric metric = parseMetric(form);
                sets = new ArrayList<>();
                for (int i = 0; i < setCount; i++) {
                    sets.add(new ExecutionSet(metric, weight, rpe));
                }
            }

            LiftExecution execution = new LiftExecution(
                    null,
                    date,
                    sets,
                    form.containsKey("warmup"),
                    form.containsKey("deload"),
                    form.getOrDefault("notes", "")
            );
            db.addLiftExecution(liftName, execution);
            redirect(exchange, "/?tab=add-execution&statusType=success&status=Execution%20saved&prefillLift=" + WebUiRenderer.urlEncode(liftName));
        } catch (Exception e) {
            redirect(exchange, "/?tab=add-execution&statusType=error&status=" + WebUiRenderer.urlEncode("Failed to save execution: " + e.getMessage()));
        }
    }

    private static void handleLoadLastExecution(HttpExchange exchange, SqliteDb db) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            String liftName = query.getOrDefault("lift", "").trim();
            if (liftName.isBlank()) {
                redirect(exchange, "/?tab=add-execution&statusType=error&status=Lift%20is%20required%20for%20Load%20Last");
                return;
            }
            boolean warmup = query.containsKey("warmup");
            boolean deload = query.containsKey("deload");

            List<LiftExecution> executions = db.getExecutions(liftName);
            LiftExecution last = selectExecutionForFlags(executions, warmup, deload);
            if (last == null) {
                String criteria = "warmup=" + warmup + ", deload=" + deload;
                redirect(exchange, "/?tab=add-execution&statusType=error&status=" + WebUiRenderer.urlEncode("No prior executions for " + liftName + " with " + criteria) + "&prefillLift=" + WebUiRenderer.urlEncode(liftName));
                return;
            }

            StringBuilder redirectUrl = new StringBuilder("/?tab=add-execution&statusType=success&status=Loaded%20last%20execution");
            redirectUrl.append("&prefillLift=").append(WebUiRenderer.urlEncode(liftName));
            redirectUrl.append("&prefillWeight=").append(WebUiRenderer.urlEncode(last.sets().isEmpty() ? "" : safe(last.sets().get(0).weight())));
            redirectUrl.append("&prefillSetCount=").append(last.sets().size());
            redirectUrl.append("&prefillDate=").append(WebUiRenderer.urlEncode(LocalDate.now().toString()));
            redirectUrl.append("&prefillWarmup=").append(last.warmup());
            redirectUrl.append("&prefillDeload=").append(last.deload());
            redirectUrl.append("&prefillNotes=").append(WebUiRenderer.urlEncode(safe(last.notes())));
            if (!last.sets().isEmpty()) {
                ExecutionSet first = last.sets().get(0);
                redirectUrl.append("&prefillRpe=").append(WebUiRenderer.urlEncode(first.rpe() == null ? "" : String.format(Locale.ROOT, "%s", first.rpe())));
                applyMetricPrefill(redirectUrl, first.metric());
            }

            redirect(exchange, redirectUrl.toString());
        } catch (Exception e) {
            redirect(exchange, "/?tab=add-execution&statusType=error&status=" + WebUiRenderer.urlEncode("Failed to load execution: " + e.getMessage()));
        }
    }

    private static void handleAddLift(HttpExchange exchange, SqliteDb db) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        try {
            Map<String, String> form = parseForm(exchange.getRequestBody());
            String name = form.getOrDefault("name", "").trim();
            if (name.isBlank()) {
                redirect(exchange, "/?tab=add-execution&statusType=error&status=Lift%20name%20is%20required");
                return;
            }

            LiftRegion region = LiftRegion.fromString(form.getOrDefault("region", "UPPER"));
            LiftType main = LiftType.fromDbValue(form.getOrDefault("main", "none"));
            List<Muscle> muscles = parseMuscles(form.get("muscles"));
            String notes = form.getOrDefault("notes", "");

            db.addLift(name, region, main, muscles, notes);
            redirect(exchange, "/?tab=add-execution&statusType=success&status=" + WebUiRenderer.urlEncode("Created lift: " + name) + "&prefillLift=" + WebUiRenderer.urlEncode(name));
        } catch (Exception e) {
            redirect(exchange, "/?tab=add-execution&statusType=error&status=" + WebUiRenderer.urlEncode("Failed to create lift: " + e.getMessage()));
        }
    }

    private static List<Muscle> parseMuscles(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(Muscle::fromString)
                .collect(Collectors.toList());
    }

    private static WebUiRenderer.AddExecutionPrefill parsePrefill(Map<String, String> query) {
        return new WebUiRenderer.AddExecutionPrefill(
                query.getOrDefault("prefillLift", ""),
                query.getOrDefault("prefillWeight", ""),
                query.getOrDefault("prefillSetCount", "1"),
                query.getOrDefault("prefillRpe", ""),
                query.getOrDefault("prefillMetricType", "reps"),
                query.getOrDefault("prefillMetricValue", "5"),
                query.getOrDefault("prefillMetricLeft", "5"),
                query.getOrDefault("prefillMetricRight", "5"),
                query.getOrDefault("prefillDate", ""),
                Boolean.parseBoolean(query.getOrDefault("prefillWarmup", "false")),
                Boolean.parseBoolean(query.getOrDefault("prefillDeload", "false")),
                query.getOrDefault("prefillNotes", "")
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void applyMetricPrefill(StringBuilder redirectUrl, SetMetric metric) {
        if (metric instanceof SetMetric.Reps reps) {
            redirectUrl.append("&prefillMetricType=reps");
            redirectUrl.append("&prefillMetricValue=").append(reps.reps());
            return;
        }
        if (metric instanceof SetMetric.RepsLr repsLr) {
            redirectUrl.append("&prefillMetricType=reps-lr");
            redirectUrl.append("&prefillMetricLeft=").append(repsLr.left());
            redirectUrl.append("&prefillMetricRight=").append(repsLr.right());
            return;
        }
        if (metric instanceof SetMetric.TimeSecs timeSecs) {
            redirectUrl.append("&prefillMetricType=time");
            redirectUrl.append("&prefillMetricValue=").append(timeSecs.seconds());
            return;
        }
        if (metric instanceof SetMetric.DistanceFeet distanceFeet) {
            redirectUrl.append("&prefillMetricType=distance");
            redirectUrl.append("&prefillMetricValue=").append(distanceFeet.feet());
        }
    }

    static LiftExecution selectExecutionForFlags(List<LiftExecution> executions, boolean warmup, boolean deload) {
        for (LiftExecution execution : executions) {
            if (execution.warmup() == warmup && execution.deload() == deload) {
                return execution;
            }
        }
        return null;
    }

    private static int parsePositiveInt(String value, String fieldName) {
        int parsed = Integer.parseInt(value.trim());
        if (parsed <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0");
        }
        return parsed;
    }

    private static Float parseOptionalFloat(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Float.parseFloat(value.trim());
    }

    private static SetMetric parseMetric(Map<String, String> form) {
        String metricType = form.getOrDefault("metricType", "reps").trim();
        return switch (metricType) {
            case "reps-lr" -> new SetMetric.RepsLr(
                    parsePositiveInt(form.getOrDefault("metricLeft", ""), "Left reps"),
                    parsePositiveInt(form.getOrDefault("metricRight", ""), "Right reps")
            );
            case "time" -> new SetMetric.TimeSecs(parsePositiveInt(form.getOrDefault("metricValue", ""), "Seconds"));
            case "distance" -> new SetMetric.DistanceFeet(parsePositiveInt(form.getOrDefault("metricValue", ""), "Feet"));
            default -> new SetMetric.Reps(parsePositiveInt(form.getOrDefault("metricValue", ""), "Reps"));
        };
    }

    private static List<ExecutionSet> parseDetailedSets(String json) {
        List<ExecutionSet> result = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (root == null || !root.isArray()) {
                return result;
            }
            for (JsonNode node : root) {
                Map<String, String> fields = new HashMap<>();
                fields.put("metricType", node.path("metricType").asText("reps"));
                fields.put("metricValue", node.path("metricValue").asText(""));
                fields.put("metricLeft", node.path("metricLeft").asText(""));
                fields.put("metricRight", node.path("metricRight").asText(""));
                SetMetric metric = parseMetric(fields);
                String weight = node.path("weight").asText("");
                Float rpe = parseOptionalFloat(node.path("rpe").asText(""));
                result.add(new ExecutionSet(metric, weight, rpe));
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return result;
    }

    private static Map<String, String> parseForm(InputStream body) throws IOException {
        String form = new String(body.readAllBytes(), StandardCharsets.UTF_8);
        return parseQuery(URI.create("/?" + form));
    }

    private static void handleLift(HttpExchange exchange, SqliteDb db) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI());
        String name = query.get("name");
        if (name == null || name.isBlank()) {
            sendHtml(exchange, WebHtml.wrapPage("Missing lift", "<h1>Missing lift name</h1><p><a href='/'>Back</a></p>"));
            return;
        }

        try {
            Lift lift = db.getLift(name);
            List<LiftExecution> executions = db.getExecutions(name);

            StringBuilder body = new StringBuilder();
            body.append("<p><a href='/'>← Back to all lifts</a></p>")
                    .append("<h1>").append(WebHtml.escapeHtml(lift.name())).append("</h1>")
                    .append("<p><strong>Region:</strong> ").append(WebHtml.escapeHtml(lift.region().toString())).append("</p>")
                    .append("<p><strong>Main type:</strong> ").append(WebHtml.escapeHtml(WebUiRenderer.formatMainType(lift))).append("</p>")
                    .append("<p><strong>Muscles:</strong> ").append(WebHtml.escapeHtml(WebUiRenderer.joinList(lift.muscles().stream().map(Object::toString).toList()))).append("</p>")
                    .append("<p><strong>Notes:</strong> ").append(WebHtml.escapeHtml(lift.notes() == null ? "" : lift.notes())).append("</p>")
                    .append("<h2>Executions</h2>");

            if (executions.isEmpty()) {
                body.append("<p>No executions recorded.</p>");
            } else {
                body.append("<table><thead><tr><th>Date</th><th>Sets</th><th>Notes</th></tr></thead><tbody>");
                for (LiftExecution execution : executions) {
                    body.append("<tr><td>")
                            .append(WebHtml.escapeHtml(WebUiRenderer.DATE_FORMAT.format(execution.date())))
                            .append("</td><td>")
                            .append(WebHtml.escapeHtml(WebUiRenderer.formatSets(execution.sets())))
                            .append("</td><td>")
                            .append(WebHtml.escapeHtml(execution.notes() == null ? "" : execution.notes()))
                            .append("</td></tr>");
                }
                body.append("</tbody></table>");
            }

            sendHtml(exchange, WebHtml.wrapPage(lift.name(), body.toString()));
        } catch (Exception e) {
            sendHtml(exchange, WebHtml.wrapPage("Error", "<h1>Error loading lift</h1><pre>" + WebHtml.escapeHtml(e.getMessage()) + "</pre><p><a href='/'>Back</a></p>"));
        }
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

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
