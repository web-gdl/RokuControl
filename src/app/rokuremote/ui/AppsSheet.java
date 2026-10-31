package app.rokuremote.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.rokuremote.core.RemoteController;
import app.rokuremote.ecp.RokuApp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Aplicaciones instaladas en el Roku, con filtro por nombre. */
final class AppsSheet extends Sheet {
    private final RemoteController controller;
    private final EditText filter;
    private List<RokuApp> apps = new ArrayList<>();
    private boolean loaded;

    AppsSheet(Context ctx, RemoteController controller) {
        super(ctx, "Aplicaciones");
        this.controller = controller;

        filter = new EditText(ctx);
        filter.setHint("Filtrar");
        filter.setHintTextColor(Theme.TEXT_DIM);
        filter.setTextColor(Theme.TEXT);
        filter.setSingleLine(true);
        filter.setBackground(Theme.rect(Theme.SURFACE, Theme.dp(ctx, 14)));
        int p = Theme.dp(ctx, 12);
        filter.setPadding(p, p, p, p);
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { render(); }
        });
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.topMargin = Theme.dp(ctx, 6);
        addHeader(filter, fp);
        render();
    }

    void setApps(List<RokuApp> list) {
        apps = list;
        loaded = true;
        render();
    }

    private void render() {
        body.removeAllViews();
        if (!loaded) {
            body.addView(msg("Cargando aplicaciones…"));
            return;
        }
        String q = filter.getText().toString().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (RokuApp a : apps) {
            if (!q.isEmpty() && !a.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            body.addView(row(a));
            shown++;
        }
        if (shown == 0) body.addView(msg(apps.isEmpty() ? "Este Roku no informó aplicaciones" : "Sin resultados"));
    }

    private View msg(String s) {
        TextView t = text(ctx, s, 14, Theme.TEXT_DIM, false);
        t.setPadding(0, Theme.dp(ctx, 12), 0, Theme.dp(ctx, 8));
        return t;
    }

    private View row(RokuApp a) {
        TextView t = text(ctx, a.name, 16, Theme.TEXT, false);
        int v = Theme.dp(ctx, 14);
        t.setPadding(Theme.dp(ctx, 16), v, Theme.dp(ctx, 16), v);
        t.setBackground(Theme.pressable(Theme.SURFACE, Theme.SURFACE_PRESSED, Theme.dp(ctx, 16)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(ctx, 8);
        t.setLayoutParams(lp);
        t.setOnClickListener(x -> {
            controller.launch(a);
            dismiss();
        });
        return t;
    }
}
