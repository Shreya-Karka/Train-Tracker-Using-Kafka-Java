package demo.kafka;

/**
 * Curated metro-to-metro route waypoints (approximate, public-road/rail corridor style),
 * Houston (Downtown) -> Denton (Downtown). You can tweak or densify later.
 */
public final class Route {
    private Route() {}

    public static final String ROUTE_NAME = "Houston → Denton (metro)";

    // [lat, lon] pairs, ordered south -> north.
    // These follow a plausible corridor via Conroe, Huntsville, College Station, Waco, Fort Worth.
    public static final double[][] HOUSTON_TO_DENTON = new double[][]{
            {29.76040, -95.36980}, // Houston (Downtown)
            {29.78250, -95.39130}, // Near Downtown/Heights
            {29.83450, -95.41450}, // North Houston
            {29.92590, -95.43050}, // Aldine-ish
            {30.07990, -95.41720}, // Spring
            {30.16580, -95.46130}, // The Woodlands
            {30.31190, -95.45610}, // Conroe
            {30.42440, -95.47970}, // Willis
            {30.54620, -95.50290}, // New Waverly
            {30.72350, -95.55080}, // Huntsville
            {30.86060, -95.63150}, // North of Huntsville
            {30.97850, -96.07300}, // I-45 to TX-30/TX-6 arc
            {30.62700, -96.33440}, // College Station
            {30.67440, -96.36980}, // Bryan
            {30.87850, -96.59300}, // Hearne
            {30.97850, -96.67360}, // Calvert
            {31.19530, -96.88200}, // Falls County arc
            {31.30660, -96.89890}, // Marlin
            {31.43000, -96.99000}, // Lorena area
            {31.54930, -97.14670}, // Waco
            {31.79470, -97.09160}, // Abbott/Hillsboro arc
            {32.01050, -97.13000}, // Hillsboro
            {32.16290, -97.15000}, // Itasca
            {32.40630, -97.21200}, // Alvarado
            {32.54210, -97.32080}, // Burleson
            {32.68000, -97.33000}, // South Fort Worth
            {32.75550, -97.33080}, // Fort Worth (Downtown)
            {32.83430, -97.22890}, // North Richland Hills
            {32.93430, -97.25170}, // Keller
            {33.00400, -97.22890}, // Roanoke
            {33.07000, -97.21000}, // Northlake
            {33.12150, -97.18420}, // Argyle
            {33.18000, -97.16500}, // South Denton
            {33.21480, -97.13310}  // Denton (Downtown)
    };
}
