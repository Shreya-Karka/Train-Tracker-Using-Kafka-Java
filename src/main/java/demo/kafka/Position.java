package demo.kafka;

public class Position {
    public String vehicleId;
    public double lat;
    public double lon;
    public double speedMph;
    public double headingDeg;
    public long ts;

    public Position() {} // for Jackson

    public Position(String vehicleId, double lat, double lon,
                    double speedMph, double headingDeg, long ts) {
        this.vehicleId = vehicleId;
        this.lat = lat;
        this.lon = lon;
        this.speedMph = speedMph;
        this.headingDeg = headingDeg;
        this.ts = ts;
    }
}
