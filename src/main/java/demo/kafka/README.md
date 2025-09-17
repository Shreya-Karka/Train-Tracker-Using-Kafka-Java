## Train Tracker with Apache Kafka (Java)

Real-time simulation of a train traveling from Houston to Denton with positions streamed via Kafka, consumed in Java, and visualized on a Leaflet web map (with smooth animation + trail).

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

- Docker (Desktop) running

- Maven (optional; IntelliJ can build too)