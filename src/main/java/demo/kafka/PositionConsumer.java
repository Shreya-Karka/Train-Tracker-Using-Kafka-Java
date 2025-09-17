package demo.kafka;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.util.Properties;
import demo.kafka.Position;
import demo.kafka.PositionStore;


import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;


public class PositionConsumer {
    static final String TOPIC = "vehicle.positions.v2";
    static final String GROUP = "tracker-app";
    static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        System.out.println("PositionConsumer starting...");
        System.out.println("Topic: " + TOPIC + " | Group: " + GROUP);

        // === Consumer configuration ===
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");                 // your Docker broker
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP);                                     // "tracker-app"
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");                       // start from beginning if new group
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");                         // commit after we process

        // Create the consumer (opens TCP and negotiates)
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            System.out.println("Kafka consumer ready.");

            // ask Kafka for our topic
            consumer.subscribe(List.of(TOPIC));
            System.out.println("Subscribed. Waiting for partition assignment...");
            for (int i = 0; i < 20; i++) {
                consumer.poll(Duration.ofMillis(500));
                var asg = consumer.assignment();
                if (!asg.isEmpty()) {
                    System.out.println("Assigned: " + asg);
                    break;
                }
            }
            System.out.println("Polling for positions...");

            while (true) {
                var records = consumer.poll(Duration.ofSeconds(1));

                records.forEach(r -> {
                    //System.out.println("RAW JSON: " + r.value());  // debug
                    try {
                        Position p = MAPPER.readValue(r.value(), Position.class);
                        PositionStore.LATEST.put(p.vehicleId, p);
                        System.out.printf(
                                "pos: veh=%s lat=%.5f lon=%.5f speed=%.1f heading=%.0f ts=%d (part=%d off=%d)%n",
                                p.vehicleId, p.lat, p.lon, p.speedMph, p.headingDeg, p.ts, r.partition(), r.offset()
                        );
                    } catch (Exception e) {
                        System.err.println("JSON PARSE FAILED → " + e.getMessage());
                    }
                });

                if (!records.isEmpty()) consumer.commitSync();
            }
        }
    }
}
