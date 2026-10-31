package app.rokuremote.core;

/** Textos de la interfaz (español). Centralizados para facilitar su traducción. */
public final class Texts {
    private Texts() {}

    public static final String APP_NAME = "Roku Control";

    public static final String STATE_NONE = "Sin dispositivo";
    public static final String STATE_CONNECTING = "Conectando…";
    public static final String STATE_CONNECTED = "Conectado";
    public static final String STATE_RECONNECTING = "Sin respuesta, reconectando…";
    public static final String TAP_TO_CHOOSE = "Toca para elegir un Roku";

    public static final String NO_DEVICE = "Primero elige un Roku";
    public static final String SEND_FAILED = "No se pudo enviar. Revisa que el Roku esté encendido y en la misma red";
    public static final String ACCESS_DENIED = "El Roku rechazó el control. En el Roku: Configuración > Sistema > "
            + "Configuración avanzada del sistema > Control por aplicaciones móviles > Habilitado";
    public static final String NOT_A_ROKU = "Esa dirección no respondió como un Roku";
    public static final String BAD_IP = "Escribe una IP de red local, por ejemplo 192.168.1.20";
    public static final String NO_NETWORK = "Sin red Wi-Fi. Conéctate a la misma red que el Roku";
    public static final String NOTHING_FOUND = "No se encontró ningún Roku. Prueba con la IP manual";

    public static final String ONLY_ON_TV = "Solo disponible en Roku TV";
    public static final String POWER_UNSUPPORTED = "Este Roku no se enciende ni apaga por red (solo Roku TV)";
    public static final String VOLUME_UNSUPPORTED = "Este Roku no controla el volumen (solo Roku TV). Usa el control de tu TV o barra de sonido";
}
