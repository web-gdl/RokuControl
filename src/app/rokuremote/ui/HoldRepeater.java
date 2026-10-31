package app.rokuremote.ui;

import android.os.Handler;
import android.os.Looper;

/** Repite una acción mientras se mantiene pulsado (como un control físico). */
final class HoldRepeater {
    interface Action {
        /** @param repeat false en la pulsación inicial, true en las repeticiones. */
        void run(boolean repeat);
    }

    private static final long FIRST_DELAY_MS = 450;
    private static final long INTERVAL_MS = 120;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable loop;

    void start(Action action, boolean repeatable) {
        stop();
        action.run(false);
        if (!repeatable) return;
        loop = new Runnable() {
            @Override public void run() {
                action.run(true);
                handler.postDelayed(this, INTERVAL_MS);
            }
        };
        handler.postDelayed(loop, FIRST_DELAY_MS);
    }

    void stop() {
        if (loop != null) handler.removeCallbacks(loop);
        loop = null;
    }
}
