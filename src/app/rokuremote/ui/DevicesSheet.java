package app.rokuremote.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.rokuremote.core.DeviceEntry;
import app.rokuremote.core.RemoteController;
import app.rokuremote.core.Texts;
import app.rokuremote.ecp.Hosts;

import java.util.List;

/** Lista de Roku detectados y recordados, búsqueda y conexión por IP. */
final class DevicesSheet extends Sheet {
    private final RemoteController controller;
    private final EditText ipField;

    DevicesSheet(Context ctx, RemoteController controller) {
        super(ctx, "Dispositivos Roku");
        this.controller = controller;

        LinearLayout footer = new LinearLayout(ctx);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(0, Theme.dp(ctx, 14), 0, 0);

        ipField = new EditText(ctx);
        ipField.setHint("IP del Roku, p. ej. 192.168.1.20");
        ipField.setHintTextColor(Theme.TEXT_DIM);
        ipField.setTextColor(Theme.TEXT);
        ipField.setSingleLine(true);
        ipField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        ipField.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        ipField.setBackground(Theme.rect(Theme.SURFACE, Theme.dp(ctx, 14)));
        int p = Theme.dp(ctx, 14);
        ipField.setPadding(p, p, p, p);
        footer.addView(ipField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bp.topMargin = Theme.dp(ctx, 10);
        bp.rightMargin = Theme.dp(ctx, 5);
        buttons.addView(pill(ctx, "Buscar de nuevo", Theme.SURFACE, Theme.TEXT, v -> controller.searchNow(true)), bp);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bp2.topMargin = Theme.dp(ctx, 10);
        bp2.leftMargin = Theme.dp(ctx, 5);
        buttons.addView(pill(ctx, "Conectar por IP", Theme.ACCENT, 0xFF0E1014, v -> connectManual()), bp2);
        footer.addView(buttons);
        addFooter(footer);
    }

    private void connectManual() {
        String ip = Hosts.normalizeLocal(ipField.getText().toString());
        if (ip == null) {
            android.widget.Toast.makeText(ctx, Texts.BAD_IP, android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        controller.addManual(ip);
        dismiss();
    }

    /** Reconstruye la lista con el estado actual. */
    void update(List<DeviceEntry> entries) {
        body.removeAllViews();
        if (entries.isEmpty()) {
            TextView empty = text(ctx, "Buscando Roku en tu red… Si no aparece ninguno, conéctate por IP.", 14, Theme.TEXT_DIM, false);
            empty.setPadding(0, Theme.dp(ctx, 8), 0, Theme.dp(ctx, 8));
            body.addView(empty);
            return;
        }
        for (DeviceEntry e : entries) body.addView(row(e));
    }

    private View row(DeviceEntry e) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int v = Theme.dp(ctx, 12);
        row.setPadding(Theme.dp(ctx, 14), v, Theme.dp(ctx, 6), v);
        row.setBackground(Theme.pressable(e.current ? Theme.SURFACE_PRESSED : Theme.SURFACE, Theme.SURFACE_PRESSED, Theme.dp(ctx, 16)));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = Theme.dp(ctx, 8);
        row.setLayoutParams(rp);
        row.setOnClickListener(x -> {
            controller.select(e.host);
            dismiss();
        });

        View dot = new View(ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(e.online ? Theme.OK : Theme.OFF);
        dot.setBackground(d);
        row.addView(dot, new LinearLayout.LayoutParams(Theme.dp(ctx, 10), Theme.dp(ctx, 10)));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Theme.dp(ctx, 12), 0, 0, 0);
        texts.addView(text(ctx, e.name, 16, Theme.TEXT, true));
        String status = e.current ? "en uso" : e.online ? "disponible" : "sin respuesta";
        texts.addView(text(ctx, e.host + " · " + status + (e.saved ? "" : " · nuevo"), 13, Theme.TEXT_DIM, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (e.saved && !e.serial.isEmpty()) {
            IconView x = new IconView(ctx, Icons.Kind.CLOSE, Theme.TEXT_DIM);
            x.setContentDescription("Olvidar " + e.name);
            x.setOnClickListener(v2 -> controller.forget(e.serial));
            row.addView(x, new LinearLayout.LayoutParams(Theme.dp(ctx, 44), Theme.dp(ctx, 44)));
        }
        return row;
    }
}
