package app.rokuremote.ecp;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Abstrae cómo se abren las conexiones, para poder fijarlas a la red Wi-Fi en Android
 * y usar conexiones directas en las pruebas.
 */
public interface NetBinder {
    HttpURLConnection open(URL url) throws IOException;

    void bind(DatagramSocket socket) throws IOException;

    /** Permite recibir tráfico multicast/UDP mientras dura la búsqueda. */
    void beginDiscovery();

    void endDiscovery();

    /** Implementación sin enlace especial (JVM / pruebas). */
    NetBinder DIRECT = new NetBinder() {
        @Override public HttpURLConnection open(URL url) throws IOException {
            return (HttpURLConnection) url.openConnection();
        }
        @Override public void bind(DatagramSocket socket) { }
        @Override public void beginDiscovery() { }
        @Override public void endDiscovery() { }
    };
}
