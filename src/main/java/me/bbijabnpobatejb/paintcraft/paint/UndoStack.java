package me.bbijabnpobatejb.paintcraft.paint;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;

public final class UndoStack {

    public static final class Step {
        private int[] index = new int[64];
        private byte[] color = new byte[64];
        private int size;
        private BitSet seen = new BitSet();
        public int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        public byte[] fullPixels;
        public int fullRes;
        private int fullSize;
        public boolean flipsImported;

        public static Step resolution(int res, byte[] pixels) {
            Step step = new Step();
            step.fullPixels = pixels;
            step.fullRes = res;
            step.fullSize = pixels.length;
            return step;
        }

        public boolean isResolution() {
            return fullPixels != null;
        }

        public void remember(int idx, byte old, int x, int y) {
            if (seen.get(idx)) {
                return;
            }
            seen.set(idx);
            if (size == index.length) {
                index = Arrays.copyOf(index, size * 2);
                color = Arrays.copyOf(color, size * 2);
            }
            index[size] = idx;
            color[size] = old;
            size++;
            grow(x, y);
        }

        public void grow(int x, int y) {
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }

        public boolean isEmpty() {
            return size == 0 && fullPixels == null;
        }

        public int size() {
            return fullPixels != null ? fullSize : size;
        }

        public void swap(byte[] pixels) {
            if (fullPixels != null) return;
            for (int i = 0; i < size; i++) {
                byte current = pixels[index[i]];
                pixels[index[i]] = color[i];
                color[i] = current;
            }
        }

        public void revertAndClear(byte[] pixels) {
            for (int i = 0; i < size; i++) {
                pixels[index[i]] = color[i];
            }
            size = 0;
            seen.clear();
            minX = minY = Integer.MAX_VALUE;
            maxX = maxY = -1;
        }

        public void seal() {
            seen = null;
            index = Arrays.copyOf(index, size);
            color = Arrays.copyOf(color, size);
        }
    }

    private final ArrayDeque<Step> undo = new ArrayDeque<>();
    private final ArrayDeque<Step> redo = new ArrayDeque<>();
    private final int maxSteps;
    private final int maxPixels;
    private int pixels;

    public UndoStack(int maxSteps, int maxPixels) {
        this.maxSteps = maxSteps;
        this.maxPixels = maxPixels;
    }

    public void push(Step step) {
        if (step.isEmpty()) {
            return;
        }
        step.seal();
        undo.addLast(step);
        pixels += step.size();
        redo.clear();
        while (undo.size() > maxSteps || (pixels > maxPixels && undo.size() > 1)) {
            pixels -= undo.removeFirst().size();
        }
    }

    public Step undo(byte[] data) {
        Step step = undo.pollLast();
        if (step == null) {
            return null;
        }
        pixels -= step.size();
        step.swap(data);
        redo.addLast(step);
        return step;
    }

    public Step redo(byte[] data) {
        Step step = redo.pollLast();
        if (step == null) {
            return null;
        }
        step.swap(data);
        undo.addLast(step);
        pixels += step.size();
        return step;
    }

    public Step peek() {
        return undo.peekLast();
    }

    public void dropLast() {
        Step step = undo.pollLast();
        if (step != null) pixels -= step.size();
    }

    public void dropRedo() {
        redo.clear();
    }

    public void clear() {
        undo.clear();
        redo.clear();
        pixels = 0;
    }
}
