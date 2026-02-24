package com.lifttrax.cli;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WebServerCli {
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
            String queryLift = query.getOrDefault("queryLift", "").trim();
            String activeTab = query.getOrDefault("tab", "add-execution").trim();

            List<Lift> lifts = new ArrayList<>(db.listLifts());
            lifts.sort(Comparator.comparing(Lift::name));

            String body = WebUiRenderer.renderIndexBody(db, lifts, search, queryLift, activeTab);
            sendHtml(exchange, WebHtml.wrapPage("LiftTrax Lifts", body));
        } catch (Exception e) {
            sendHtml(exchange, WebHtml.wrapPage("Error", "<h1>Error</h1><pre>" + WebHtml.escapeHtml(e.getMessage()) + "</pre>"));
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

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
