package app.rokuremote;

import app.rokuremote.core.DeviceEntry;
import app.rokuremote.core.DeviceRepository;
import app.rokuremote.core.RemoteController;
import app.rokuremote.core.SavedDevice;
import app.rokuremote.core.Texts;
import app.rokuremote.ecp.DeviceInfo;
import app.rokuremote.ecp.EcpClient;
import app.rokuremote.ecp.Hosts;
import app.rokuremote.ecp.Key;
import app.rokuremote.ecp.NetBinder;
import app.rokuremote.ecp.RokuApp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/** Pruebas funcionales de la lógica y del protocolo contra un Roku simulado. Se ejecutan en la JVM, sin dispositivo. */
public final class AllTests {
    private static int passed, failed;

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  ok   " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }

    private static boolean eventually(long ms, BooleanSupplier c) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (c.getAsBoolean()) return true;
            try { Thread.sleep(20); } catch (InterruptedException e) { return false; }
        }
        return c.getAsBoolean();
    }

    public static void main(String[] args) throws Exception {
        hosts();
        parsing();
        ecpClient();
        ssdp();
        controller();
        System.out.println("\n" + passed + " correctas, " + failed + " con fallo");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---- validación de direcciones -----------------------------------------------------
    private static void hosts() {
        System.out.println("Direcciones");
        check("192.168.1.20 es local", Hosts.isLocalIpv4("192.168.1.20"));
        check("10.0.0.5 es local", Hosts.isLocalIpv4("10.0.0.5"));
        check("172.16.0.1 y 172.31.255.1 son locales", Hosts.isLocalIpv4("172.16.0.1") && Hosts.isLocalIpv4("172.31.255.1"));
        check("172.32.0.1 NO es local", !Hosts.isLocalIpv4("172.32.0.1"));
        check("169.254.1.1 (enlace local) es local", Hosts.isLocalIpv4("169.254.1.1"));
        check("8.8.8.8 se rechaza", !Hosts.isLocalIpv4("8.8.8.8"));
        check("127.0.0.1 se rechaza", Hosts.normalizeLocal("127.0.0.1") == null);
        check("octeto 300 se rechaza", Hosts.normalizeLocal("192.168.1.300") == null);
        check("texto/hostname se rechaza", Hosts.normalizeLocal("roku.local") == null && Hosts.normalizeLocal("evil.com") == null);
        check("con puerto o ruta se rechaza", Hosts.normalizeLocal("192.168.1.2:8060") == null && Hosts.normalizeLocal("192.168.1.2/x") == null);
        check("destino permitido: local o el propio teléfono, nada más", Hosts.isAllowedTarget("192.168.0.9") && Hosts.isAllowedTarget("127.0.0.1")
                && !Hosts.isAllowedTarget("93.184.216.34") && !Hosts.isAllowedTarget("example.com") && !Hosts.isAllowedTarget(null));
        check("vacío/null se rechaza", Hosts.normalizeLocal("") == null && Hosts.normalizeLocal(null) == null);
        check("normaliza espacios y ceros", "192.168.1.5".equals(Hosts.normalizeLocal("  192.168.001.05 ")));
    }

    // ---- lectura de respuestas -----------------------------------------------------------
    private static void parsing() {
        System.out.println("Lectura de respuestas");
        FakeRoku f = new FakeRoku();
        String tv = "<device-info><serial-number>S1</serial-number><model-name>TCL</model-name>"
                + "<user-device-name>Sala &amp; TV</user-device-name><power-mode>Ready</power-mode><is-tv>true</is-tv></device-info>";
        DeviceInfo d = DeviceInfo.parse(tv);
        check("TV: nombre con entidades", d != null && d.name.equals("Sala & TV"));
        check("TV: volumen y energía soportados (is-tv)", d != null && d.supportsVolume && d.supportsPower);
        check("TV en espera no figura encendida", d != null && !d.isPoweredOn());
        DeviceInfo stick = DeviceInfo.parse("<device-info><serial-number>S2</serial-number><model-name>Express</model-name></device-info>");
        check("Reproductor: sin volumen ni energía", stick != null && !stick.supportsVolume && !stick.supportsPower);
        check("Reproductor: nombre cae al modelo", stick != null && stick.name.equals("Express"));
        DeviceInfo mod = DeviceInfo.parse("<device-info><serial-number>S3</serial-number><model-name>X</model-name>"
                + "<supports-tv-power-control>false</supports-tv-power-control><supports-audio-volume-control>true</supports-audio-volume-control></device-info>");
        check("Campos de Roku OS 15 tienen prioridad", mod != null && mod.supportsVolume && !mod.supportsPower);
        check("Respuesta que no es de Roku se rechaza", DeviceInfo.parse("<html>hola</html>") == null && DeviceInfo.parse("") == null);
        check("Entidad externa no se expande", DeviceInfo.parse("<!DOCTYPE x [<!ENTITY a SYSTEM \"file:///etc/passwd\">]><device-info>"
                + "<serial-number>&a;</serial-number><model-name>M</model-name></device-info>").serial.equals("&a;"));

        List<RokuApp> apps = RokuApp.parseList("<apps><app id=\"837\" type=\"appl\">YouTube</app><app id=\"12\">Netflix</app>"
                + "<app id=\"x y\">Malo</app><app id=\"5\"></app></apps>");
        check("Apps: ordenadas y filtradas", apps.size() == 2 && apps.get(0).name.equals("Netflix") && apps.get(1).id.equals("837"));
        f.stop();
    }

    // ---- cliente ECP ---------------------------------------------------------------------
    private static void ecpClient() throws Exception {
        System.out.println("Cliente ECP");
        FakeRoku roku = new FakeRoku();
        roku.start(0);
        EcpClient c = new EcpClient("127.0.0.1", roku.port(), NetBinder.DIRECT);

        DeviceInfo info = c.deviceInfo();
        check("device-info por GET", roku.requests.contains("GET /query/device-info") && info.name.equals("Sala"));
        check("apps: 3 válidas (la de id inválido se descarta)", c.apps().size() == 3);

        c.key(Key.HOME);
        c.key(Key.VOLUME_UP);
        check("keypress usa POST", roku.requests.contains("POST /keypress/Home") && roku.requests.contains("POST /keypress/VolumeUp"));

        c.literal('a');
        c.literal(' ');
        c.literal('&');
        c.literal(0x20AC); // €
        c.literal(0x1F600); // emoji (par sustituto)
        check("literal ASCII", roku.requests.contains("POST /keypress/Lit_a"));
        check("literal espacio = %20 (no '+')", roku.requests.contains("POST /keypress/Lit_%20"));
        check("literal & codificado", roku.requests.contains("POST /keypress/Lit_%26"));
        check("literal € en UTF-8", roku.requests.contains("POST /keypress/Lit_%E2%82%AC"));
        check("literal emoji en UTF-8", roku.requests.contains("POST /keypress/Lit_%F0%9F%98%80"));

        c.launch("12");
        check("launch por POST", roku.requests.contains("POST /launch/12"));

        boolean rejected = false;
        try { c.key("../etc/passwd"); } catch (IllegalArgumentException e) { rejected = true; }
        check("tecla con caracteres inválidos se rechaza antes de enviar", rejected);
        boolean rejected2 = false;
        try { c.launch("1/../2"); } catch (IllegalArgumentException e) { rejected2 = true; }
        check("id de app inválido se rechaza antes de enviar", rejected2);

        roku.keyStatus = 403;
        boolean denied = false;
        try { c.key(Key.HOME); } catch (EcpClient.EcpException e) { denied = e.isAccessDenied(); }
        check("403 se reconoce como acceso denegado", denied);
        roku.keyStatus = 200;

        roku.garbage = true;
        boolean notRoku = false;
        try { c.deviceInfo(); } catch (EcpClient.EcpException e) { notRoku = true; }
        check("respuesta que no es Roku se informa", notRoku);
        roku.garbage = false;

        roku.oversizeBytes = 400 * 1024;
        boolean tooBig = false;
        try { c.deviceInfo(); } catch (EcpClient.EcpException e) { tooBig = true; }
        check("respuesta enorme se corta", tooBig);
        roku.oversizeBytes = 0;

        roku.stop();
        boolean unreachable = false;
        try { c.key(Key.HOME); } catch (EcpClient.EcpException e) { unreachable = e.httpCode == 0 && !e.isAccessDenied(); }
        check("Roku apagado: error de comunicación sin colgarse", unreachable);
    }

    // ---- búsqueda SSDP -------------------------------------------------------------------
    private static void ssdp() throws Exception {
        System.out.println("Búsqueda SSDP");
        check("respuesta válida → IP", callParse("HTTP/1.1 200 OK\r\nCache-Control: max-age=3600\r\nST: roku:ecp\r\n"
                + "USN: uuid:roku:ecp:X0\r\nLocation: http://192.168.1.134:8060/\r\n\r\n").equals("192.168.1.134"));
        check("IP pública en LOCATION se ignora", callParse("HTTP/1.1 200 OK\r\nST: roku:ecp\r\nLocation: http://8.8.8.8:8060/\r\n\r\n") == null);
        check("otro ST se ignora", callParse("HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\nLocation: http://192.168.1.9:8060/\r\n\r\n") == null);
        check("NOTIFY/otro método se ignora", callParse("NOTIFY * HTTP/1.1\r\nST: roku:ecp\r\nLocation: http://192.168.1.9:8060/\r\n\r\n") == null);

        // Respondedor UDP local que imita a un Roku
        DatagramSocket responder = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        final List<String> received = new CopyOnWriteArrayList<>();
        Thread t = new Thread(() -> {
            try {
                byte[] buf = new byte[1500];
                while (!responder.isClosed()) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    responder.receive(p);
                    received.add(new String(p.getData(), 0, p.getLength(), StandardCharsets.US_ASCII));
                    byte[] resp = ("HTTP/1.1 200 OK\r\nST: roku:ecp\r\nUSN: uuid:roku:ecp:X1\r\n"
                            + "Location: http://192.168.1.77:8060/\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
                    responder.send(new DatagramPacket(resp, resp.length, p.getAddress(), p.getPort()));
                }
            } catch (Exception ignored) { }
        });
        t.setDaemon(true);
        t.start();
        Set<String> found = callDiscover("127.0.0.1", responder.getLocalPort());
        responder.close();
        check("M-SEARCH con ST: roku:ecp enviado", !received.isEmpty() && received.get(0).startsWith("M-SEARCH * HTTP/1.1")
                && received.get(0).contains("ST: roku:ecp") && received.get(0).contains("ssdp:discover"));
        check("descubre una sola IP aunque conteste varias veces", found.size() == 1 && found.contains("192.168.1.77"));
    }

    private static String callParse(String s) throws Exception {
        java.lang.reflect.Method m = Class.forName("app.rokuremote.ecp.SsdpDiscovery").getDeclaredMethod("parseResponse", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, s);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> callDiscover(String group, int port) throws Exception {
        java.lang.reflect.Method m = Class.forName("app.rokuremote.ecp.SsdpDiscovery")
                .getDeclaredMethod("discover", String.class, int.class, int.class, NetBinder.class);
        m.setAccessible(true);
        return (Set<String>) m.invoke(null, group, port, 2500, NetBinder.DIRECT);
    }

    // ---- controlador ---------------------------------------------------------------------
    static final class MemRepo implements DeviceRepository {
        volatile List<SavedDevice> devices = new ArrayList<>();
        volatile String last;
        @Override public List<SavedDevice> load() { return new ArrayList<>(devices); }
        @Override public void save(List<SavedDevice> d) { devices = new ArrayList<>(d); }
        @Override public String lastSerial() { return last; }
        @Override public void setLastSerial(String s) { last = s; }
    }

    static final class Events implements RemoteController.Listener {
        volatile RemoteController.State state = RemoteController.State.NONE;
        volatile DeviceInfo info;
        volatile List<DeviceEntry> entries = new ArrayList<>();
        final List<String> notices = new CopyOnWriteArrayList<>();
        @Override public void onConnection(RemoteController.State s, DeviceInfo i, String h) { state = s; info = i; }
        @Override public void onDevices(List<DeviceEntry> e) { entries = e; }
        @Override public void onApps(List<RokuApp> a) { }
        @Override public void onNotice(String m) { notices.add(m); }
    }

    private static void controller() throws Exception {
        System.out.println("Controlador (Roku simulado en 127.0.0.1:8060)");
        FakeRoku roku = new FakeRoku();
        roku.start(8060);
        try {
            // 1. Reconexión automática a un dispositivo recordado, y envío de teclas
            MemRepo repo = new MemRepo();
            repo.devices = new ArrayList<>(List.of(new SavedDevice("X00100000001", "127.0.0.1", "Sala")));
            repo.last = "X00100000001";
            Events ev = new Events();
            RemoteController rc = new RemoteController(repo, NetBinder.DIRECT, Runnable::run, ev, 150);
            rc.start();
            rc.resume();
            check("reconecta solo al último dispositivo", eventually(3000, () -> ev.state == RemoteController.State.CONNECTED));
            check("detecta Roku TV con volumen y energía", ev.info != null && ev.info.isTv && ev.info.supportsVolume && ev.info.supportsPower);
            check("lista marca el dispositivo en uso y en línea", ev.entries.size() == 1 && ev.entries.get(0).current && ev.entries.get(0).online);

            rc.sendKey(Key.HOME, false);
            check("tecla Home llega al Roku", eventually(2000, () -> roku.requests.contains("POST /keypress/Home")));
            rc.sendLiteral('h');
            check("texto llega como Lit_", eventually(2000, () -> roku.requests.contains("POST /keypress/Lit_h")));
            rc.togglePower();
            check("TV encendido: botón de energía envía PowerOff", eventually(2000, () -> roku.requests.contains("POST /keypress/PowerOff")));

            // 2. Acceso denegado en el Roku
            roku.keyStatus = 403;
            Thread.sleep(2600); // el aviso se limita a uno cada 2,5 s
            rc.sendKey(Key.UP, false);
            check("403 muestra cómo habilitar el control", eventually(2000, () -> ev.notices.contains(Texts.ACCESS_DENIED)));
            roku.keyStatus = 200;

            // 3. Caída y recuperación del Roku
            roku.stop();
            check("al caer el Roku pasa a reconectando", eventually(4000, () -> ev.state == RemoteController.State.RECONNECTING));
            check("la lista lo marca fuera de línea", eventually(2000, () -> !ev.entries.isEmpty() && !ev.entries.get(0).online));
            roku.start(8060);
            check("al volver se reconecta solo", eventually(5000, () -> ev.state == RemoteController.State.CONNECTED));

            // 4. Olvidar dispositivo
            rc.forget("X00100000001");
            check("olvidar borra el dispositivo y desconecta", repo.devices.isEmpty() && repo.last == null
                    && eventually(1000, () -> ev.state == RemoteController.State.NONE));
            rc.shutdown();

            // 5. Primer uso por IP: solo se recuerda si respondió como Roku; persiste sin datos extra
            MemRepo repo2 = new MemRepo();
            Events ev2 = new Events();
            RemoteController rc2 = new RemoteController(repo2, NetBinder.DIRECT, Runnable::run, ev2, 150);
            rc2.start();
            rc2.resume();
            rc2.addManual("8.8.8.8");
            check("IP pública se rechaza con aviso y no selecciona nada", ev2.notices.contains(Texts.BAD_IP) && ev2.state == RemoteController.State.NONE);
            rc2.select("93.184.216.34");
            check("select() también rechaza destinos fuera de la red local", ev2.state == RemoteController.State.NONE);
            rc2.select("127.0.0.1");
            check("conexión por IP guarda el dispositivo", eventually(3000, () -> repo2.devices.size() == 1));
            SavedDevice sd = repo2.devices.get(0);
            check("solo se guardan serie, IP y nombre", sd.serial.equals("X00100000001") && sd.host.equals("127.0.0.1") && sd.name.equals("Sala"));
            rc2.shutdown();

            // 6. Reproductor Roku sin funciones de TV (volumen / energía)
            roku.isTv = false;
            Events ev3 = new Events();
            RemoteController rc3 = new RemoteController(new MemRepo(), NetBinder.DIRECT, Runnable::run, ev3, 150);
            rc3.start();
            rc3.select("127.0.0.1");
            check("reproductor: sin volumen ni energía", eventually(3000, () -> ev3.info != null && !ev3.info.supportsVolume && !ev3.info.supportsPower));
            int before = roku.requests.size();
            rc3.togglePower();
            check("reproductor: energía avisa que no está soportada y no envía nada",
                    ev3.notices.contains(Texts.POWER_UNSUPPORTED) && !roku.requests.subList(before, roku.requests.size()).contains("POST /keypress/PowerOn"));
            rc3.shutdown();

            // 7. TV en espera: el botón de energía enciende
            roku.isTv = true;
            roku.powerMode = "Ready";
            Events ev4 = new Events();
            RemoteController rc4 = new RemoteController(new MemRepo(), NetBinder.DIRECT, Runnable::run, ev4, 150);
            rc4.start();
            rc4.select("127.0.0.1");
            check("TV en espera detectado", eventually(3000, () -> ev4.info != null && !ev4.info.isPoweredOn()));
            rc4.togglePower();
            check("TV en espera: energía envía PowerOn", eventually(2000, () -> roku.requests.contains("POST /keypress/PowerOn")));
            rc4.shutdown();

            // 8. Sin dispositivo elegido
            Events ev5 = new Events();
            RemoteController rc5 = new RemoteController(new MemRepo(), NetBinder.DIRECT, Runnable::run, ev5, 150);
            rc5.start();
            rc5.sendKey(Key.HOME, false);
            check("sin dispositivo avisa y no falla", ev5.notices.contains(Texts.NO_DEVICE));
            rc5.shutdown();

            // 9. Privacidad: toda petición fue al Roku simulado y solo a rutas ECP
            boolean onlyEcp = true;
            for (String r : roku.requests) {
                if (!(r.startsWith("GET /query/") || r.startsWith("POST /keypress/") || r.startsWith("POST /launch/"))) onlyEcp = false;
            }
            check("solo se usan rutas ECP (query, keypress, launch)", onlyEcp);
        } finally {
            roku.stop();
        }
    }
}
