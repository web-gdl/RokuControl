package app.rokuremote.core;

import app.rokuremote.ecp.DeviceInfo;
import app.rokuremote.ecp.EcpClient;
import app.rokuremote.ecp.Hosts;
import app.rokuremote.ecp.Key;
import app.rokuremote.ecp.NetBinder;
import app.rokuremote.ecp.RokuApp;
import app.rokuremote.ecp.SsdpDiscovery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lógica de negocio: dispositivo actual, reconexión, búsqueda y envío de teclas.
 * No conoce la interfaz: avisa por {@link Listener}, siempre en el ejecutor {@code ui}.
 * Mientras la app no está visible ({@link #pause()}) no hay tráfico de red.
 */
public final class RemoteController {
    public enum State { NONE, CONNECTING, CONNECTED, RECONNECTING }

    public interface Listener {
        void onConnection(State state, DeviceInfo info, String host);
        void onDevices(List<DeviceEntry> entries);
        void onApps(List<RokuApp> apps);
        void onNotice(String message);
    }

    private static final long DEFAULT_POLL_MS = 3000;
    private static final int SEARCH_MS = 3500;
    private static final long NOTICE_GAP_MS = 2500;

    private final DeviceRepository repo;
    private final NetBinder net;
    private final Executor ui;
    private final Listener listener;
    private final long pollMs;

    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(daemon("roku-poll"));
    private final ExecutorService sender = Executors.newSingleThreadExecutor(daemon("roku-send"));
    private final ExecutorService searcher = Executors.newSingleThreadExecutor(daemon("roku-search"));
    private final AtomicBoolean searching = new AtomicBoolean();
    private final AtomicInteger pendingKeys = new AtomicInteger();

    private final Object lock = new Object();
    private final List<SavedDevice> saved = new ArrayList<>();
    private final Map<String, DeviceInfo> online = new LinkedHashMap<>(); // host -> info
    private String host;
    private DeviceInfo info;
    private State state = State.NONE;
    private int failures;
    private ScheduledFuture<?> pollTask;
    private long lastNoticeAt;

    public RemoteController(DeviceRepository repo, NetBinder net, Executor ui, Listener listener) {
        this(repo, net, ui, listener, DEFAULT_POLL_MS);
    }

    /** {@code pollMs}: cada cuánto se comprueba que el Roku sigue respondiendo (mientras la app es visible). */
    public RemoteController(DeviceRepository repo, NetBinder net, Executor ui, Listener listener, long pollMs) {
        this.pollMs = pollMs;
        this.repo = repo;
        this.net = net;
        this.ui = ui;
        this.listener = listener;
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    // ---- ciclo de vida ------------------------------------------------------------------

    /** Carga los dispositivos recordados y reconecta al último usado. */
    public void start() {
        String lastSerial = repo.lastSerial();
        synchronized (lock) {
            saved.clear();
            saved.addAll(repo.load());
            SavedDevice pick = null;
            for (int i = saved.size() - 1; i >= 0; i--) {
                if (!Hosts.isAllowedTarget(saved.get(i).host)) saved.remove(i); // dato alterado: se descarta
            }
            for (SavedDevice d : saved) {
                if (d.serial.equals(lastSerial)) pick = d;
            }
            if (pick == null && !saved.isEmpty()) pick = saved.get(0);
            if (pick != null) {
                host = pick.host;
                state = State.CONNECTING;
            }
        }
        publishConnection();
        publishDevices();
    }

    /** La app es visible: vigilar la conexión y buscar dispositivos. */
    public void resume() {
        synchronized (lock) {
            if (pollTask == null || pollTask.isCancelled()) {
                pollTask = poller.scheduleWithFixedDelay(this::pollOnce, 0, pollMs, TimeUnit.MILLISECONDS);
            }
        }
        searchNow(false);
    }

    /** La app salió de primer plano: detener todo el tráfico periódico. */
    public void pause() {
        synchronized (lock) {
            if (pollTask != null) pollTask.cancel(false);
            pollTask = null;
        }
    }

    public void shutdown() {
        pause();
        poller.shutdownNow();
        sender.shutdownNow();
        searcher.shutdownNow();
    }

    // ---- selección y dispositivos -------------------------------------------------------

    /** Controla el dispositivo en esa IP (ya validada o proveniente de la búsqueda). */
    public void select(String newHost) {
        if (!Hosts.isAllowedTarget(newHost)) {
            notice(Texts.BAD_IP);
            return;
        }
        synchronized (lock) {
            if (newHost.equals(host) && state == State.CONNECTED) return;
            host = newHost;
            info = null;
            failures = 0;
            state = State.CONNECTING;
        }
        publishConnection();
        publishDevices();
        poller.execute(this::pollOnce);
    }

    /** Conexión manual por IP. Rechaza direcciones que no sean de red local. */
    public void addManual(String input) {
        String ip = Hosts.normalizeLocal(input);
        if (ip == null) {
            notice(Texts.BAD_IP);
            return;
        }
        select(ip);
    }

    public void forget(String serial) {
        boolean wasCurrent = false;
        synchronized (lock) {
            for (int i = saved.size() - 1; i >= 0; i--) {
                if (saved.get(i).serial.equals(serial)) saved.remove(i);
            }
            if (info != null && info.serial.equals(serial)) {
                host = null;
                info = null;
                state = State.NONE;
                wasCurrent = true;
            }
            repo.save(new ArrayList<>(saved));
            if (wasCurrent) repo.setLastSerial(null);
        }
        publishConnection();
        publishDevices();
    }

    /** Busca Roku en la red. {@code userInitiated} muestra avisos si no se encuentra nada. */
    public void searchNow(boolean userInitiated) {
        if (!searching.compareAndSet(false, true)) return;
        searcher.execute(() -> {
            try {
                Set<String> ips = SsdpDiscovery.discover(SEARCH_MS, net);
                for (String ip : ips) {
                    try {
                        DeviceInfo di = new EcpClient(ip, net).deviceInfo();
                        onFound(ip, di);
                    } catch (IOException ignored) {
                        // respondió SSDP pero no ECP: se ignora
                    }
                }
                if (ips.isEmpty() && userInitiated) notice(Texts.NOTHING_FOUND);
            } catch (IOException e) {
                if (userInitiated) notice(Texts.NO_NETWORK);
            } finally {
                searching.set(false);
            }
        });
    }

    private void onFound(String ip, DeviceInfo di) {
        boolean connect = false;
        synchronized (lock) {
            online.put(ip, di);
            // Calcular antes de actualizar la IP recordada, que es lo que se compara.
            boolean sameDeviceNewIp = info == null && host != null && !ip.equals(host) && savedSerialForHost(host, di.serial);
            boolean changed = rememberLocked(ip, di);
            boolean currentMoved = info != null && info.serial.equals(di.serial) && !ip.equals(host);
            if (host == null && saved.size() <= 1) connect = true;       // primer uso: un único Roku
            if (sameDeviceNewIp || currentMoved) connect = true;       // la IP cambió (DHCP)
            if (changed) repo.save(new ArrayList<>(saved));
        }
        publishDevices();
        if (connect) select(ip);
    }

    private boolean savedSerialForHost(String h, String serial) {
        for (SavedDevice d : saved) if (d.host.equals(h) && d.serial.equals(serial)) return true;
        return false;
    }

    /** Inserta o actualiza el dispositivo recordado. Debe llamarse con {@code lock}. */
    private boolean rememberLocked(String ip, DeviceInfo di) {
        if (di.serial.isEmpty()) return false;
        for (int i = 0; i < saved.size(); i++) {
            SavedDevice d = saved.get(i);
            if (d.serial.equals(di.serial)) {
                if (d.host.equals(ip) && d.name.equals(di.name)) return false;
                saved.set(i, new SavedDevice(di.serial, ip, di.name));
                return true;
            }
        }
        saved.add(new SavedDevice(di.serial, ip, di.name));
        return true;
    }

    // ---- vigilancia de la conexión ------------------------------------------------------

    private void pollOnce() {
        final String h;
        synchronized (lock) {
            h = host;
        }
        if (h == null) return;
        DeviceInfo di = null;
        IOException err = null;
        try {
            di = new EcpClient(h, net).deviceInfo();
        } catch (IOException e) {
            err = e;
        }
        boolean runSearch = false;
        boolean connChanged = false;
        boolean devicesChanged = false;
        synchronized (lock) {
            if (!h.equals(host)) return; // se cambió de dispositivo mientras tanto
            if (di != null) {
                failures = 0;
                connChanged = state != State.CONNECTED || !sameStatus(info, di);
                state = State.CONNECTED;
                info = di;
                online.put(h, di);
                if (rememberLocked(h, di)) repo.save(new ArrayList<>(saved));
                repo.setLastSerial(di.serial.isEmpty() ? null : di.serial);
                devicesChanged = true;
            } else {
                failures++;
                if (failures >= 2 && state != State.RECONNECTING) {
                    state = State.RECONNECTING;
                    online.remove(h);
                    connChanged = true;
                    devicesChanged = true;
                }
                runSearch = failures % 3 == 0; // quizá cambió la IP: buscar de nuevo
            }
        }
        if (connChanged) publishConnection();
        if (devicesChanged) publishDevices();
        if (runSearch) searchNow(false);
    }

    private static boolean sameStatus(DeviceInfo a, DeviceInfo b) {
        return a != null && a.powerMode.equals(b.powerMode) && a.name.equals(b.name)
                && a.supportsVolume == b.supportsVolume && a.supportsPower == b.supportsPower;
    }

    // ---- envío de órdenes ---------------------------------------------------------------

    /** @param repeat true si es una repetición por mantener pulsado: se descarta si hay una orden en cola. */
    public void sendKey(String key, boolean repeat) {
        final String h = currentHost();
        if (h == null) {
            notice(Texts.NO_DEVICE);
            return;
        }
        if (repeat && pendingKeys.get() > 0) return;
        pendingKeys.incrementAndGet();
        sender.execute(() -> {
            try {
                new EcpClient(h, net).key(key);
            } catch (EcpClient.EcpException e) {
                onSendError(e);
            } finally {
                pendingKeys.decrementAndGet();
            }
        });
    }

    /** Envía un carácter al teclado en pantalla del Roku. */
    public void sendLiteral(int codePoint) {
        final String h = currentHost();
        if (h == null) {
            notice(Texts.NO_DEVICE);
            return;
        }
        sender.execute(() -> {
            try {
                new EcpClient(h, net).literal(codePoint);
            } catch (EcpClient.EcpException e) {
                onSendError(e);
            }
        });
    }

    /** Alterna encendido/apagado en Roku TV; en otros modelos avisa que no es posible. */
    public void togglePower() {
        DeviceInfo di;
        synchronized (lock) {
            di = info;
        }
        if (di == null) {
            notice(Texts.NO_DEVICE);
        } else if (!di.supportsPower) {
            notice(Texts.POWER_UNSUPPORTED);
        } else {
            sendKey(di.isPoweredOn() ? Key.POWER_OFF : Key.POWER_ON, false);
        }
    }

    public void launch(RokuApp app) {
        final String h = currentHost();
        if (h == null) {
            notice(Texts.NO_DEVICE);
            return;
        }
        sender.execute(() -> {
            try {
                new EcpClient(h, net).launch(app.id);
            } catch (EcpClient.EcpException e) {
                onSendError(e);
            }
        });
    }

    /** Pide la lista de aplicaciones instaladas en el Roku actual. */
    public void loadApps() {
        final String h = currentHost();
        if (h == null) {
            notice(Texts.NO_DEVICE);
            return;
        }
        sender.execute(() -> {
            try {
                List<RokuApp> apps = new EcpClient(h, net).apps();
                ui.execute(() -> listener.onApps(apps));
            } catch (EcpClient.EcpException e) {
                onSendError(e);
            }
        });
    }

    private void onSendError(EcpClient.EcpException e) {
        notice(e.isAccessDenied() ? Texts.ACCESS_DENIED : Texts.SEND_FAILED);
        if (!e.isAccessDenied()) poller.execute(this::pollOnce);
    }

    // ---- publicación hacia la interfaz --------------------------------------------------

    private String currentHost() {
        synchronized (lock) {
            return host;
        }
    }

    private void notice(String message) {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (now - lastNoticeAt < NOTICE_GAP_MS) return; // evita avisos repetidos al mantener pulsado
            lastNoticeAt = now;
        }
        ui.execute(() -> listener.onNotice(message));
    }

    private void publishConnection() {
        final State s;
        final DeviceInfo di;
        final String h;
        synchronized (lock) {
            s = state;
            di = info;
            h = host;
        }
        ui.execute(() -> listener.onConnection(s, di, h));
    }

    private void publishDevices() {
        final List<DeviceEntry> list = new ArrayList<>();
        synchronized (lock) {
            for (SavedDevice d : saved) {
                DeviceInfo di = online.get(d.host);
                list.add(new DeviceEntry(d.host, d.serial, di != null ? di.name : d.name, true,
                        di != null, d.host.equals(host)));
            }
            for (Map.Entry<String, DeviceInfo> e : online.entrySet()) {
                boolean known = false;
                for (SavedDevice d : saved) if (d.host.equals(e.getKey())) known = true;
                if (!known) {
                    list.add(new DeviceEntry(e.getKey(), e.getValue().serial, e.getValue().name, false,
                            true, e.getKey().equals(host)));
                }
            }
        }
        ui.execute(() -> listener.onDevices(list));
    }
}
