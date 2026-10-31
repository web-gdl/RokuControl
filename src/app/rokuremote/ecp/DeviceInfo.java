package app.rokuremote.ecp;

import java.util.List;

/** Datos de {@code GET /query/device-info} que la app necesita. Inmutable. */
public final class DeviceInfo {
    public final String serial;
    public final String name;
    public final String model;
    public final String softwareVersion;
    public final boolean isTv;
    /** Valor de power-mode ("PowerOn", "Ready", "DisplayOff"...) o "" si no lo informa. */
    public final String powerMode;
    /** El dispositivo controla volumen por ECP (Roku TV). */
    public final boolean supportsVolume;
    /** El dispositivo controla encendido/apagado por ECP (Roku TV). */
    public final boolean supportsPower;

    DeviceInfo(String serial, String name, String model, String softwareVersion,
               boolean isTv, String powerMode, boolean supportsVolume, boolean supportsPower) {
        this.serial = serial;
        this.name = name;
        this.model = model;
        this.softwareVersion = softwareVersion;
        this.isTv = isTv;
        this.powerMode = powerMode;
        this.supportsVolume = supportsVolume;
        this.supportsPower = supportsPower;
    }

    public boolean isPoweredOn() {
        return powerMode.isEmpty() || "PowerOn".equalsIgnoreCase(powerMode);
    }

    /**
     * Interpreta la respuesta XML. Las banderas de volumen y energía usan los campos
     * {@code supports-audio-volume-control} / {@code supports-tv-power-control} (Roku OS 15+)
     * y, si faltan, {@code is-tv}.
     */
    public static DeviceInfo parse(String xml) {
        List<SimpleXml.Element> l = SimpleXml.leaves(xml);
        String model = orEmpty(SimpleXml.first(l, "model-name"));
        String serial = orEmpty(SimpleXml.first(l, "serial-number"));
        if (serial.isEmpty()) serial = orEmpty(SimpleXml.first(l, "device-id"));
        if (serial.isEmpty() && model.isEmpty()) return null; // no parece device-info de Roku
        String name = orEmpty(SimpleXml.first(l, "user-device-name"));
        if (name.isEmpty()) name = orEmpty(SimpleXml.first(l, "friendly-device-name"));
        if (name.isEmpty()) name = model.isEmpty() ? "Roku" : model;
        boolean tv = bool(SimpleXml.first(l, "is-tv"), false);
        boolean vol = bool(SimpleXml.first(l, "supports-audio-volume-control"), tv);
        boolean pow = bool(SimpleXml.first(l, "supports-tv-power-control"), tv);
        return new DeviceInfo(serial, clip(name), clip(model),
                clip(orEmpty(SimpleXml.first(l, "software-version"))), tv,
                orEmpty(SimpleXml.first(l, "power-mode")), vol, pow);
    }

    private static boolean bool(String v, boolean def) {
        if (v == null || v.isEmpty()) return def;
        return "true".equalsIgnoreCase(v);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String clip(String s) {
        return s.length() > 60 ? s.substring(0, 60) : s;
    }
}
