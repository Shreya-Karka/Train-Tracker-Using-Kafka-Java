package demo.kafka;

public class Bootstrap {
    public static void main(String[] args) throws Exception {
        // Start web server on its own thread
        Thread web = new Thread(() -> {
            try { WebServer.main(new String[0]); } catch (Exception e) { e.printStackTrace(); }
        }, "webserver");
        web.start();

        // Give the web server a moment to bind port 8080
        Thread.sleep(500);

        // Start consumer (blocks in poll loop)
        PositionConsumer.main(new String[0]);
    }
}
