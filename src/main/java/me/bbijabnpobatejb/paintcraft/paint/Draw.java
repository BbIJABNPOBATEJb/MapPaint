package me.bbijabnpobatejb.paintcraft.paint;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public final class Draw {

    private static final int[][] DABS = new int[65][];

    private final byte[] pixels;
    private final int width;
    private final int height;
    private final UndoStack.Step step;
    public boolean square;
    public int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;

    public Draw(byte[] pixels, int width, int height, UndoStack.Step step) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.step = step;
    }

    public boolean changed() {
        return maxX >= 0;
    }

    public void set(int x, int y, byte color) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        int index = y * width + x;
        byte old = pixels[index];
        if (old == color) {
            return;
        }
        step.remember(index, old, x, y);
        pixels[index] = color;
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
    }

    public void dab(int cx, int cy, int size, byte color) {
        if (size <= 1) {
            set(cx, cy, color);
            return;
        }
        if (cx + size < 0 || cy + size < 0 || cx - size >= width || cy - size >= height) {
            return;
        }
        if (square) {
            int shift = (size - 1) / 2;
            bar(cx - shift, cy - shift, cx - shift + size - 1, cy - shift + size - 1, color);
            return;
        }
        int[] offsets = dabOffsets(size);
        for (int i = 0; i < offsets.length; i += 2) {
            set(cx + offsets[i], cy + offsets[i + 1], color);
        }
    }

    public void bar(int x0, int y0, int x1, int y1, byte color) {
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(width - 1, x1);
        y1 = Math.min(height - 1, y1);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, color);
            }
        }
    }

    private static int[] dabOffsets(int size) {
        size = Math.min(size, DABS.length - 1);
        int[] offsets = DABS[size];
        if (offsets == null) {
            double r = size / 2.0;
            int shift = (size - 1) / 2;
            int[] buffer = new int[size * size * 2];
            int n = 0;
            for (int dy = 0; dy < size; dy++) {
                for (int dx = 0; dx < size; dx++) {
                    double px = dx + 0.5 - r, py = dy + 0.5 - r;
                    if (px * px + py * py <= r * r) {
                        buffer[n++] = dx - shift;
                        buffer[n++] = dy - shift;
                    }
                }
            }
            offsets = DABS[size] = Arrays.copyOf(buffer, n);
        }
        return offsets;
    }

    public void line(int x0, int y0, int x1, int y1, int size, byte color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;
        while (true) {
            dab(x0, y0, size, color);
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int doubled = error * 2;
            if (doubled > -dy) {
                error -= dy;
                x0 += sx;
            }
            if (doubled < dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    public void rect(int x0, int y0, int x1, int y1, int size, byte color) {
        int left = Math.min(x0, x1), right = Math.max(x0, x1), top = Math.min(y0, y1), bottom = Math.max(y0, y1);
        int before = (size - 1) / 2, after = size - 1 - before;
        bar(left - before, top - before, right + after, top + after, color);
        bar(left - before, bottom - before, right + after, bottom + after, color);
        bar(left - before, top - before, left + after, bottom + after, color);
        bar(right - before, top - before, right + after, bottom + after, color);
    }

    public void ellipse(int x0, int y0, int x1, int y1, int size, byte color) {
        long a = Math.abs(x1 - x0), b = Math.abs(y1 - y0);
        long odd = b & 1;
        long dx = 4 * (1 - a) * b * b, dy = 4 * (odd + 1) * a * a;
        long error = dx + dy + odd * a * a;
        if (x0 > x1) {
            x0 = x1;
            x1 += (int) a;
        }
        if (y0 > y1) {
            y0 = y1;
        }
        y0 += (int) ((b + 1) / 2);
        y1 = y0 - (int) odd;
        long stepA = 8 * a * a, stepB = 8 * b * b;
        do {
            dab(x1, y0, size, color);
            dab(x0, y0, size, color);
            dab(x0, y1, size, color);
            dab(x1, y1, size, color);
            long doubled = 2 * error;
            if (doubled <= dy) {
                y0++;
                y1--;
                error += dy += stepA;
            }
            if (doubled >= dx || 2 * error > dy) {
                x0++;
                x1--;
                error += dx += stepB;
            }
        } while (x0 <= x1);
        while (y0 - y1 < b) {
            dab(x0 - 1, y0, size, color);
            dab(x1 + 1, y0++, size, color);
            dab(x0 - 1, y1, size, color);
            dab(x1 + 1, y1--, size, color);
        }
    }

    public void fill(int x, int y, byte color) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        byte target = pixels[y * width + x];
        if (target == color) {
            return;
        }
        int[] stack = new int[1024];
        int top = 0;
        set(x, y, color);
        stack[top++] = y * width + x;
        while (top > 0) {
            int index = stack[--top];
            int px = index % width, py = index / width;
            if (top + 4 > stack.length) {
                stack = Arrays.copyOf(stack, stack.length * 2);
            }
            if (px > 0 && pixels[index - 1] == target) {
                set(px - 1, py, color);
                stack[top++] = index - 1;
            }
            if (px < width - 1 && pixels[index + 1] == target) {
                set(px + 1, py, color);
                stack[top++] = index + 1;
            }
            if (py > 0 && pixels[index - width] == target) {
                set(px, py - 1, color);
                stack[top++] = index - width;
            }
            if (py < height - 1 && pixels[index + width] == target) {
                set(px, py + 1, color);
                stack[top++] = index + width;
            }
        }
    }

    public void spray(int cx, int cy, int size, byte color) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double radius = Math.max(1.5, size * 1.5);
        int dots = Math.max(2, (int) (radius * radius * 0.12));
        for (int i = 0; i < dots; i++) {
            double angle = random.nextDouble(Math.PI * 2), distance = Math.sqrt(random.nextDouble()) * radius;
            set(cx + (int) Math.round(Math.cos(angle) * distance), cy + (int) Math.round(Math.sin(angle) * distance), color);
        }
    }
}
