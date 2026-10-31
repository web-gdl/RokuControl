package app.rokuremote.ecp;

/**
 * Validación de direcciones. La app solo habla con direcciones IPv4 de red local:
 * así una IP mal escrita nunca envía tráfico a Internet.
 */
public final class Hosts {
    private Hosts() {}

    /** Devuelve los 4 octetos o null si no es una IPv4 en formato decimal estricto. */
    static int[] parseIpv4(String s) {
        if (s == null) return null;
        String[] p = s.split("\\.", -1);
        if (p.length != 4) return null;
        int[] o = new int[4];
        for (int i = 0; i < 4; i++) {
            String t = p[i];
            if (t.isEmpty() || t.length() > 3) return null;
            for (int k = 0; k < t.length(); k++) {
                if (t.charAt(k) < '0' || t.charAt(k) > '9') return null;
            }
            int v = Integer.parseInt(t);
            if (v > 255) return null;
            o[i] = v;
        }
        return o;
    }

    /** true si es IPv4 privada (10/8, 172.16/12, 192.168/16) o de enlace local (169.254/16). */
    public static boolean isLocalIpv4(String s) {
        int[] o = parseIpv4(s);
        if (o == null) return false;
        if (o[0] == 10) return true;
        if (o[0] == 172 && o[1] >= 16 && o[1] <= 31) return true;
        if (o[0] == 192 && o[1] == 168) return true;
        return o[0] == 169 && o[1] == 254;
    }

    /**
     * Destinos a los que la app puede conectarse: red local (ver {@link #isLocalIpv4}) o el propio
     * teléfono (127.0.0.0/8, el tráfico nunca sale del dispositivo). Cualquier otro se rechaza.
     */
    public static boolean isAllowedTarget(String s) {
        int[] o = parseIpv4(s);
        return o != null && (o[0] == 127 || isLocalIpv4(s));
    }

    /** Quita espacios y devuelve la IP normalizada (sin ceros a la izquierda) o null si no es válida/local. */
    public static String normalizeLocal(String input) {
        if (input == null) return null;
        int[] o = parseIpv4(input.trim());
        if (o == null) return null;
        String n = o[0] + "." + o[1] + "." + o[2] + "." + o[3];
        return isLocalIpv4(n) ? n : null;
    }
}
