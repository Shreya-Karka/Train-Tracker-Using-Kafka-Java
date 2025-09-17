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

        // ==== Home page (now includes link to /map) ====
        http.createContext("/", ex -> respondHtml(ex, """
      <!doctype html>
      <meta charset="utf-8">
      <title>Tracker</title>
      <style>body{font:16px system-ui;margin:32px}</style>
      <h1>Tracker server is up ✅</h1>
      <ul>
        <li>JSON: <a href="/positions">/positions</a></li>
        <li>UI (raw JSON, auto-refresh): <a href="/ui">/ui</a></li>
        <li>Map (Leaflet): <a href="/map">/map</a></li>
      </ul>
    """));

        // ==== JSON of latest positions ====
        http.createContext("/positions", ex -> {
            byte[] body = WRITER.writeValueAsBytes(PositionStore.LATEST.values());
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.getResponseHeaders().add("Cache-Control", "no-store");
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.sendResponseHeaders(200, body.length);
            try (var os = ex.getResponseBody()) { os.write(body); }
        });

        // (optional) support trailing slash too
        http.createContext("/positions/", ex -> {
            byte[] body = WRITER.writeValueAsBytes(PositionStore.LATEST.values());
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.getResponseHeaders().add("Cache-Control", "no-store");
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.sendResponseHeaders(200, body.length);
            try (var os = ex.getResponseBody()) { os.write(body); }
        });

        http.createContext("/route", ex -> {
            var mapper = new ObjectMapper();
            ObjectNode feature = mapper.createObjectNode();
            feature.put("type", "Feature");
            ObjectNode props = mapper.createObjectNode();
            props.put("name", Route.ROUTE_NAME);
            feature.set("properties", props);

            ObjectNode geom = mapper.createObjectNode();
            geom.put("type", "LineString");

            // GeoJSON expects [lon, lat] pairs:
            ArrayNode coords = mapper.createArrayNode();
            for (double[] wp : Route.HOUSTON_TO_DENTON) {
                ArrayNode pair = mapper.createArrayNode();
                pair.add(wp[1]); // lon
                pair.add(wp[0]); // lat
                coords.add(pair);
            }
            geom.set("coordinates", coords);
            feature.set("geometry", geom);

            byte[] body = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(feature);
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.getResponseHeaders().add("Cache-Control", "no-store");
            ex.sendResponseHeaders(200, body.length);
            try (var os = ex.getResponseBody()) { os.write(body); }
        });


        // ==== Simple UI that shows JSON, refreshes every 3s ====
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

        // ==== Leaflet map with smooth animation + trail ====
        http.createContext("/map", ex -> {
            String html = """
    <!doctype html>
    <html>
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <title>Live Tracker Map</title>
      <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
      <style>
        html, body { height:100%; margin:0; }
        #map { height:100%; width:100%; }
        .panel { position:absolute; top:10px; left:10px; background:rgba(255,255,255,.9);
                 padding:8px 12px; border-radius:8px; font:14px system-ui; z-index:1000; }
        .muted { color:#666; }
        /* custom train icon */
        .train-icon { font-size: 28px; line-height: 28px; text-align:center;
                      text-shadow: 0 0 3px #fff, 0 0 6px #fff; }
        .train-icon .emoji { display:inline-block; transform-origin:center center; }
      </style>
    </head>
    <body>
      <div id="map"></div>
      <div class="panel">
        <div><strong>Live tracker</strong></div>
        <div class="muted">smooth animation + trail (updates every ~0.5s)</div>
        <div class="muted"><a href="/">back to home</a></div>
      </div>
      <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
      <script>
        if (typeof L === 'undefined') {
          document.body.innerHTML = '<p style="padding:20px;font:16px system-ui;color:#c00">Leaflet failed to load (CDN blocked).</p>';
        } else {
          const map = L.map('map').setView([29.7604, -95.3698], 7);
          L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap'
          }).addTo(map);

          // === config ===
          const FETCH_MS = 500;   // how often we fetch from /positions
          const ANIM_MS  = 500;   // animation duration between samples (≈ FETCH_MS)
          const TRAIL_MAX = 600;  // keep last N points in the trail per vehicle

          // === helpers ===
          const lerp = (a,b,t) => a + (b-a)*t;
          const clamp01 = (x) => Math.max(0, Math.min(1, x));
          function lerpHeading(a, b, t){
            let d = ((b - a + 540) % 360) - 180; // shortest arc
            return (a + d * t + 360) % 360;
          }
          const ease = (t) => t<0.5 ? 2*t*t : 1 - Math.pow(-2*t+2,2)/2;

          // train icon
          const trainIcon = L.divIcon({
            className: 'train-icon',
            html: '<span class="emoji">🚆</span>',
            iconSize: [32, 32],
            iconAnchor: [16, 16],
          });

          // per-vehicle animation + trail state:
          // id -> { marker, path (L.Polyline), from:{lat,lon,hdg}, to:{lat,lon,hdg}, start, dur }
          const state = new Map();
          
          // draw planned route (GeoJSON)
          (async () => {
              try {
                      const res = await fetch('/route', { cache: 'no-store' });
                      const geo = await res.json();
                      const routeLayer = L.geoJSON(geo, { style: { weight: 3, opacity: 0.6 } }).addTo(map);
                      // Fit the map to the planned route once
                      map.fitBounds(routeLayer.getBounds().pad(0.2));
                  } catch (e) {
                      console.error('failed to load route', e);
                  }
         })();
                    

          async function fetchPositions(){
            try{
              const res = await fetch('/positions', { cache: 'no-store' });
              const arr = await res.json();

              if (arr.length && state.size === 0){
                const bounds = L.latLngBounds(arr.map(p => [p.lat, p.lon]));
                map.fitBounds(bounds.pad(0.2));
              }
              const now = performance.now();

              for (const p of arr) {
                const id = p.vehicleId;
                let s = state.get(id);

                if (!s) {
                  // first sighting: create marker AND a trail polyline
                  const marker = L.marker([p.lat, p.lon], { icon: trainIcon }).addTo(map)
                                   .bindPopup(`<b>${id}</b><br>(${p.lat.toFixed(5)}, ${p.lon.toFixed(5)})`);
                  const path = L.polyline([[p.lat, p.lon]], { weight: 4, opacity: 0.85 }).addTo(map);
                  s = {
                    marker,
                    path,
                    from: { lat: p.lat, lon: p.lon, hdg: p.headingDeg ?? 0 },
                    to:   { lat: p.lat, lon: p.lon, hdg: p.headingDeg ?? 0 },
                    start: now,
                    dur: ANIM_MS
                  };
                  state.set(id, s);
                  continue;
                }

                // add latest sample to the trail (dedupe if same as last)
                const pts = s.path.getLatLngs();
                const last = pts[pts.length - 1];
                if (!last || last.lat !== p.lat || last.lng !== p.lon) {
                  pts.push(L.latLng(p.lat, p.lon));
                  // trim to last TRAIL_MAX points
                  if (pts.length > TRAIL_MAX) s.path.setLatLngs(pts.slice(-TRAIL_MAX));
                  else s.path.setLatLngs(pts);
                }

                // rebase animation from current interpolated position to the new sample
                const prog = clamp01((now - s.start) / s.dur);
                const curLat = lerp(s.from.lat, s.to.lat, ease(prog));
                const curLon = lerp(s.from.lon, s.to.lon, ease(prog));
                const curHdg = lerpHeading(s.from.hdg, s.to.hdg, ease(prog));

                s.from = { lat: curLat, lon: curLon, hdg: curHdg };
                s.to   = { lat: p.lat,  lon: p.lon,  hdg: (p.headingDeg ?? curHdg) };
                s.start = now;
                s.dur   = ANIM_MS;
              }
            } catch(e){
              console.error('fetchPositions error', e);
            }
          }

          function animate(){
            const now = performance.now();
            for (const [id, s] of state) {
              const t = clamp01((now - s.start) / s.dur);
              const tt = ease(t);

              const lat = lerp(s.from.lat, s.to.lat, tt);
              const lon = lerp(s.from.lon, s.to.lon, tt);
              const hdg = lerpHeading(s.from.hdg, s.to.hdg, tt);

              s.marker.setLatLng([lat, lon]);

              // rotate inner emoji span
              const el = s.marker.getElement();
              if (el) {
                const span = el.querySelector('.emoji');
                if (span) span.style.transform = `rotate(${hdg}deg)`;
              }

              if (s.marker.isPopupOpen()) {
                s.marker.setPopupContent(
                  `<b>${id}</b><br>(${lat.toFixed(5)}, ${lon.toFixed(5)})<br>${Math.round(hdg)}°`
                );
              }
            }
            requestAnimationFrame(animate);
          }

          // start loops
          fetchPositions();
          setInterval(fetchPositions, FETCH_MS);
          requestAnimationFrame(animate);
        }
      </script>
    </body>
    </html>
  """;
            byte[] body = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (var os = ex.getResponseBody()) { os.write(body); }
        });



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