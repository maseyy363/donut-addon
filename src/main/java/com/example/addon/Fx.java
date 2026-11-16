package com.example.addon.modules;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/** Small visual helpers. */
public final class Fx {
    private Fx() {}

    /** Same colour with a gently pulsing alpha (so beams and boxes are easier to spot). */
    public static SettingColor pulse(SettingColor c, boolean on) {
        if (!on) return c;
        double k = 0.65 + 0.35 * Math.sin(System.currentTimeMillis() / 350.0);
        return new SettingColor(c.r, c.g, c.b, (int) (c.a * k));
    }
}
