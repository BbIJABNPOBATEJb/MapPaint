package me.bbijabnpobatejb.paintcraft.diag;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

import java.util.Arrays;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Profiler {

    public static final String[] PHASES = {"paint", "panel", "preview"};
    private static final int WINDOW = 1200;

    long[][] samples = new long[PHASES.length][WINDOW];
    @NonFinal int cursor;
    @Getter
    @NonFinal int size;

    public void record(long... nanos) {
        for (int phase = 0; phase < PHASES.length; phase++) samples[phase][cursor] = nanos[phase];
        cursor = (cursor + 1) % WINDOW;
        size = Math.min(size + 1, WINDOW);
    }

    public double average(int phase) {
        if (size == 0) return 0;
        long sum = 0;
        for (int i = 0; i < size; i++) sum += value(phase, i);
        return sum / (double) size / 1_000_000.0;
    }

    public double max(int phase) {
        long max = 0;
        for (int i = 0; i < size; i++) max = Math.max(max, value(phase, i));
        return max / 1_000_000.0;
    }

    public double percentile(int phase, double share) {
        if (size == 0) return 0;
        long[] sorted = new long[size];
        for (int i = 0; i < size; i++) sorted[i] = value(phase, i);
        Arrays.sort(sorted);
        return sorted[Math.min(size - 1, (int) (size * share))] / 1_000_000.0;
    }

    private long value(int phase, int index) {
        return phase < PHASES.length ? samples[phase][index] : total(index);
    }

    private long total(int index) {
        long sum = 0;
        for (long[] phase : samples) sum += phase[index];
        return sum;
    }
}
