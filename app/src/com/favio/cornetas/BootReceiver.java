package com.favio.cornetas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Arranca el servicio al encender o reiniciar la TV, tras actualizar la app,
 * y cada vez que salta la alarma de vigilancia si el servicio se cerró.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.enabled(context)) return;
        boolean watchdog = Watchdog.ACTION_CHECK.equals(intent.getAction());
        if (!watchdog && !Prefs.startOnBoot(context)) return;
        if (watchdog && KeepAliveService.running) return;
        try {
            KeepAliveService.start(context);
        } catch (RuntimeException ignored) {
            // El sistema puede negar el arranque en segundo plano; la próxima alarma lo reintenta.
        }
    }
}
