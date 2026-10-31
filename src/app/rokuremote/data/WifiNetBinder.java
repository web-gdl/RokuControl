package app.rokuremote.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;

import app.rokuremote.ecp.NetBinder;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Fija el tráfico a la red Wi-Fi/Ethernet local (aunque haya datos móviles activos)
 * y toma el bloqueo de multicast mientras se buscan dispositivos.
 */
public final class WifiNetBinder implements NetBinder {
    private final ConnectivityManager cm;
    private final WifiManager.MulticastLock multicast;

    public WifiNetBinder(Context context) {
        Context app = context.getApplicationContext();
        cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        multicast = wm.createMulticastLock("rokuremote-ssdp");
        multicast.setReferenceCounted(false);
    }

    @SuppressWarnings("deprecation") // getAllNetworks: no hay alternativa síncrona sin callbacks
    private Network lanNetwork() {
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            if (c != null && (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                return n;
            }
        }
        return null; // p. ej. el teléfono es el punto de acceso: se usa la ruta predeterminada
    }

    @Override
    public HttpURLConnection open(URL url) throws IOException {
        Network n = lanNetwork();
        return (HttpURLConnection) (n != null ? n.openConnection(url) : url.openConnection());
    }

    @Override
    public void bind(DatagramSocket socket) throws IOException {
        Network n = lanNetwork();
        if (n != null) n.bindSocket(socket);
    }

    @Override
    public void beginDiscovery() {
        multicast.acquire();
    }

    @Override
    public void endDiscovery() {
        if (multicast.isHeld()) multicast.release();
    }
}
