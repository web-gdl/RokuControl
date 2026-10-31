package app.rokuremote.core;

/** Dispositivo recordado localmente: solo número de serie, última IP y nombre. */
public final class SavedDevice {
    public final String serial;
    public final String host;
    public final String name;

    public SavedDevice(String serial, String host, String name) {
        this.serial = serial;
        this.host = host;
        this.name = name;
    }
}
