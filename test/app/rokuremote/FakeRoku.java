package app.rokuremote;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Roku simulado: sirve los endpoints ECP que usa la app y registra cada petición recibida. */
final class FakeRoku {
    final List<String> requests = new CopyOnWriteArrayList<>();
    volatile boolean isTv = true;
    volatile String powerMode = "PowerOn";
    volatile int keyStatus = 200;
    volatile boolean modernFlags = false; // campos de Roku OS 15+
    volatile String serial = "X00100000001";
    volatile String name = "Sala";
    volatile boolean garbage = false;
    volatile int oversizeBytes = 0;

    private HttpServer server;
    private int port;

    int port() { return port; }

    void start(int wantedPort) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", wantedPort), 0);
        port = server.getAddress().getPort();
        server.createContext("/", ex -> {
            String line = ex.getRequestMethod() + " " + ex.getRequestURI().getRawPath();
            requests.add(line);
            String path = ex.getRequestURI().getRawPath();
            int status = 200;
            String body = "";
            if (garbage) {
                body = "<html>no soy un roku</html>";
            } else if (oversizeBytes > 0) {
                body = new String(new char[oversizeBytes]).replace('\0', 'a');
            } else if (path.equals("/query/device-info") && ex.getRequestMethod().equals("GET")) {
                body = deviceInfo();
            } else if (path.equals("/query/apps") && ex.getRequestMethod().equals("GET")) {
                body = "<apps><app id=\"12\" type=\"appl\" version=\"4.1\">Netflix</app>"
                        + "<app id=\"837\" type=\"appl\" version=\"1.0\">YouTube &amp; Co</app>"
                        + "<app id=\"bad id!\" type=\"appl\" version=\"1\">Roto</app>"
                        + "<app id=\"2285\" type=\"appl\" version=\"2\">Hulu</app></apps>";
            } else if (path.startsWith("/keypress/") && ex.getRequestMethod().equals("POST")) {
                status = keyStatus;
            } else if (path.startsWith("/launch/") && ex.getRequestMethod().equals("POST")) {
                status = 200;
            } else {
                status = 404;
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
            if (b.length > 0) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(b);
                }
            }
            ex.close();
        });
        server.start();
    }

    void stop() {
        if (server != null) server.stop(0);
        server = null;
    }

    private String deviceInfo() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" ?><device-info>");
        sb.append("<udn>uuid-1</udn><serial-number>").append(serial).append("</serial-number>");
        sb.append("<device-id>DEV1</device-id><model-name>").append(isTv ? "Roku TV Test" : "Roku Express").append("</model-name>");
        sb.append("<user-device-name>").append(name).append("</user-device-name>");
        sb.append("<software-version>14.1.0</software-version>");
        sb.append("<power-mode>").append(powerMode).append("</power-mode>");
        if (modernFlags) {
            sb.append("<supports-tv-power-control>").append(isTv).append("</supports-tv-power-control>");
            sb.append("<supports-audio-volume-control>").append(isTv).append("</supports-audio-volume-control>");
        } else {
            sb.append("<is-tv>").append(isTv).append("</is-tv>");
        }
        return sb.append("</device-info>").toString();
    }
}
