package demo.kafka;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.nio.charset.StandardCharsets;

public class WebServer {
    static final ObjectWriter WRITER = new ObjectMapper().writerWithDefaultPrettyPrinter();

    public static void main(String[] args) throws Exception {
        HttpServer http = HttpServer.create(new java.net.InetSocketAddress(8080), 0);

        // Root page
        http.createContext("/", ex -> respondHtml(ex, """
      <!doctype html>
      <meta charset="utf-8">
      <title>Tracker</title>
      <style>body{font:16px system-ui;margin:32px}</style>
      <h1>Tracker server is up ✅</h1>
      <p>JSON: <a href="/positions">/positions</a> • UI: <a href="/ui">/ui</a></p>
    """));

        // JSON endpoint: latest positions from shared store
        http.createContext("/positions", ex -> {
            byte[] body = WRITER.writeValueAsBytes(PositionStore.LATEST.values());
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.sendResponseHeaders(200, body.length);
            try (var os = ex.getResponseBody()) { os.write(body); }
        });

        // Simple UI that refreshes every 3s
        http.createContext("/ui", ex -> respondHtml(ex, """
      <!doctype html><meta charset="utf-8"><title>Positions UI</title>
      <style>body{font:16px system-ui;margin:24px}pre{background:#0b0b0b;color:#00ff8c;padding:12px;border-radius:8px}</style>
      <h1>Latest positions</h1>
      <p style="color:#666">Refreshing every 3s from <code>/positions</code></p>
      <pre id="out">loading…</pre>
      <script>
        async function tick(){
          try{
            const r = await fetch('/positions',{cache:'no-store'});
            const j = await r.json();
            document.getElementById('out').textContent = JSON.stringify(j, null, 2);
          }catch(e){
            document.getElementById('out').textContent = 'error: ' + e;
          }
        }
        tick(); setInterval(tick, 3000);
      </script>
    """));




        http.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        http.start();
        System.out.println("HTTP server running at http://localhost:8080/");
    }

    private static void respondHtml(HttpExchange ex, String html) throws java.io.IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (var os = ex.getResponseBody()) { os.write(body); }
    }
}
