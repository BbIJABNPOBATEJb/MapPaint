package me.bbijabnpobatejb.paintcraft.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MathUtil {

    public int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
