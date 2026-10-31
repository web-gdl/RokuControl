package app.rokuremote.core;

/** Fila de la lista de dispositivos: una IP con lo que se sabe de ella. */
public final class DeviceEntry {
    public final String host;
    public final String serial;
    public final String name;
    /** Se recuerda en este teléfono. */
    public final boolean saved;
    /** Respondió en esta sesión. */
    public final boolean online;
    /** Es el dispositivo que se está controlando. */
    public final boolean current;

    public DeviceEntry(String host, String serial, String name, boolean saved, boolean online, boolean current) {
        this.host = host;
        this.serial = serial;
        this.name = name;
        this.saved = saved;
        this.online = online;
        this.current = current;
    }
}
