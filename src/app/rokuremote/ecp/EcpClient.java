package app.rokuremote.ecp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Cliente HTTP del External Control Protocol de Roku (puerto 8060, red local).
 * Todas las llamadas son bloqueantes: deben ejecutarse fuera del hilo de interfaz.
 */
public final class EcpClient {
    public static final int PORT = 8060;
    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int READ_TIMEOUT_MS = 2500;
    private static final int MAX_BODY = 256 * 1024;

    /** Error de comunicación con el Roku; {@code httpCode} es 0 si no hubo respuesta HTTP. */
    public static final class EcpException extends IOException {
        private static final long serialVersionUID = 1L;
        public final int httpCode;
        public EcpException(String message, int httpCode, Throwable cause) {
            super(message, cause);
            this.httpCode = httpCode;
        }
        /** 401/403: el Roku rechaza el control («Control por aplicaciones móviles» limitado o desactivado). */
        public boolean isAccessDenied() {
            return httpCode == 401 || httpCode == 403;
        }
    }

    private final String host;
    private final int port;
    private final NetBinder net;

    public EcpClient(String host, NetBinder net) {
        this(host, PORT, net);
    }

    /** El puerto distinto de 8060 solo existe para pruebas con un Roku simulado. */
    public EcpClient(String host, int port, NetBinder net) {
        this.host = host;
        this.port = port;
        this.net = net;
    }

    public DeviceInfo deviceInfo() throws EcpException {
        DeviceInfo info = DeviceInfo.parse(request("GET", "/query/device-info"));
        if (info == null) throw new EcpException("Respuesta inesperada: no es un Roku", 0, null);
        return info;
    }

    public List<RokuApp> apps() throws EcpException {
        return RokuApp.parseList(request("GET", "/query/apps"));
    }

    public void key(String key) throws EcpException {
        if (!key.matches("[A-Za-z0-9]{1,24}")) throw new IllegalArgumentException("tecla inválida");
        request("POST", "/keypress/" + key);
    }

    /** Envía un carácter al teclado en pantalla del Roku (UTF-8 codificado en porcentaje). */
    public void literal(int codePoint) throws EcpException {
        String ch = new String(Character.toChars(codePoint));
        String enc;
        try {
            enc = URLEncoder.encode(ch, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        request("POST", "/keypress/Lit_" + enc);
    }

    public void launch(String appId) throws EcpException {
        if (!appId.matches("[A-Za-z0-9_.-]{1,32}")) throw new IllegalArgumentException("id inválido");
        request("POST", "/launch/" + appId);
    }

    private String request(String method, String path) throws EcpException {
        HttpURLConnection c = null;
        try {
            c = net.open(new URL("http", host, port, path));
            c.setRequestMethod(method);
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(READ_TIMEOUT_MS);
            c.setInstanceFollowRedirects(false);
            c.setUseCaches(false);
            if (method.equals("POST")) {
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(0);
                c.getOutputStream().close();
            }
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new EcpException("HTTP " + code, code, null);
            }
            return readBody(c.getInputStream());
        } catch (EcpException e) {
            throw e;
        } catch (IOException e) {
            throw new EcpException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 0, e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readBody(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BODY) throw new IOException("Respuesta demasiado grande");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
