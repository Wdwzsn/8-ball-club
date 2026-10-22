package com.pool8.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import java.util.Random;

/**
 * 纯代码合成的音效与音乐（无任何音频文件）。
 *
 * 采用单例 + 多实例 Clip 池：高频音效（撞球/撞库/出杆/点击）各有多个 Clip 轮换，
 * 避免快速连击时互相打断；BGM 用 LOOP_CONTINUOUSLY 循环。
 */
public final class SoundFx {
    private static final int RATE = 44100;
    private static SoundFx instance;

    private boolean enabled = true;
    private float masterVolume = 1f;
    private final Clip[] ball, rail, cue, click;
    private final int[] idx = {0, 0, 0, 0};
    private final Clip pot, win, lose, bgm;
    private boolean bgmOn;

    public static synchronized SoundFx get() {
        if (instance == null) instance = new SoundFx();
        return instance;
    }

    private SoundFx() {
        ball = pool(3, synthBallHit());
        rail = pool(3, synthRailHit());
        cue = pool(2, synthCueStrike());
        click = pool(4, synthClick());
        pot = clip(synthPotted());
        win = clip(synthWin());
        lose = clip(synthLose());
        bgm = clip(synthBgm());
        boolean any = false;
        for (Clip c : ball) any |= c != null;
        for (Clip c : rail) any |= c != null;
        for (Clip c : cue) any |= c != null;
        for (Clip c : click) any |= c != null;
        any |= pot != null || win != null || lose != null;
        enabled = any;
    }

    // ---------------------------------------------------------------- 对外接口

    public boolean isEnabled() { return enabled; }

    public float getVolume() { return masterVolume; }

    /** 0..1 主音量，对音乐与音效同时生效。 */
    public void setVolume(float v) {
        masterVolume = Math.max(0f, Math.min(1f, v));
    }

    public void setEnabled(boolean on) {
        enabled = on;
        if (on) {
            if (bgmOn) loopBgm();
        } else {
            stop(bgm);
        }
    }

    public void startBgm() {
        bgmOn = true;
        if (enabled) loopBgm();
    }

    public void stopBgm() {
        bgmOn = false;
        stop(bgm);
    }

    public void ballHit(double impact) {
        play(ball, idx[0]++, volDb((float) clamp(impact / 4.0, 0.15, 1.0)));
    }

    public void railHit(double impact) {
        play(rail, idx[1]++, volDb((float) clamp(impact / 5.0, 0.12, 0.8)));
    }

    public void cueStrike(double power) {
        play(cue, idx[2]++, volDb((float) clamp(0.3 + power, 0.3, 1.0)));
    }

    public void potted() { play(pot, volDb(0.8f)); }
    public void click() { play(click, idx[3]++, volDb(0.6f)); }
    public void win() { play(win, volDb(0.85f)); }
    public void lose() { play(lose, volDb(0.85f)); }

    // ---------------------------------------------------------------- 播放

    private void play(Clip c, float db) {
        if (!enabled || c == null) return;
        try {
            if (c.isRunning()) c.stop();
            c.setFramePosition(0);
            setGain(c, masterDb(db));
            c.start();
        } catch (Exception ignored) {}
    }

    private void play(Clip[] pool, int i, float db) {
        if (!enabled || pool == null) return;
        Clip c = pool[Math.floorMod(i, pool.length)];
        if (c == null) return;
        try {
            if (c.isRunning()) c.stop();
            c.setFramePosition(0);
            setGain(c, masterDb(db));
            c.start();
        } catch (Exception ignored) {}
    }

    private void loopBgm() {
        if (bgm == null) return;
        try {
            setGain(bgm, masterDb(-16f));
            bgm.setFramePosition(0);
            bgm.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception ignored) {}
    }

    private float masterDb(float db) {
        if (masterVolume < 0.001f) return -80f;
        return (float) (db + 20.0 * Math.log10(masterVolume));
    }

    private static void stop(Clip c) {
        if (c == null) return;
        try { if (c.isRunning()) c.stop(); } catch (Exception ignored) {}
    }

    private static void setGain(Clip c, float db) {
        try {
            if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl fc = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                db = Math.max(fc.getMinimum(), Math.min(fc.getMaximum(), db));
                fc.setValue(db);
            }
        } catch (Exception ignored) {}
    }

    private static float volDb(float vol) {
        return (float) (-30.0 + 26.0 * clamp(vol, 0.0, 1.0));
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    // ---------------------------------------------------------------- 合成

    private static Clip[] pool(int n, float[] samples) {
        Clip[] arr = new Clip[n];
        for (int i = 0; i < n; i++) arr[i] = clip(samples);
        return arr;
    }

    private static Clip clip(float[] samples) {
        try {
            byte[] b = new byte[samples.length * 2];
            for (int i = 0; i < samples.length; i++) {
                int v = (int) (Math.max(-1f, Math.min(1f, samples[i])) * 32767);
                b[i * 2] = (byte) (v & 0xFF);
                b[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
            }
            AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
            Clip c = (Clip) AudioSystem.getLine(new Line.Info(Clip.class));
            c.open(fmt, b, 0, b.length);
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    private static float[] buffer(double seconds) {
        return new float[(int) (RATE * seconds)];
    }

    private static void addTone(float[] s, double t0, double dur, double freq,
                                double amp, double attack, double release) {
        int lo = Math.max(0, (int) Math.floor(t0 * RATE));
        int hi = Math.min(s.length, (int) Math.ceil((t0 + dur) * RATE));
        for (int i = lo; i < hi; i++) {
            double t = i / (double) RATE - t0;
            double env = 1.0;
            if (attack > 0 && t < attack) env = t / attack;
            double rem = dur - t;
            if (release > 0 && rem < release) env *= Math.max(0.0, rem / release);
            s[i] += (float) (Math.sin(2 * Math.PI * freq * t) * amp * env);
        }
    }

    private static float[] synthClick() {
        float[] s = buffer(0.045);
        for (int i = 0; i < s.length; i++) {
            double t = i / (double) RATE;
            double env = Math.exp(-t * 95);
            s[i] = (float) ((Math.sin(2 * Math.PI * 1500 * t) + 0.5 * Math.sin(2 * Math.PI * 920 * t)) * 0.45 * env);
        }
        return s;
    }

    private static float[] synthCueStrike() {
        Random r = new Random(11);
        float[] s = buffer(0.06);
        for (int i = 0; i < s.length; i++) {
            double t = i / (double) RATE;
            double env = Math.exp(-t * 70);
            double v = 0.6 * Math.sin(2 * Math.PI * 950 * t) + 0.35 * (r.nextDouble() * 2 - 1);
            s[i] = (float) (v * 0.7 * env);
        }
        return s;
    }

    private static float[] synthBallHit() {
        Random r = new Random(23);
        float[] s = buffer(0.055);
        for (int i = 0; i < s.length; i++) {
            double t = i / (double) RATE;
            double env = Math.exp(-t * 55);
            double v = 0.55 * Math.sin(2 * Math.PI * 2100 * t)
                    + 0.3 * Math.sin(2 * Math.PI * 3400 * t + 0.7)
                    + 0.22 * (r.nextDouble() * 2 - 1);
            s[i] = (float) (v * env);
        }
        return s;
    }

    private static float[] synthRailHit() {
        Random r = new Random(37);
        float[] s = buffer(0.095);
        for (int i = 0; i < s.length; i++) {
            double t = i / (double) RATE;
            double env = Math.exp(-t * 24);
            double v = 0.6 * Math.sin(2 * Math.PI * 185 * t)
                    + 0.25 * Math.sin(2 * Math.PI * 92 * t)
                    + 0.18 * (r.nextDouble() * 2 - 1);
            s[i] = (float) (v * env);
        }
        return s;
    }

    private static float[] synthPotted() {
        Random r = new Random(53);
        float[] s = buffer(0.30);
        double phase = 0;
        for (int i = 0; i < s.length; i++) {
            double t = i / (double) RATE;
            double f = 360 * Math.exp(-t * 7) + 70;
            phase += f / RATE;
            double env = Math.exp(-t * 9);
            s[i] = (float) ((0.5 * Math.sin(2 * Math.PI * phase) + 0.2 * (r.nextDouble() * 2 - 1)) * env);
        }
        return s;
    }

    private static float[] synthWin() {
        float[] s = buffer(1.7);
        double[] ns = {523.25, 659.25, 783.99, 1046.50};
        for (int i = 0; i < ns.length; i++) {
            addTone(s, i * 0.16, 0.5, ns[i], 0.42, 0.01, 0.2);
            addTone(s, i * 0.16, 0.5, ns[i] * 2, 0.14, 0.01, 0.2);
        }
        for (double f : ns) addTone(s, 0.72, 0.9, f, 0.16, 0.15, 0.5);
        return s;
    }

    private static float[] synthLose() {
        float[] s = buffer(1.7);
        double[] ns = {523.25, 493.88, 392.00, 329.63, 261.63};
        for (int i = 0; i < ns.length; i++) {
            addTone(s, i * 0.22, 0.55, ns[i], 0.36, 0.01, 0.25);
        }
        addTone(s, 1.15, 0.5, 130.81, 0.22, 0.05, 0.35);
        return s;
    }

    /** 8 秒循环的轻音乐：C - Am - F - G 分解和弦。 */
    private static float[] synthBgm() {
        float[] s = buffer(8.0);
        double bar = 2.0;
        double[][] chords = {
                {130.81, 261.63, 329.63, 392.00},   // C : C3 C4 E4 G4
                {110.00, 220.00, 261.63, 329.63},   // Am: A2 A3 C4 E4
                {87.31, 174.61, 220.00, 261.63},    // F : F2 F3 A3 C4
                {98.00, 196.00, 246.94, 293.66},    // G : G2 G3 B3 D4
        };
        for (int barIdx = 0; barIdx < 4; barIdx++) {
            double t0 = barIdx * bar;
            double bass = chords[barIdx][0];
            addTone(s, t0, bar * 0.98, bass, 0.16, 0.05, 0.5);
            double[] arp = {chords[barIdx][1], chords[barIdx][2], chords[barIdx][3], chords[barIdx][2],
                    chords[barIdx][1], chords[barIdx][3], chords[barIdx][2], chords[barIdx][3]};
            for (int k = 0; k < 8; k++) {
                double nt = t0 + k * (bar / 8.0);
                addTone(s, nt, 0.6, arp[k], 0.085, 0.012, 0.28);
                addTone(s, nt, 0.6, arp[k] * 2.0, 0.03, 0.012, 0.28);
            }
        }
        return s;
    }
}
