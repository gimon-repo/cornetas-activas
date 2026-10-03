package com.favio.cornetas;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String PERM_NOTIF = "android.permission.POST_NOTIFICATIONS";
    private static final int REQ_NOTIF = 1;

    private TextView status, permStatus;
    private Button toggle, mode, interval, freq, level, compensate, boot, perms, test;
    /** Evita volver a abrir el mismo diálogo de permisos en bucle durante una visita. */
    private boolean askedNotifThisVisit, askedBatteryThisVisit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        toggle = findViewById(R.id.toggle);
        mode = findViewById(R.id.mode);
        interval = findViewById(R.id.interval);
        freq = findViewById(R.id.freq);
        level = findViewById(R.id.level);
        boot = findViewById(R.id.boot);
        compensate = findViewById(R.id.compensate);
        test = findViewById(R.id.test);
        perms = findViewById(R.id.perms);
        permStatus = findViewById(R.id.perm_status);

        toggle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            boolean on = !KeepAliveService.running;
            Prefs.setEnabled(MainActivity.this, on);
            if (on) {
                KeepAliveService.start(MainActivity.this);
                askNextPermission(false);
            } else {
                Watchdog.cancel(MainActivity.this);
                KeepAliveService.stop(MainActivity.this);
            }
            // El servicio tarda un instante en marcarse como activo.
            v.postDelayed(new Runnable() { @Override public void run() { refresh(); }}, 300);
            refresh(on);
        }});
        mode.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            Prefs.setMode(MainActivity.this, (Prefs.mode(MainActivity.this) + 1) % Prefs.MODE_NAMES.length);
            applyChange();
        }});
        interval.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            Prefs.setIntervalIndex(MainActivity.this, (Prefs.intervalIndex(MainActivity.this) + 1) % Prefs.INTERVALS_MIN.length);
            applyChange();
        }});
        freq.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            Prefs.setFreqIndex(MainActivity.this, (Prefs.freqIndex(MainActivity.this) + 1) % Prefs.FREQS_HZ.length);
            applyChange();
        }});
        level.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            Prefs.setLevelIndex(MainActivity.this, (Prefs.levelIndex(MainActivity.this) + 1) % Prefs.LEVELS.length);
            applyChange();
        }});
        compensate.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            Prefs.setCompensate(MainActivity.this, !Prefs.compensate(MainActivity.this));
            applyChange();
        }});
        boot.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            Prefs.setStartOnBoot(MainActivity.this, !Prefs.startOnBoot(MainActivity.this));
            refresh();
        }});
        test.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playTestTone(); }});
        perms.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            askNextPermission(true);
        }});
        // Si quedó activado y el sistema cerró el servicio, se vuelve a arrancar al abrir la app.
        if (Prefs.enabled(MainActivity.this) && !KeepAliveService.running) {
            KeepAliveService.start(MainActivity.this);
        }
        toggle.requestFocus();
    }

    /** Actualiza el estado en pantalla (activo / en descanso) cada 2 s mientras la app está abierta. */
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            status.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        status.removeCallbacks(ticker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.removeCallbacks(ticker);
        status.postDelayed(ticker, 2000);
        refresh();
        // Al abrir la app se piden, uno por uno, los permisos que falten.
        askNextPermission(false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        refresh();
        askNextPermission(false);
    }

    private boolean notificationsGranted() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(PERM_NOTIF) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Pide el siguiente permiso que falte: primero notificaciones (Android 13+, necesario para que
     * el servicio en 2do plano sea visible) y luego quitar la optimización de batería, que permite
     * que la app siga activa y se reactive sola tras reiniciar o encender la TV.
     * Con force=true (botón "Revisar permisos") abre la pantalla de ajustes aunque ya se haya pedido.
     */
    private void askNextPermission(boolean force) {
        if (!notificationsGranted() && (force || !askedNotifThisVisit)) {
            askedNotifThisVisit = true;
            if (force && !shouldShowRequestPermissionRationale(PERM_NOTIF) && Prefs.askedNotif(this)) {
                // El usuario ya lo negó antes: solo se puede activar desde Ajustes.
                openSettings(new Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                        .putExtra("android.provider.extra.APP_PACKAGE", getPackageName()));
            } else {
                Prefs.setAskedNotif(this);
                requestPermissions(new String[]{PERM_NOTIF}, REQ_NOTIF);
            }
            return;
        }
        if (!Watchdog.ignoringBatteryOptimizations(this) && (force || !askedBatteryThisVisit)) {
            askedBatteryThisVisit = true;
            Intent direct = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            if (!openSettings(direct)
                    && !openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
                openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
            return;
        }
        if (force) {
            openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private boolean openSettings(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /** Guarda el ajuste y, si está activo, el servicio lo toma al instante. */
    private void applyChange() {
        if (KeepAliveService.running) KeepAliveService.start(MainActivity.this);
        refresh();
    }

    private void refresh() {
        refresh(KeepAliveService.running);
    }

    private void refresh(boolean on) {
        boolean rest = on && KeepAliveService.resting;
        status.setText(!on ? "○ Detenido"
                : rest ? "◐ EN DESCANSO: no hay cornetas Bluetooth conectadas"
                : "● ACTIVO: las cornetas reciben señal");
        status.setTextColor(!on ? 0xFFEF5350 : rest ? 0xFFFFCA28 : 0xFF66BB6A);
        toggle.setText(on ? "Detener" : "Activar");
        int md = Prefs.mode(MainActivity.this);
        boolean pulse = md != Prefs.MODE_CONTINUOUS;
        mode.setText("Modo: " + Prefs.MODE_NAMES[md]);
        compensate.setText("Compensar volumen bajo de la TV: "
                + (Prefs.compensate(MainActivity.this) ? "Sí" : "No"));
        interval.setText("Pulso cada: " + Prefs.INTERVALS_MIN[Prefs.intervalIndex(MainActivity.this)] + " min");
        interval.setEnabled(pulse);
        interval.setAlpha(pulse ? 1f : 0.4f);
        freq.setText("Frecuencia: " + Prefs.FREQ_NAMES[Prefs.freqIndex(MainActivity.this)]);
        level.setText("Nivel: " + Prefs.LEVEL_NAMES[Prefs.levelIndex(MainActivity.this)]);
        boolean notif = notificationsGranted();
        boolean battery = Watchdog.ignoringBatteryOptimizations(MainActivity.this);
        permStatus.setText("Permisos: notificaciones " + (notif ? "✓" : "✗")
                + "   ·   sin límite de batería " + (battery ? "✓" : "✗")
                + (notif && battery ? "" : "   (pulsa Revisar permisos)"));
        permStatus.setTextColor(notif && battery ? 0xFF66BB6A : 0xFFFFCA28);
        boot.setText("Iniciar al encender la TV: " + (Prefs.startOnBoot(MainActivity.this) ? "Sí" : "No"));
    }

    /** Tono de 440 Hz audible durante 2 s para comprobar que el audio llega a las cornetas. */
    private void playTestTone() {
        new Thread(new Runnable() { @Override public void run() {
            int rate = 44100;
            short[] pcm = new short[rate * 2];
            for (int i = 0; i < pcm.length; i++) {
                double env = Math.min(1.0, Math.min(i, pcm.length - i) / (rate * 0.05));
                pcm[i] = (short) (Math.sin(2 * Math.PI * 440 * i / rate) * env * 0.25 * Short.MAX_VALUE);
            }
            AudioTrack t = new AudioTrack(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                    new AudioFormat.Builder().setSampleRate(rate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    pcm.length * 2, AudioTrack.MODE_STATIC,
                    android.media.AudioManager.AUDIO_SESSION_ID_GENERATE);
            t.write(pcm, 0, pcm.length);
            t.play();
            try { Thread.sleep(2300); } catch (InterruptedException ignored) { }
            t.release();
        }}).start();
    }
}
