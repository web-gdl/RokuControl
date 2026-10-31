package app.rokuremote.ecp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Aplicación (canal) instalada en el Roku. */
public final class RokuApp {
    public final String id;
    public final String name;

    public RokuApp(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /** Interpreta {@code GET /query/apps}. Ignora entradas sin id numérico/alfanumérico válido. */
    public static List<RokuApp> parseList(String xml) {
        List<RokuApp> out = new ArrayList<>();
        for (SimpleXml.Element e : SimpleXml.leaves(xml)) {
            if (!e.name.equals("app")) continue;
            String id = e.attrs.get("id");
            if (id == null || !id.matches("[A-Za-z0-9_.-]{1,32}") || e.text.isEmpty()) continue;
            out.add(new RokuApp(id, e.text.length() > 60 ? e.text.substring(0, 60) : e.text));
        }
        Collections.sort(out, (a, b) -> a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT)));
        return out;
    }
}
