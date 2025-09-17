package demo.kafka;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PositionStore {
    private PositionStore() {}
    // latest position per vehicleId
    public static final Map<String, Position> LATEST = new ConcurrentHashMap<>();
}
