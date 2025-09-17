package demo.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;
import demo.kafka.Route;


public class PositionProducer {
    // === Topic where we’ll stream GPS points ===
    static final String TOPIC = "vehicle.positions.v2";

    // === Simulated vehicle identity ===
    static final String VEHICLE_ID = "train-001";

    // === Route: Houston -> Denton (approx) ===
    static final double START_LAT = 29.7604;   // Houston
    static final double START_LON = -95.3698;
    static final double END_LAT   = 33.2148;   // Denton
    static final double END_LON   = -97.1331;

    public static void main(String[] args) throws Exception{
        System.out.println("PositionProducer starting...");
        System.out.println("Vehicle: " + VEHICLE_ID);
        System.out.println("Route: (" + START_LAT + "," + START_LON + ") -> (" + END_LAT + "," + END_LON + ")");
        System.out.println("Topic: " + TOPIC);

        // === Producer configuration ===
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");       // your Docker Kafka
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // good, safe defaults for learning
        props.put(ProducerConfig.ACKS_CONFIG, "all");                // safest ack
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true"); // avoid duplicates on retry

        // create the producer (opens TCP, discovers cluster)
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            System.out.println("KafkaProducer ready.");

            // === stream along the route ===
            final int STEPS_PER_SEGMENT = 20; // 20 * ~33 segments ≈ 660 total points (nice & smooth)
            final long PAUSE_MS = 300;        // adjust speed visually on the map
            final double DEFAULT_SPEED = 55.0; // cosmetic field


            for (int seg = 0; seg < Route.HOUSTON_TO_DENTON.length - 1; seg++) {
                double latA = Route.HOUSTON_TO_DENTON[seg][0];
                double lonA = Route.HOUSTON_TO_DENTON[seg][1];
                double latB = Route.HOUSTON_TO_DENTON[seg+1][0];
                double lonB = Route.HOUSTON_TO_DENTON[seg+1][1];

                double headingDeg = bearingDeg(latA, lonA, latB, lonB);

                for (int s = 0; s < STEPS_PER_SEGMENT; s++) {
                    double t = (double) s / STEPS_PER_SEGMENT; // 0..1
                    double lat = lerp(latA, latB, t);
                    double lon = lerp(lonA, lonB, t);
                    long ts = System.currentTimeMillis();

                    String key = VEHICLE_ID;
                    String value = String.format(
                            "{\"vehicleId\":\"%s\",\"lat\":%.6f,\"lon\":%.6f," +
                                    "\"speedMph\":%.1f,\"headingDeg\":%.1f,\"ts\":%d}",
                            key, lat, lon, DEFAULT_SPEED, headingDeg, ts
                    );

                    double latSnap = lat, lonSnap = lon;
                    int pct = (int) Math.round(100.0 * (seg + t) / (Route.HOUSTON_TO_DENTON.length - 1));

                    ProducerRecord<String, String> rec = new ProducerRecord<>(TOPIC, key, value);
                    producer.send(rec, (m, ex) -> {
                        if (ex != null) {
                            System.err.println("Send failed: " + ex.getMessage());
                        } else {
                            System.out.printf("sent %s %3d%% -> %s-%d@%d (lat=%.5f, lon=%.5f)%n",
                                    key, pct, m.topic(), m.partition(), m.offset(), latSnap, lonSnap);
                        }
                    });

                    Thread.sleep(PAUSE_MS);
                }
            }
            producer.flush();


        }

    }

    // linear interpolation helper
    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t; // t in [0,1]
    }

    // very simple bearing (degrees) from start->end (good enough for our demo)
    private static double bearingDeg(double lat1, double lon1, double lat2, double lon2) {
        double rlat1 = Math.toRadians(lat1), rlat2 = Math.toRadians(lat2);
        double dLon  = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(rlat2);
        double x = Math.cos(rlat1)*Math.sin(rlat2) - Math.sin(rlat1)*Math.cos(rlat2)*Math.cos(dLon);
        double brng = Math.toDegrees(Math.atan2(y, x));
        return (brng + 360.0) % 360.0;
    }

}
