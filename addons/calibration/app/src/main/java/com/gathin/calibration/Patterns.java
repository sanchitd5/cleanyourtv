package com.gathin.calibration;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.util.Locale;

/** The picture patterns, drawn pixel for pixel into a canvas of the display's native size. */
final class Patterns {
    private Patterns() {}

    static final String[] IDS = {
        "pluge_limited", "pluge_full", "white_clip", "ramp", "bars75", "bars100", "sat_rgb", "sat_cmy",
        "sharp", "gamma", "grey5", "grey50", "grey100", "red", "green", "blue", "motion"};
    static final int[] TITLES = {
        R.string.p_pluge_limited_title, R.string.p_pluge_full_title, R.string.p_white_clip_title,
        R.string.p_ramp_title, R.string.p_bars75_title, R.string.p_bars100_title, R.string.p_sat_rgb_title,
        R.string.p_sat_cmy_title, R.string.p_sharp_title, R.string.p_gamma_title, R.string.p_grey5_title,
        R.string.p_grey50_title, R.string.p_grey100_title, R.string.p_red_title, R.string.p_green_title,
        R.string.p_blue_title, R.string.p_motion_title};
    static final int[] HELPS = {
        R.string.p_pluge_limited_help, R.string.p_pluge_full_help, R.string.p_white_clip_help,
        R.string.p_ramp_help, R.string.p_bars75_help, R.string.p_bars100_help, R.string.p_sat_rgb_help,
        R.string.p_sat_cmy_help, R.string.p_sharp_help, R.string.p_gamma_help, R.string.p_grey5_help,
        R.string.p_grey50_help, R.string.p_grey100_help, R.string.p_red_help, R.string.p_green_help,
        R.string.p_blue_help, R.string.p_motion_help};

    static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    static boolean animated(int index) { return "motion".equals(IDS[index]); }

    private static final Paint FILL = new Paint();   // no anti-aliasing: every edge lands on a pixel

    private static int rgb(int[] c) { return Color.rgb(c[0], c[1], c[2]); }

    private static int grey(int code) { return Color.rgb(code, code, code); }

    private static void rect(Canvas c, float l, float t, float r, float b, int color) {
        FILL.setColor(color);
        c.drawRect(Math.round(l), Math.round(t), Math.round(r), Math.round(b), FILL);
    }

    /** A paint that repeats a tiny bitmap, unfiltered, so a 1-pixel pattern stays 1 pixel. */
    private static Paint tile(int tw, int th, int... argb) {
        Bitmap bm = Bitmap.createBitmap(argb, tw, th, Bitmap.Config.ARGB_8888);
        Paint p = new Paint();
        p.setShader(new BitmapShader(bm, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        p.setFilterBitmap(false);
        return p;
    }

    private static void label(Canvas c, String text, float cx, float y, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, cx, y, p);
    }

    /** Draws pattern {@code index} filling {@code w} x {@code h}; {@code timeMs} drives the moving one. */
    static void draw(Canvas c, int w, int h, int index, boolean limited, long timeMs) {
        int black = Levels.black(limited), white = Levels.white(limited);
        c.drawColor(grey(black));
        float ts = h / 45f;
        int dim = grey(Levels.grey(45, limited));
        switch (IDS[index]) {
            case "pluge_limited": pluge(c, w, h, Levels.PLUGE_LIMITED, 16, ts, dim); break;
            case "pluge_full": pluge(c, w, h, Levels.PLUGE_FULL, 0, ts, dim); break;
            case "white_clip": {
                c.drawColor(grey(16));
                int[] codes = Levels.whiteClipCodes();
                float bw = w * 0.9f / codes.length, x0 = w * 0.05f;
                for (int i = 0; i < codes.length; i++) {
                    rect(c, x0 + i * bw, h * 0.2f, x0 + (i + 1) * bw, h * 0.8f, grey(codes[i]));
                    label(c, String.valueOf(codes[i]), x0 + (i + 0.5f) * bw, h * 0.86f, ts * 0.8f, dim);
                }
                break;
            }
            case "ramp": ramp(c, w, h, limited, ts, dim); break;
            case "bars75": bars(c, w, h, 0.75, limited); break;
            case "bars100": bars(c, w, h, 1.0, limited); break;
            case "sat_rgb": saturation(c, w, h, new int[]{Levels.R, Levels.G, Levels.B}, limited, ts); break;
            case "sat_cmy": saturation(c, w, h, new int[]{Levels.G | Levels.B, Levels.R | Levels.B,
                Levels.R | Levels.G}, limited, ts); break;
            case "sharp": sharp(c, w, h, limited, ts); break;
            case "gamma": gamma(c, w, h, limited, ts, dim); break;
            case "grey5": c.drawColor(grey(Levels.grey(5, limited))); break;
            case "grey50": c.drawColor(grey(Levels.grey(50, limited))); break;
            case "grey100": c.drawColor(grey(white)); break;
            case "red": c.drawColor(rgb(Levels.bar(Levels.R, 1, limited))); break;
            case "green": c.drawColor(rgb(Levels.bar(Levels.G, 1, limited))); break;
            case "blue": c.drawColor(rgb(Levels.bar(Levels.B, 1, limited))); break;
            case "motion": motion(c, w, h, limited, timeMs, ts); break;
            default: break;
        }
    }

    private static void pluge(Canvas c, int w, int h, int[] bars, int background, float ts, int dim) {
        c.drawColor(grey(background));
        float bw = w / (bars.length * 2f);
        float x0 = (w - (bars.length * 2 - 1) * bw) / 2;
        for (int i = 0; i < bars.length; i++) {
            float x = x0 + i * 2 * bw;
            rect(c, x, h * 0.2f, x + bw, h * 0.8f, grey(bars[i]));
            label(c, String.valueOf(bars[i]), x + bw / 2, h * 0.86f, ts * 0.8f, dim);
        }
        label(c, "background " + background, w / 2f, h * 0.14f, ts, dim);
    }

    private static void ramp(Canvas c, int w, int h, boolean limited, float ts, int dim) {
        int lo = Levels.black(limited), n = Levels.white(limited) - lo + 1;
        for (int i = 0; i < n; i++) rect(c, (float) i * w / n, 0, (float) (i + 1) * w / n, h * 0.5f, grey(lo + i));
        for (int i = 0; i < 11; i++) {
            float x0 = i * w / 11f, x1 = (i + 1) * w / 11f;
            rect(c, x0, h * 0.5f, x1, h, grey(Levels.grey(i * 10, limited)));
            label(c, i * 10 + "%", (x0 + x1) / 2, h * 0.95f, ts, i < 5 ? grey(Levels.grey(80, limited))
                : grey(Levels.grey(10, limited)));
        }
    }

    private static void bars(Canvas c, int w, int h, double amplitude, boolean limited) {
        int n = Levels.BAR_MASKS.length;
        for (int i = 0; i < n; i++) {
            rect(c, (float) i * w / n, 0, (float) (i + 1) * w / n, h * 0.75f,
                rgb(Levels.bar(Levels.BAR_MASKS[i], amplitude, limited)));
        }
        for (int i = 0; i < 5; i++) {
            rect(c, (float) i * w / 5, h * 0.75f, (float) (i + 1) * w / 5, h, grey(Levels.grey(i * 25, limited)));
        }
    }

    private static void saturation(Canvas c, int w, int h, int[] masks, boolean limited, float ts) {
        int steps = 6;
        for (int r = 0; r < masks.length; r++) {
            for (int s = 0; s < steps; s++) {
                float x0 = (float) s * w / steps, x1 = (float) (s + 1) * w / steps;
                rect(c, x0, (float) r * h / masks.length, x1, (float) (r + 1) * h / masks.length,
                    rgb(Levels.saturation(masks[r], s, steps, limited)));
            }
        }
        for (int s = 0; s < steps; s++) {
            label(c, s * 20 + "%", (s + 0.5f) * w / steps, h - ts, ts, grey(Levels.grey(100, limited)));
        }
    }

    private static void sharp(Canvas c, int w, int h, boolean limited, float ts) {
        int on = grey(Levels.white(limited)), off = grey(Levels.black(limited));
        int top = Math.round(h * 0.1f), bottom = Math.round(h * 0.9f);
        float third = w * 0.9f / 3, x0 = w * 0.05f;
        Paint vertical = tile(2, 1, on, off), horizontal = tile(1, 2, on, off), checker = tile(2, 2, on, off, off, on);
        Paint[] paints = {vertical, horizontal, checker};
        for (int i = 0; i < 3; i++) {
            c.save();
            c.clipRect(Math.round(x0 + i * third + 4), top, Math.round(x0 + (i + 1) * third - 4), bottom);
            c.drawRect(0, 0, w, h, paints[i]);
            c.restore();
        }
        // Crosshair and circle, one pixel wide, over the middle of the screen.
        FILL.setColor(on);
        c.drawRect(0, h / 2, w, h / 2 + 1, FILL);
        c.drawRect(w / 2, 0, w / 2 + 1, h, FILL);
        Paint ring = new Paint();
        ring.setColor(on);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1);
        c.drawCircle(w / 2f, h / 2f, h / 4f, ring);
        // A border on the very edge: all four lines visible means no overscan.
        FILL.setColor(on);
        c.drawRect(0, 0, w, 1, FILL);
        c.drawRect(0, h - 1, w, h, FILL);
        c.drawRect(0, 0, 1, h, FILL);
        c.drawRect(w - 1, 0, w, h, FILL);
        int dim = grey(Levels.grey(60, limited));
        label(c, "lines 1:1", x0 + third / 2, top - ts, ts, dim);
        label(c, "lines 1:1", x0 + third * 1.5f, top - ts, ts, dim);
        label(c, "checker 1:1", x0 + third * 2.5f, top - ts, ts, dim);
    }

    private static void gamma(Canvas c, int w, int h, boolean limited, float ts, int dim) {
        int on = grey(Levels.white(limited)), off = grey(Levels.black(limited));
        Paint checker = tile(2, 2, on, off, off, on);
        int n = Levels.GAMMAS.length;
        for (int i = 0; i < n; i++) {
            int l = Math.round((float) i * w / n), r = Math.round((float) (i + 1) * w / n);
            c.save();
            c.clipRect(l + 6, Math.round(h * 0.15f), r - 6, Math.round(h * 0.8f));
            c.drawRect(0, 0, w, h, checker);
            c.restore();
            float cx = (l + r) / 2f, cy = h * 0.475f, s = (r - l) * 0.22f;
            rect(c, cx - s, cy - s, cx + s, cy + s, grey(Levels.gammaPatch(Levels.GAMMAS[i], limited)));
            label(c, String.format(Locale.US, "%.1f", Levels.GAMMAS[i]), cx, h * 0.87f, ts * 1.4f, dim);
        }
    }

    private static void motion(Canvas c, int w, int h, boolean limited, long timeMs, float ts) {
        int on = grey(Levels.white(limited)), bg = grey(Levels.grey(20, limited));
        c.drawColor(bg);
        float size = h / 8f, speed = w / 2.5f;
        float span = w + size;
        // Row 1: a white square, crossing in 2.5 s.
        float x = (timeMs / 1000f * speed) % span - size;
        rect(c, x, h * 0.1f, x + size, h * 0.1f + size, on);
        // Row 2: the same square, still, to compare edges.
        rect(c, w / 2f - size / 2, h * 0.3f, w / 2f + size / 2, h * 0.3f + size, on);
        // Row 3: slow 4-pixel stripes, a quarter of the speed.
        float x2 = (timeMs / 1000f * speed / 4) % span - size * 2;
        c.save();
        c.clipRect(Math.round(Math.max(0, x2)), Math.round(h * 0.5f),
            Math.round(Math.min(w, x2 + size * 2)), Math.round(h * 0.5f + size));
        c.drawRect(0, 0, w, h, tile(8, 1, on, on, on, on, bg, bg, bg, bg));
        c.restore();
        // Row 4: text that rolls at full speed, for judging blur and judder on edges.
        float x3 = (timeMs / 1000f * speed) % (w + size * 4) - size * 4;
        label(c, "MOTION 0123", x3 + size * 2, h * 0.85f, h / 10f, on);
        label(c, "same speed, text and square: judder, blur or soap-opera look?", w / 2f, h - ts, ts, grey(Levels.grey(60, limited)));
    }

    /** The help panel over the lower part of the picture. */
    static void overlay(Canvas c, int w, int h, String title, String help, String footer) {
        float size = h / 36f;
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.WHITE);
        tp.setTextSize(size);
        int width = (int) (w * 0.8f);
        StaticLayout body = StaticLayout.Builder.obtain(help, 0, help.length(), tp, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
        float pad = size, head = size * 1.6f, foot = size * 1.6f;
        float panelH = body.getHeight() + head + foot + pad * 2;
        float left = (w - width) / 2f - pad, top = h - panelH - h * 0.03f;
        Paint bg = new Paint();
        bg.setColor(Color.argb(215, 0, 0, 0));
        c.drawRect(left, top, left + width + pad * 2, top + panelH, bg);
        Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
        hp.setColor(Color.rgb(255, 200, 80));
        hp.setTextSize(size * 1.25f);
        hp.setFakeBoldText(true);
        c.drawText(title, left + pad, top + pad + size * 1.2f, hp);
        c.save();
        c.translate(left + pad, top + pad + head);
        body.draw(c);
        c.restore();
        Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
        fp.setColor(Color.rgb(170, 170, 170));
        fp.setTextSize(size * 0.8f);
        c.drawText(footer, left + pad, top + panelH - pad * 0.7f, fp);
    }
}
