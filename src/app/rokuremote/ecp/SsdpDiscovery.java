package app.rokuremote.ecp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Búsqueda de Roku por SSDP (M-SEARCH, ST: roku:ecp) en la red local. */
public final class SsdpDiscovery {
    private static final String GROUP = "239.255.255.250";
    private static final int SSDP_PORT = 1900;

    private SsdpDiscovery() {}

    static final String M_SEARCH = "M-SEARCH * HTTP/1.1\r\n"
            + "Host: 239.255.255.250:1900\r\n"
            + "Man: \"ssdp:discover\"\r\n"
            + "ST: roku:ecp\r\n"
            + "MX: 2\r\n"
            + "\r\n";

    /**
     * Busca durante {@code durationMs} y devuelve las IP (únicas, solo locales) que respondieron.
     * Bloqueante. Lanza IOException si no se puede abrir el socket (p. ej., sin red).
     */
    public static Set<String> discover(int durationMs, NetBinder net) throws IOException {
        return discover(GROUP, SSDP_PORT, durationMs, net);
    }

    /** Variante con destino configurable (solo para pruebas con un respondedor local). */
    static Set<String> discover(String group, int port, int durationMs, NetBinder net) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        net.beginDiscovery();
        DatagramSocket socket = new DatagramSocket();
        try {
            net.bind(socket);
            socket.setSoTimeout(400);
            byte[] msg = M_SEARCH.getBytes(StandardCharsets.US_ASCII);
            InetAddress addr = InetAddress.getByName(group);
            long end = System.currentTimeMillis() + durationMs;
            long nextSend = 0;
            byte[] buf = new byte[1500];
            while (System.currentTimeMillis() < end) {
                if (System.currentTimeMillis() >= nextSend) {
                    socket.send(new DatagramPacket(msg, msg.length, addr, port));
                    nextSend = System.currentTimeMillis() + 1200;
                }
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(p);
                } catch (SocketTimeoutException e) {
                    continue;
                }
                String ip = parseResponse(new String(p.getData(), 0, p.getLength(), StandardCharsets.US_ASCII));
                if (ip != null) found.add(ip);
            }
        } finally {
            socket.close();
            net.endDiscovery();
        }
        return found;
    }

    /** Extrae la IP del encabezado LOCATION de una respuesta de Roku; null si no corresponde. */
    static String parseResponse(String text) {
        String[] lines = text.split("\r?\n");
        if (lines.length == 0 || !lines[0].startsWith("HTTP/1.1 200")) return null;
        boolean roku = false;
        String location = null;
        for (String line : lines) {
            int i = line.indexOf(':');
            if (i <= 0) continue;
            String k = line.substring(0, i).trim().toLowerCase(Locale.ROOT);
            String v = line.substring(i + 1).trim();
            if (k.equals("st") && v.equals("roku:ecp")) roku = true;
            if (k.equals("location")) location = v;
        }
        if (!roku || location == null) return null;
        try {
            String host = new URI(location).getHost();
            return Hosts.isLocalIpv4(host) ? host : null;
        } catch (Exception e) {
            return null;
        }
    }
}
