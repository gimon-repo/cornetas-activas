package com.favio.cornetas;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Servicio en primer plano que reproduce sin parar un tono grave de muy bajo nivel
 * (o pulsos periódicos) para que las cornetas Bluetooth sigan recibiendo señal y no se apaguen.
 * No pide foco de audio, así que se mezcla con lo que esté sonando en otras apps.
 */
public class KeepAliveService extends Service {
    static final String ACTION_STOP = "com.favio.cornetas.STOP";

    private static final String CHANNEL_ID = "keepalive";
    private static final int NOTIFICATION_ID = 1;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHUNK_FRAMES = 2048;
    private static final double PULSE_SECONDS = 3.0;
    private static final double FADE_SECONDS = 0.25;
    /** En los pulsos la señal sube 4 veces (+12 dB) respecto al tono base. */
    private static final double PULSE_BOOST = 4.0;
    /** Tope de amplitud (-6 dBFS) aunque la compensación pida más. */
    private static final double MAX_AMPLITUDE = 0.5;

    static volatile boolean running;

    private volatile boolean stopRequested;
    private volatile boolean restartTrack;
    private volatile int freqHz;
    private volatile float level;
    private volatile int mode;
    private volatile boolean compensate;
    private volatile long intervalFrames;

    private Thread audioThread;
    private PowerManager.WakeLock wakeLock;

    /** Al despertar la pantalla se recrea la pista, por si el enrutamiento Bluetooth cambió. */
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            restartTrack = true;
        }
    };

    static void start(Context c) {
        Intent i = new Intent(c, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            c.startForegroundService(i);
        } else {
            c.startService(i);
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, KeepAliveService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, f);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Prefs.setEnabled(this, false);
            Watchdog.cancel(this);
            stopSelf();
            return START_NOT_STICKY;
        }
        goForeground();
        Watchdog.schedule(this);
        loadSettings();
        if (audioThread == null) {
            stopRequested = false;
            audioThread = new Thread(new Runnable() {
                @Override public void run() { audioLoop(); }
            }, "cornetas-audio");
            audioThread.start();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cornetas:audio");
            wakeLock.acquire();
        }
        running = true;
        return START_STICKY;
    }

    private void loadSettings() {
        freqHz = Prefs.FREQS_HZ[Prefs.freqIndex(this)];
        level = Prefs.LEVELS[Prefs.levelIndex(this)];
        mode = Prefs.mode(this);
        compensate = Prefs.compensate(this);
        intervalFrames = (long) Prefs.INTERVALS_MIN[Prefs.intervalIndex(this)] * 60L * SAMPLE_RATE;
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Cornetas activas", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        int piFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent open = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), piFlags);
        PendingIntent stop = PendingIntent.getService(
                this, 1, new Intent(this, KeepAliveService.class).setAction(ACTION_STOP), piFlags);
        Notification n = b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Cornetas activas")
                .setContentText("Manteniendo las cornetas Bluetooth encendidas")
                .setContentIntent(open)
                .addAction(android.R.drawable.ic_media_pause, "Detener", stop)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private static AudioTrack createTrack() {
        int min = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat fmt = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();
        AudioTrack t = new AudioTrack(attrs, fmt, Math.max(min, CHUNK_FRAMES * 4 * 2),
                AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        t.play();
        return t;
    }

    /**
     * Cuánto atenúa el volumen actual de la TV la señal, como factor lineal (1 = sin atenuar).
     * Usa la curva real de volumen del sistema (Android 9+) y, como mínimo, una estimación por la
     * posición del volumen: con "volumen absoluto" Bluetooth la atenuación la hace la corneta y el
     * sistema reporta 0 dB, pero igual llega menos señal a su detector.
     */
    private float volumeAttenuation(AudioManager am) {
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (cur <= 0 || max <= 0) return 1f; // en silencio no hay nada que compensar
        double frac = (double) cur / max;
        double estimateDb = -40.0 * (1.0 - frac);
        double db = estimateDb;
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                float sysDb = am.getStreamVolumeDb(AudioManager.STREAM_MUSIC, cur,
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
                if (!Float.isNaN(sysDb) && !Float.isInfinite(sysDb)) db = Math.min(db, sysDb);
            } catch (RuntimeException ignored) { }
        }
        return (float) Math.pow(10, db / 20.0);
    }

    private void audioLoop() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        short[] buf = new short[CHUNK_FRAMES * 2];
        double phaseA = 0, phaseB = 0;
        long frame = 0;
        long pulseFrames = (long) (PULSE_SECONDS * SAMPLE_RATE);
        long fadeFrames = (long) (FADE_SECONDS * SAMPLE_RATE);
        double amp = 0; // amplitud suavizada para que los cambios de volumen no hagan "clic"
        int chunks = 0;
        float compensation = 1f;
        AudioTrack track = null;
        while (!stopRequested) {
            try {
                if (track == null || restartTrack) {
                    restartTrack = false;
                    if (track != null) track.release();
                    track = createTrack();
                }
                // Revisa el volumen de la TV unas 2 veces por segundo.
                if (chunks++ % 10 == 0) {
                    compensation = compensate ? 1f / volumeAttenuation(am) : 1f;
                }
                int f = freqHz;
                boolean mix = f == 0;
                double stepA = 2 * Math.PI * (mix ? 30 : f) / SAMPLE_RATE;
                double stepB = 2 * Math.PI * 50 / SAMPLE_RATE;
                int m = mode;
                long interval = Math.max(intervalFrames, pulseFrames + 1);
                double base = Math.min(MAX_AMPLITUDE, level * compensation);
                double boosted = Math.min(MAX_AMPLITUDE, base * PULSE_BOOST);
                for (int i = 0; i < CHUNK_FRAMES; i++) {
                    // Envolvente del pulso: 0 fuera del pulso, 1 en el centro, con rampas suaves.
                    long pos = frame % interval;
                    double env;
                    if (pos >= pulseFrames) env = 0;
                    else if (pos < fadeFrames) env = (double) pos / fadeFrames;
                    else if (pos > pulseFrames - fadeFrames) env = (double) (pulseFrames - pos) / fadeFrames;
                    else env = 1.0;
                    double target;
                    if (m == Prefs.MODE_CONTINUOUS) target = base;
                    else if (m == Prefs.MODE_PULSES) target = boosted * env;
                    else target = base + (boosted - base) * env;
                    amp += (target - amp) * 0.0005; // ~45 ms de transición
                    double wave = mix ? 0.5 * (Math.sin(phaseA) + Math.sin(phaseB)) : Math.sin(phaseA);
                    short s = (short) (wave * amp * Short.MAX_VALUE);
                    buf[2 * i] = s;
                    buf[2 * i + 1] = s;
                    phaseA += stepA;
                    if (phaseA > 2 * Math.PI) phaseA -= 2 * Math.PI;
                    phaseB += stepB;
                    if (phaseB > 2 * Math.PI) phaseB -= 2 * Math.PI;
                    frame++;
                }
                int written = track.write(buf, 0, buf.length);
                if (written < 0) {
                    restartTrack = true;
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                break;
            } catch (RuntimeException e) {
                // La salida de audio pudo cambiar (cornetas reconectadas); reintentar.
                restartTrack = true;
                try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
            }
        }
        if (track != null) {
            try { track.stop(); } catch (RuntimeException ignored) { }
            track.release();
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        stopRequested = true;
        if (audioThread != null) {
            audioThread.interrupt();
            audioThread = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (!Prefs.enabled(this)) Watchdog.cancel(this);
        try { unregisterReceiver(screenReceiver); } catch (RuntimeException ignored) { }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
