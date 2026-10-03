package com.favio.cornetas;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

/** Alarma periódica que vuelve a arrancar el servicio si el sistema lo cerró. */
final class Watchdog {
    static final String ACTION_CHECK = "com.favio.cornetas.WATCHDOG";
    private static final long INTERVAL_MS = AlarmManager.INTERVAL_FIFTEEN_MINUTES;

    private Watchdog() {}

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, BootReceiver.class).setAction(ACTION_CHECK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, 0, i, flags);
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + INTERVAL_MS, INTERVAL_MS, pending(c));
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(c));
    }

    /** true si la app está excluida de la optimización de batería (necesario para reactivarse sola). */
    static boolean ignoringBatteryOptimizations(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }
}
