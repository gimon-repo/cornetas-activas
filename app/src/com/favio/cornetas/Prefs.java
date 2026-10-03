package com.favio.cornetas;

import android.content.Context;
import android.content.SharedPreferences;

/** Ajustes guardados de la app. */
final class Prefs {
    /** Frecuencias del tono; 0 = mezcla de 30 Hz y 50 Hz. */
    static final int[] FREQS_HZ = {20, 30, 50, 0};
    static final String[] FREQ_NAMES = {"20 Hz", "30 Hz", "50 Hz", "Mezcla 30 + 50 Hz"};
    static final int MODE_CONTINUOUS = 0, MODE_PULSES = 1, MODE_COMBINED = 2;
    static final String[] MODE_NAMES = {"Continuo", "Pulsos", "Combinado (recomendado)"};
    static final float[] LEVELS = {0.005f, 0.015f, 0.04f, 0.10f};
    static final String[] LEVEL_NAMES = {"Muy bajo", "Bajo", "Medio", "Alto"};
    static final int[] INTERVALS_MIN = {1, 3, 5, 10};

    private static final String FILE = "cornetas";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) { return sp(c).getBoolean("enabled", false); }
    static void setEnabled(Context c, boolean v) { sp(c).edit().putBoolean("enabled", v).apply(); }

    static boolean startOnBoot(Context c) { return sp(c).getBoolean("boot", true); }
    static void setStartOnBoot(Context c, boolean v) { sp(c).edit().putBoolean("boot", v).apply(); }

    /** Continuo = tono fijo; Pulsos = solo ráfagas periódicas; Combinado = tono fijo + ráfagas más fuertes. */
    static int mode(Context c) { return clamp(sp(c).getInt("mode", MODE_COMBINED), MODE_NAMES.length); }
    static void setMode(Context c, int m) { sp(c).edit().putInt("mode", m).apply(); }

    static int freqIndex(Context c) { return clamp(sp(c).getInt("freq2", 3), FREQS_HZ.length); }
    static void setFreqIndex(Context c, int i) { sp(c).edit().putInt("freq2", i).apply(); }

    /** Sube la señal automáticamente cuando el volumen de la TV está bajo. */
    static boolean compensate(Context c) { return sp(c).getBoolean("compensate", true); }
    static void setCompensate(Context c, boolean v) { sp(c).edit().putBoolean("compensate", v).apply(); }

    static int levelIndex(Context c) { return clamp(sp(c).getInt("level", 1), LEVELS.length); }
    static void setLevelIndex(Context c, int i) { sp(c).edit().putInt("level", i).apply(); }

    static int intervalIndex(Context c) { return clamp(sp(c).getInt("interval", 2), INTERVALS_MIN.length); }
    static void setIntervalIndex(Context c, int i) { sp(c).edit().putInt("interval", i).apply(); }

    static boolean askedNotif(Context c) { return sp(c).getBoolean("asked_notif", false); }
    static void setAskedNotif(Context c) { sp(c).edit().putBoolean("asked_notif", true).apply(); }

    private static int clamp(int i, int n) { return (i < 0 || i >= n) ? 0 : i; }
}
