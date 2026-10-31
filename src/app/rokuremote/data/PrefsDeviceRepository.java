package app.rokuremote.data;

import android.content.Context;
import android.content.SharedPreferences;

import app.rokuremote.core.DeviceRepository;
import app.rokuremote.core.SavedDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * Guarda en SharedPreferences privadas de la app (no se respaldan ni salen del teléfono)
 * únicamente: número de serie, IP y nombre de cada Roku recordado.
 */
public final class PrefsDeviceRepository implements DeviceRepository {
    private static final String FILE = "devices";
    private static final String KEY_LIST = "list";
    private static final String KEY_LAST = "last";
    private static final int MAX_DEVICES = 20;

    private final SharedPreferences prefs;

    public PrefsDeviceRepository(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @Override
    public List<SavedDevice> load() {
        List<SavedDevice> out = new ArrayList<>();
        String raw = prefs.getString(KEY_LIST, "");
        for (String line : raw.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length == 3 && !f[0].isEmpty() && !f[1].isEmpty()) {
                out.add(new SavedDevice(f[0], f[1], f[2]));
            }
        }
        return out;
    }

    @Override
    public void save(List<SavedDevice> devices) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (SavedDevice d : devices) {
            if (n++ >= MAX_DEVICES) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(clean(d.serial)).append('\t').append(clean(d.host)).append('\t').append(clean(d.name));
        }
        prefs.edit().putString(KEY_LIST, sb.toString()).apply();
    }

    @Override
    public String lastSerial() {
        return prefs.getString(KEY_LAST, null);
    }

    @Override
    public void setLastSerial(String serial) {
        if (serial == null) prefs.edit().remove(KEY_LAST).apply();
        else prefs.edit().putString(KEY_LAST, serial).apply();
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
