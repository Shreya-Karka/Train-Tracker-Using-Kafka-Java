## Train Tracker with Apache Kafka (Java)

Real-time simulation of a train traveling from Houston to Denton with positions streamed via Kafka, consumed in Java, and visualized on a Leaflet web map (with smooth animation + trail).

## Live Map Tracking Train
[live map.webm](files/live%20map.webm)

## ✨ Features

- Producer streams GPS-like points along a curvy, realistic route (Houston → Denton).

- Consumer keeps only the latest position per vehicle in memory.

- Embedded Web server exposes:

    - GET /positions → live JSON of latest positions

    - GET /route → planned route (GeoJSON LineString)

    - GET /ui → simple JSON viewer (auto-refresh)

    - GET /map → interactive map with:

- 🚆 custom train icon

- Buttery-smooth motion between samples

- Trail polyline (recent path)

- Single topic: vehicle.positions.v1

- Ready to scale to multiple trains (IDs) later.

## 🧭 Architecture (high-level)
```
PositionProducer  -->  Kafka topic (vehicle.positions.v1)  -->  PositionConsumer
(Java)                   (local single-broker)               (Java)
|
| updates
v
PositionStore (in-memory)
|
WebServer (HTTP on :8080)
/positions /map

```
## 🧰 Prerequisites

- Java 23 (OpenJDK)

- Docker (Desktop) running Kafka
```
# Start Kafka (single node)
docker run -d --name kafka -p 9092:9092 -p 9093:9093 \
  -e KAFKA_CFG_NODE_ID=1 \
  -e KAFKA_CFG_PROCESS_ROLES=broker,controller \
  -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=true \
  bitnami/kafka:latest
```
- Maven (optional; IntelliJ can build too)



## 📁 Project Layout

```
src/main/java/demo/kafka/
  Bootstrap.java          # starts WebServer + PositionConsumer in same JVM
  WebServer.java          # HTTP server: /, /positions, /route, /ui, /map
  PositionConsumer.java   # Kafka consumer -> updates PositionStore
  PositionProducer.java   # single train along the route
  MultiTrainProducer.java # (optional) multiple trains in parallel
  Position.java           # POJO: vehicleId, lat, lon, speedMph, headingDeg, ts
  PositionStore.java      # ConcurrentHashMap<vehicleId, Position>
  Route.java              # curated waypoints Houston -> Denton
pom.xml                   # deps: kafka-clients, jackson, (optional) slf4j-simple

 ```

## ⚙️ Build & Run

- Run Bootstrap (starts web + consumer).
- Console should show: HTTP server running at http://localhost:8080/ and Subscribed. Polling…

- Run PositionProducer (streams points).
- You’ll see sent train-001 … logs.

Open:

http://localhost:8080/
→ links to endpoints

http://localhost:8080/map
→ live map (smooth + trail)

http://localhost:8080/positions
→ live JSON

http://localhost:8080/route
→ planned route (GeoJSON)

## 🧩 Data Model

Position (JSON)

```
{
  "vehicleId": "train-001",
  "lat": 29.7604,
  "lon": -95.3698,
  "speedMph": 55.0,
  "headingDeg": 321.0,
  "ts": 1737066123456
}
```

