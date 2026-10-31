package app.rokuremote.core;

import java.util.List;

/** Almacenamiento local de dispositivos recordados (sin nube, sin cuenta). */
public interface DeviceRepository {
    List<SavedDevice> load();

    void save(List<SavedDevice> devices);

    /** Número de serie del último dispositivo usado, o null. */
    String lastSerial();

    void setLastSerial(String serial);
}
