package app.rokuremote.ecp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lector mínimo para las respuestas planas de ECP (device-info, apps).
 * No es un analizador XML general: no procesa DTD ni entidades externas, por lo que
 * una respuesta maliciosa de la red local no puede provocar lecturas de archivos ni expansión de entidades.
 */
final class SimpleXml {
    private SimpleXml() {}

    private static final Pattern ELEMENT = Pattern.compile("<([A-Za-z][A-Za-z0-9_-]*)((?:\\s+[A-Za-z_:][A-Za-z0-9_:.-]*\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))*)\\s*>([^<]*)</\\1\\s*>");
    private static final Pattern ATTR = Pattern.compile("([A-Za-z_:][A-Za-z0-9_:.-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    static final class Element {
        final String name;
        final Map<String, String> attrs;
        final String text;
        Element(String name, Map<String, String> attrs, String text) {
            this.name = name; this.attrs = attrs; this.text = text;
        }
    }

    /** Todos los elementos hoja (con texto y sin hijos) en orden de aparición. */
    static List<Element> leaves(String xml) {
        List<Element> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher m = ELEMENT.matcher(xml);
        while (m.find()) {
            Map<String, String> attrs = new HashMap<>();
            Matcher a = ATTR.matcher(m.group(2));
            while (a.find()) {
                attrs.put(a.group(1), unescape(a.group(2) != null ? a.group(2) : a.group(3)));
            }
            out.add(new Element(m.group(1), attrs, unescape(m.group(3)).trim()));
        }
        return out;
    }

    /** Primer valor de un elemento hoja con ese nombre, o null. */
    static String first(List<Element> leaves, String name) {
        for (Element e : leaves) if (e.name.equals(name)) return e.text;
        return null;
    }

    static String unescape(String s) {
        if (s == null || s.indexOf('&') < 0) return s == null ? "" : s;
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }
}
