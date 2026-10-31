package app.rokuremote.ui;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import app.rokuremote.core.RemoteController;
import app.rokuremote.ecp.Key;

/**
 * Teclado: lo que se escribe aquí se envía carácter a carácter al teclado en pantalla del Roku
 * (ECP solo acepta texto mientras el Roku muestra su propio teclado).
 */
final class KeyboardSheet extends Sheet {
    KeyboardSheet(Context ctx, RemoteController controller) {
        super(ctx, "Teclado");

        EditText input = new EditText(ctx);
        input.setHint("Escribe aquí");
        input.setHintTextColor(Theme.TEXT_DIM);
        input.setTextColor(Theme.TEXT);
        input.setSingleLine(true);
        // Sin sugerencias ni autocorrección: cada tecla se envía tal cual.
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setBackground(Theme.rect(Theme.SURFACE, Theme.dp(ctx, 14)));
        int p = Theme.dp(ctx, 14);
        input.setPadding(p, p, p, p);

        input.addTextChangedListener(new TextWatcher() {
            private int removedCodePoints;
            private String added = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                String removed = s.subSequence(start, start + count).toString();
                removedCodePoints = removed.codePointCount(0, removed.length());
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                added = s.subSequence(start, start + count).toString();
            }

            @Override
            public void afterTextChanged(Editable s) {
                for (int i = 0; i < removedCodePoints; i++) controller.sendKey(Key.BACKSPACE, false);
                for (int i = 0; i < added.length(); ) {
                    int cp = added.codePointAt(i);
                    controller.sendLiteral(cp);
                    i += Character.charCount(cp);
                }
                removedCodePoints = 0;
                added = "";
            }
        });
        // Tecla de acción del teclado del teléfono = Enter en el Roku (buscar / aceptar).
        input.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                controller.sendKey(Key.ENTER, false);
                return true;
            }
            return false;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        body.addView(input, lp);
        body.addView(text(ctx, "Funciona cuando el Roku muestra su teclado en pantalla (por ejemplo, al buscar). "
                + "Usa la tecla de enviar del teléfono para pulsar Enter en el Roku.", 13, Theme.TEXT_DIM, false));

        showKeyboardOnOpen(input);
    }
}
