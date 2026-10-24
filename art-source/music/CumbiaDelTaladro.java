import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * "Cumbia del Taladro (FM Tumbera)": the radio module's built-in station. An original instrumental synthesized from
 * scratch: a cumbia section (keyboard lead with vibrato and echo, guiro, timbal, cowbell, bass) and an RKT section
 * (dembow beat, 808 bass), mastered like a car stereo pushed too far: boosted lows, saturated, small speaker. The
 * harmony is the old Andalusian cadence (Am G F E) and its variations; the melodies are written for this track.
 * <p>
 * Usage: java CumbiaDelTaladro.java out.wav, then
 * ffmpeg -i out.wav -c:a libvorbis -q:a 4 src/main/resources/assets/drillships/sounds/music/cumbia_del_taladro.ogg
 * It loops seamlessly: 20 bars at 100 bpm, 48 seconds.
 */
public class CumbiaDelTaladro {
    static final int RATE = 44100;
    static final double BPM = 100;
    static final double BEAT = 60.0 / BPM;
    static final double BAR = 4 * BEAT;
    static final double EIGHTH = BEAT / 2;
    static final double SIXTEENTH = BEAT / 4;
    static final int BARS = 20;

    static float[] mix = new float[(int) (BARS * BAR * RATE) + 1];
    static float[] leadBus = new float[mix.length];
    static Random random = new Random(1987);

    // Chords per bar: root (MIDI) and minor/major third.
    static final int[][] CHORDS = {
            // intro
            {57, 0}, {57, 0},
            // A: Am Am G G F F E E (0 = minor, 1 = major)
            {57, 0}, {57, 0}, {55, 1}, {55, 1}, {53, 1}, {53, 1}, {52, 1}, {52, 1},
            // B: Dm Am E Am Dm Am E E
            {50, 0}, {57, 0}, {52, 1}, {57, 0}, {50, 0}, {57, 0}, {52, 1}, {52, 1},
            // outro (back into the intro)
            {52, 1}, {52, 1}};

    // Lead melody, eighth notes per bar (-1 rest, -2 hold the previous note).
    static final int[][] MELODY_A = {
            {76, -1, 72, 76, 81, -1, 79, 76},
            {77, 76, 74, 72, 71, -1, 72, -1},
            {74, -1, 71, 74, 79, -1, 77, 74},
            {76, 74, 72, 71, 69, -1, 71, -1},
            {72, -1, 69, 72, 77, -1, 76, 72},
            {74, 72, 71, 69, 67, -1, 69, -1},
            {68, -1, 71, 76, 80, -1, 77, 76},
            {74, 72, 71, 68, 64, -2, -2, -1}};
    static final int[][] MELODY_B = {
            {74, 74, -1, 77, -1, 81, -1, 77},
            {76, 76, -1, 72, -1, 76, 69, -1},
            {71, 71, -1, 68, -1, 71, 76, -1},
            {72, 71, 69, -1, 69, -2, -2, -1},
            {74, 74, -1, 77, -1, 81, -1, 77},
            {76, 76, -1, 72, -1, 76, 81, -1},
            {80, -1, 77, 76, -1, 74, 71, -1},
            {68, 71, 74, 77, 76, -2, -2, -1}};

    public static void main(String[] args) throws IOException {
        for (int bar = 0; bar < BARS; bar++) {
            double t0 = bar * BAR;
            boolean rkt = bar >= 10 && bar < 18;
            int[] chord = CHORDS[bar];
            if (rkt) {
                dembow(t0);
                bass808(t0, chord[0] - 12);
            } else {
                cumbiaDrums(t0, bar);
                cumbiaBass(t0, chord);
            }
            guiro(t0, rkt);
            if (bar >= 2) {
                chordStabs(t0, chord, rkt);
            }
            if (bar >= 2 && bar < 10) {
                melody(t0, MELODY_A[bar - 2]);
            } else if (rkt) {
                melody(t0, MELODY_B[bar - 10]);
            }
        }
        echo(leadBus, 3 * SIXTEENTH, 0.32, 0.35);
        for (int i = 0; i < mix.length; i++) {
            mix[i] += leadBus[i];
        }
        master();
        writeWav(args.length > 0 ? args[0] : "cumbia_del_taladro.wav");
    }

    // Cumbia: kick on 1 and 3, timbal on the offbeats, cowbell on 2 and 4, a fill at the end of every 4 bars.
    static void cumbiaDrums(double t0, int bar) {
        kick(t0, 0.9, false);
        kick(t0 + 2 * BEAT, 0.85, false);
        for (int i = 0; i < 4; i++) {
            timbal(t0 + i * BEAT + EIGHTH, i % 2 == 0 ? 330 : 280, 0.35);
        }
        cowbell(t0 + BEAT, 0.3);
        cowbell(t0 + 3 * BEAT, 0.3);
        if (bar % 4 == 3) {
            for (int i = 0; i < 4; i++) {
                timbal(t0 + 3 * BEAT + i * SIXTEENTH, 360 - i * 25, 0.45);
            }
        }
    }

    // RKT: four on the floor and the dembow snare (3 3 2 feel over each half bar).
    static void dembow(double t0) {
        for (int i = 0; i < 4; i++) {
            kick(t0 + i * BEAT, 0.75, true);
        }
        for (int s : new int[] {3, 6, 11, 14}) {
            snare(t0 + s * SIXTEENTH, 0.55);
        }
        for (int i = 0; i < 8; i++) {
            hat(t0 + i * EIGHTH + SIXTEENTH, 0.12);
        }
    }

    // Guiro: the long-short-short scrape of cumbia, on every beat.
    static void guiro(double t0, boolean rkt) {
        for (int beat = 0; beat < 4; beat++) {
            double t = t0 + beat * BEAT;
            scrape(t, EIGHTH * 0.9, rkt ? 0.12 : 0.2);
            scrape(t + EIGHTH, SIXTEENTH * 0.7, rkt ? 0.08 : 0.14);
            scrape(t + EIGHTH + SIXTEENTH, SIXTEENTH * 0.7, rkt ? 0.08 : 0.14);
        }
    }

    // Root on 1, fifth on 3, a pickup on the "and" of 4.
    static void cumbiaBass(double t0, int[] chord) {
        int root = chord[0] - 12;
        bassNote(t0, root, BEAT * 0.9, 0.5);
        bassNote(t0 + 2 * BEAT, root + 7, BEAT * 0.9, 0.45);
        bassNote(t0 + 3 * BEAT + EIGHTH, root + (chord[1] == 1 ? 4 : 3), EIGHTH * 0.9, 0.4);
    }

    // Sliding 808 under the dembow.
    static void bass808(double t0, int root) {
        for (double[] hit : new double[][] {{0, 0, 3}, {3, 0, 3}, {6, 7, 2}, {8, 0, 3}, {11, 0, 3}, {14, 5, 2}}) {
            double start = t0 + hit[0] * SIXTEENTH, len = hit[2] * SIXTEENTH;
            double f = freq(root - 12 + (int) hit[1]);
            int n = (int) (len * RATE);
            for (int i = 0; i < n; i++) {
                double t = (double) i / RATE;
                double glide = f * (1 + 0.6 * Math.exp(-t * 40));
                double env = Math.min(1, t * 400) * Math.exp(-t * 2.5);
                add(mix, start + t, 0.4 * env * Math.tanh(2.5 * Math.sin(2 * Math.PI * glide * t)));
            }
        }
    }

    // Keyboard chords on the offbeats, the cumbia "comping".
    static void chordStabs(double t0, int[] chord, boolean rkt) {
        int root = chord[0] + 12, third = root + (chord[1] == 1 ? 4 : 3), fifth = root + 7;
        for (int i = 0; i < 4; i++) {
            double t = t0 + i * BEAT + EIGHTH;
            for (int note : new int[] {root, third, fifth}) {
                organ(mix, t, freq(note), EIGHTH * 0.6, rkt ? 0.05 : 0.07);
            }
        }
    }

    static void melody(double t0, int[] notes) {
        for (int i = 0; i < notes.length; i++) {
            if (notes[i] < 0) {
                continue;
            }
            int length = 1;
            while (i + length < notes.length && notes[i + length] == -2) {
                length++;
            }
            lead(t0 + i * EIGHTH, freq(notes[i]), length * EIGHTH * 0.92, 0.22);
        }
    }

    // Instruments

    // The cumbia keyboard: two detuned squares and a saw, quick attack, a slow vibrato.
    static void lead(double start, double f, double len, double amp) {
        int n = (int) ((len + 0.08) * RATE);
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double vib = 1 + 0.008 * Math.sin(2 * Math.PI * 5.5 * t) * Math.min(1, t * 4);
            double ph = f * vib * t;
            double s = 0.45 * square(ph * 1.003) + 0.35 * square(ph * 0.997) + 0.3 * saw(ph * 2);
            double env = Math.min(1, t * 300) * (t < len ? 1 - 0.3 * t / len : Math.max(0, 1 - (t - len) / 0.08) * 0.7);
            add(leadBus, start + t, amp * env * s);
        }
    }

    static void organ(float[] bus, double start, double f, double len, double amp) {
        int n = (int) (len * RATE);
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double s = Math.sin(2 * Math.PI * f * t) + 0.5 * Math.sin(4 * Math.PI * f * t) + 0.25 * square(f * 3 * t);
            double env = Math.min(1, t * 500) * Math.exp(-t * 6);
            add(bus, start + t, amp * env * s);
        }
    }

    static void bassNote(double start, int note, double len, double amp) {
        double f = freq(note);
        int n = (int) (len * RATE);
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double s = Math.sin(2 * Math.PI * f * t) + 0.3 * square(f * t);
            double env = Math.min(1, t * 300) * Math.exp(-t * 3);
            add(mix, start + t, amp * env * Math.tanh(1.8 * s));
        }
    }

    static void kick(double start, double amp, boolean long808) {
        double len = long808 ? 0.35 : 0.18;
        int n = (int) (len * RATE);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double f = 45 + 90 * Math.exp(-t * 35);
            phase += 2 * Math.PI * f / RATE;
            double env = Math.exp(-t * (long808 ? 12 : 18));
            add(mix, start + t, amp * env * Math.sin(phase));
        }
    }

    static void snare(double start, double amp) {
        int n = (int) (0.14 * RATE);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            lp += 0.5 * ((random.nextDouble() * 2 - 1) - lp);
            double s = 0.7 * lp + 0.4 * Math.sin(2 * Math.PI * 190 * t);
            add(mix, start + t, amp * Math.exp(-t * 28) * s);
        }
    }

    static void hat(double start, double amp) {
        int n = (int) (0.04 * RATE);
        double prev = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double noise = random.nextDouble() * 2 - 1;
            double high = noise - prev;
            prev = noise;
            add(mix, start + t, amp * Math.exp(-t * 90) * high);
        }
    }

    // High-passed noise with a ratchet: the teeth of the guiro.
    static void scrape(double start, double len, double amp) {
        int n = (int) (len * RATE);
        double prev = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double noise = random.nextDouble() * 2 - 1;
            double high = noise - prev;
            prev = noise;
            double teeth = 0.55 + 0.45 * square(t * 70);
            double env = Math.min(1, t * 200) * Math.min(1, (len - t) * 80);
            add(mix, start + t, amp * env * teeth * high);
        }
    }

    static void timbal(double start, double f, double amp) {
        int n = (int) (0.12 * RATE);
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double s = Math.sin(2 * Math.PI * f * t) + 0.4 * (random.nextDouble() * 2 - 1) * Math.exp(-t * 60);
            add(mix, start + t, amp * Math.exp(-t * 22) * s);
        }
    }

    static void cowbell(double start, double amp) {
        int n = (int) (0.15 * RATE);
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double s = square(540 * t) + square(800 * t);
            add(mix, start + t, amp * 0.5 * Math.exp(-t * 20) * s);
        }
    }

    // Effects

    static void echo(float[] bus, double delay, double feedback, double wet) {
        int d = (int) (delay * RATE);
        float[] out = bus.clone();
        for (int i = d; i < bus.length; i++) {
            out[i] += (float) (feedback * out[i - d]);
        }
        for (int i = 0; i < bus.length; i++) {
            bus[i] = (float) (bus[i] * (1 - wet) + out[i] * wet + bus[i] * wet * 0.5);
        }
    }

    // A car stereo pushed too far: boomy lows, hard saturation, a small speaker's top end, and the rattle of the trunk.
    static void master() {
        double lowState = 0, hpState = 0, prevIn = 0, lpState = 0, rattleEnv = 0;
        double peak = 0;
        float[] out = new float[mix.length];
        for (int i = 0; i < mix.length; i++) {
            double x = mix[i];
            // High-pass at ~40 Hz.
            hpState = 0.994 * (hpState + x - prevIn);
            prevIn = x;
            // Bass boost: add the low band again.
            lowState += 0.012 * (hpState - lowState);
            double y = hpState + 1.4 * lowState;
            // Saturation, slightly asymmetric like a clipping amp.
            y = Math.tanh(2.0 * y + 0.08 * y * y);
            // Small speaker: low-pass ~7 kHz.
            lpState += 0.6 * (y - lpState);
            y = lpState;
            // Trunk rattle: buzz that follows the bass.
            rattleEnv = Math.max(Math.abs(lowState), rattleEnv * 0.9995);
            y += 0.03 * rattleEnv * (random.nextDouble() * 2 - 1);
            out[i] = (float) y;
            peak = Math.max(peak, Math.abs(y));
        }
        for (int i = 0; i < out.length; i++) {
            mix[i] = (float) (out[i] / peak * 0.89);
        }
    }

    // Helpers

    static double freq(int midi) {
        return 440.0 * Math.pow(2, (midi - 69) / 12.0);
    }

    static double square(double phase) {
        return (phase - Math.floor(phase)) < 0.5 ? 1 : -1;
    }

    static double saw(double phase) {
        return 2 * (phase - Math.floor(phase)) - 1;
    }

    // Wraps past the end, so tails of the last bar ring into the start of the loop.
    static void add(float[] bus, double time, double value) {
        int i = (int) (time * RATE);
        bus[Math.floorMod(i, bus.length)] += (float) value;
    }

    static void writeWav(String path) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(path))) {
            int bytes = mix.length * 2;
            out.writeBytes("RIFF");
            out.writeInt(Integer.reverseBytes(36 + bytes));
            out.writeBytes("WAVEfmt ");
            out.writeInt(Integer.reverseBytes(16));
            out.writeShort(Short.reverseBytes((short) 1));
            out.writeShort(Short.reverseBytes((short) 1));
            out.writeInt(Integer.reverseBytes(RATE));
            out.writeInt(Integer.reverseBytes(RATE * 2));
            out.writeShort(Short.reverseBytes((short) 2));
            out.writeShort(Short.reverseBytes((short) 16));
            out.writeBytes("data");
            out.writeInt(Integer.reverseBytes(bytes));
            for (float s : mix) {
                out.writeShort(Short.reverseBytes((short) Math.round(Math.max(-1, Math.min(1, s)) * 32767)));
            }
        }
    }
}
