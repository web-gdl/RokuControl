package app.rokuremote.ui;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import app.rokuremote.core.DeviceEntry;
import app.rokuremote.core.RemoteController;
import app.rokuremote.core.Texts;
import app.rokuremote.data.PrefsDeviceRepository;
import app.rokuremote.data.WifiNetBinder;
import app.rokuremote.ecp.DeviceInfo;
import app.rokuremote.ecp.Key;
import app.rokuremote.ecp.RokuApp;

import java.util.ArrayList;
import java.util.List;

/** Pantalla única: control remoto virtual. Solo construye la interfaz; la lógica vive en {@link RemoteController}. */
public final class MainActivity extends Activity implements RemoteController.Listener {
    private RemoteController controller;

    private View statusDot;
    private TextView nameView;
    private TextView subView;
    private RemoteButton powerButton;
    private RemoteButton volUp;
    private RemoteButton volDown;
    private RemoteButton mute;

    private List<DeviceEntry> entries = new ArrayList<>();
    private DevicesSheet devicesSheet;
    private AppsSheet appsSheet;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(Theme.BG);
        setContentView(buildUi());
        controller = new RemoteController(new PrefsDeviceRepository(this), new WifiNetBinder(this),
                new Handler(Looper.getMainLooper())::post, this);
        controller.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        controller.resume();
    }

    @Override
    protected void onPause() {
        controller.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        controller.shutdown();
        super.onDestroy();
    }

    // ---- construcción de la interfaz ----------------------------------------------------

    private View buildUi() {
        final int base = Theme.dp(this, 16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                l = i.left; t = i.top; r = i.right; b = i.bottom;
            } else {
                l = legacy(ins, 0); t = legacy(ins, 1); r = legacy(ins, 2); b = legacy(ins, 3);
            }
            v.setPadding(base + l, base / 2 + t, base + r, base / 2 + b);
            return ins;
        });

        root.addView(buildHeader(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 64)));

        DPadView dpad = new DPadView(this, (key, repeat) -> controller.sendKey(key, repeat));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        dp.topMargin = Theme.dp(this, 6);
        dp.bottomMargin = Theme.dp(this, 6);
        root.addView(dpad, dp);

        root.addView(row(
                key(Icons.Kind.BACK, "Atrás", Key.BACK, false),
                key(Icons.Kind.HOME, "Inicio", Key.HOME, false),
                key(Icons.Kind.REPLAY, "Repetir escena", Key.REPLAY, false),
                key(Icons.Kind.OPTIONS, "Opciones", Key.OPTIONS, false)));
        root.addView(row(
                key(Icons.Kind.REV, "Retroceder", Key.REV, false),
                key(Icons.Kind.PLAY, "Reproducir o pausar", Key.PLAY, false),
                key(Icons.Kind.FWD, "Avanzar", Key.FWD, false)));

        volDown = key(Icons.Kind.VOL_DOWN, "Bajar volumen", Key.VOLUME_DOWN, true);
        mute = key(Icons.Kind.MUTE, "Silenciar", Key.VOLUME_MUTE, false);
        volUp = key(Icons.Kind.VOL_UP, "Subir volumen", Key.VOLUME_UP, true);
        root.addView(row(volDown, mute, volUp));

        root.addView(row(
                new RemoteButton(this, Icons.Kind.KEYBOARD, "Teclado", "Abrir teclado", false, rep -> showKeyboard()),
                new RemoteButton(this, Icons.Kind.APPS, "Apps", "Abrir aplicaciones", false, rep -> showApps())));
        return root;
    }

    @SuppressWarnings("deprecation")
    private static int legacy(WindowInsets ins, int side) {
        switch (side) {
            case 0: return ins.getSystemWindowInsetLeft();
            case 1: return ins.getSystemWindowInsetTop();
            case 2: return ins.getSystemWindowInsetRight();
            default: return ins.getSystemWindowInsetBottom();
        }
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setBackground(Theme.pressable(Theme.BG, Theme.SURFACE, Theme.dp(this, 16)));
        info.setPadding(Theme.dp(this, 10), 0, Theme.dp(this, 6), 0);
        info.setContentDescription("Elegir dispositivo Roku");
        info.setOnClickListener(v -> showDevices());

        statusDot = new View(this);
        info.addView(statusDot, new LinearLayout.LayoutParams(Theme.dp(this, 10), Theme.dp(this, 10)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Theme.dp(this, 12), 0, 0, 0);
        nameView = Sheet.text(this, Texts.STATE_NONE, 18, Theme.TEXT, true);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subView = Sheet.text(this, Texts.TAP_TO_CHOOSE, 13, Theme.TEXT_DIM, false);
        subView.setSingleLine(true);
        subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(nameView);
        texts.addView(subView);
        info.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(new IconView(this, Icons.Kind.CHEVRON, Theme.TEXT_DIM), new LinearLayout.LayoutParams(Theme.dp(this, 32), Theme.dp(this, 32)));

        header.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        powerButton = new RemoteButton(this, Icons.Kind.POWER, null, "Encender o apagar", false, rep -> controller.togglePower());
        powerButton.setIconColor(Theme.DANGER);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(Theme.dp(this, 52), Theme.dp(this, 52));
        pp.leftMargin = Theme.dp(this, 10);
        header.addView(powerButton, pp);

        setDot(Theme.OFF);
        return header;
    }

    private RemoteButton key(Icons.Kind kind, String description, String key, boolean repeatable) {
        return new RemoteButton(this, kind, null, description, repeatable, rep -> controller.sendKey(key, rep));
    }

    private View row(RemoteButton... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (RemoteButton b : buttons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.leftMargin = lp.rightMargin = Theme.dp(this, 5);
            row.addView(b, lp);
        }
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 60));
        rp.topMargin = Theme.dp(this, 8);
        row.setLayoutParams(rp);
        return row;
    }

    private void setDot(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        statusDot.setBackground(d);
    }

    // ---- hojas --------------------------------------------------------------------------

    private void showDevices() {
        DevicesSheet s = new DevicesSheet(this, controller);
        devicesSheet = s;
        s.dialog.setOnDismissListener(d -> {
            if (devicesSheet == s) devicesSheet = null;
        });
        s.update(entries);
        s.show();
        controller.searchNow(false);
    }

    private void showApps() {
        AppsSheet s = new AppsSheet(this, controller);
        appsSheet = s;
        s.dialog.setOnDismissListener(d -> {
            if (appsSheet == s) appsSheet = null;
        });
        s.show();
        controller.loadApps();
    }

    private void showKeyboard() {
        new KeyboardSheet(this, controller).show();
    }

    // ---- RemoteController.Listener (siempre en el hilo principal) -----------------------

    @Override
    public void onConnection(RemoteController.State state, DeviceInfo info, String host) {
        String name = info != null ? info.name : "Roku";
        switch (state) {
            case CONNECTED:
                setDot(Theme.OK);
                nameView.setText(name);
                String model = info.model.isEmpty() ? "" : info.model + " · ";
                subView.setText(model + host + (info.isPoweredOn() ? "" : " · en espera"));
                break;
            case CONNECTING:
                setDot(Theme.WARN);
                nameView.setText(name);
                subView.setText(Texts.STATE_CONNECTING + " · " + host);
                break;
            case RECONNECTING:
                setDot(Theme.WARN);
                nameView.setText(name);
                subView.setText(Texts.STATE_RECONNECTING + " · " + host);
                break;
            default:
                setDot(Theme.OFF);
                nameView.setText(Texts.STATE_NONE);
                subView.setText(Texts.TAP_TO_CHOOSE);
        }
        // Funciones que dependen del modelo: se indican como no disponibles, no se ocultan.
        boolean known = info != null;
        powerButton.setLocked(known && !info.supportsPower ? Texts.POWER_UNSUPPORTED : null);
        powerButton.setIconColor(known && !info.isPoweredOn() ? Theme.OK : Theme.DANGER);
        String volReason = known && !info.supportsVolume ? Texts.VOLUME_UNSUPPORTED : null;
        volUp.setLocked(volReason);
        volDown.setLocked(volReason);
        mute.setLocked(volReason);
    }

    @Override
    public void onDevices(List<DeviceEntry> list) {
        entries = list;
        if (devicesSheet != null && devicesSheet.isShowing()) devicesSheet.update(list);
    }

    @Override
    public void onApps(List<RokuApp> apps) {
        if (appsSheet != null && appsSheet.isShowing()) appsSheet.setApps(apps);
    }

    @Override
    public void onNotice(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
