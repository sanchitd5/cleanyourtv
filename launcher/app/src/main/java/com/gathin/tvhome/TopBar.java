package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.format.DateFormat;
import android.text.method.TransformationMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Locale;

/**
 * The `split` and `tvos` top bars (HOME-DESIGN.md section 21). The classic header is laid out as ever; this restyles
 * its views once, after the first layout has told it the classic height, and then places each one by a static
 * translation derived from measured baselines and ink bounds (never margins), so no view is re-parented and the whole bar
 * still drifts and dims as one unit with the header container ({@link HomeIdle}). Returns null for `classic`.
 */
final class TopBar {
    private static final String TAG = "TopBar";
    /** Caption tracking (em): Outfit 0.12; Inter wants 0.08 (21.8). The one place it is set. */
    static final float CAPTION_TRACKING = 0.12f;
    private static final int CAPTION_INK = 0xE6FFFFFF;
    /** Longest the date and the greeting may be, dp: 470 px at density 2. */
    private static final int LINE_MAX_DP = 235;

    private final Config cfg;
    private final float d;
    private final boolean tvos;
    private final String mode;
    private final View root, header, leftBlock;
    private final TextView clock, ampm, date, greeting;
    private final View divider;
    private final LinearLayout buttons;
    private final int screenW;
    private final Rect rc = new Rect(), ra = new Rect(), rd = new Rect(), rg = new Rect(), rh = new Rect();

    private boolean styled, probed;
    private float scaleX = 1f;

    private TopBar(Activity a, Config cfg, float density, String mode) {
        this.cfg = cfg;
        this.d = density;
        this.mode = mode;
        this.tvos = Config.TOP_BAR_MODE_TVOS.equals(mode);
        root = a.findViewById(R.id.root);
        leftBlock = a.findViewById(R.id.leftBlock);
        header = (View) leftBlock.getParent();
        clock = a.findViewById(R.id.clock);
        ampm = a.findViewById(R.id.ampm);
        date = a.findViewById(R.id.date);
        greeting = a.findViewById(R.id.greeting);
        divider = a.findViewById(R.id.clockDivider);
        buttons = a.findViewById(R.id.topButtons);
        screenW = a.getResources().getDisplayMetrics().widthPixels;
    }

    /** Null for classic, or when the layout cannot be used with this header (right-to-left menus, Shamsi or lunar dates). */
    static TopBar attach(Activity a, Config cfg, float density) {
        String mode = TopBarLayout.mode(cfg.topBarLayout, a.getResources().getBoolean(R.bool.rtl_menu_bar), cfg.shamsiDate, cfg.lunarDate);
        if (Config.TOP_BAR_MODE_CLASSIC.equals(mode)) return null;
        TopBar bar = new TopBar(a, cfg, density, mode);
        bar.header.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                bar.header.getViewTreeObserver().removeOnPreDrawListener(this);
                bar.style();
                return false;   // the restyle asked for another layout; draw only after it
            }
        });
        return bar;
    }

    // ---- once: styles and sizes, after the classic layout has been measured ----

    private void style() {
        // The classic left block's height is what the dock and the grid below were laid out against: keep it.
        leftBlock.setMinimumHeight(leftBlock.getHeight());
        boolean greetingShown = greeting.getVisibility() != View.GONE;
        if (greetingShown) {
            ViewGroup.LayoutParams lp = greeting.getLayoutParams();
            lp.height = greeting.getHeight();
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            greeting.setLayoutParams(lp);
            caption(greeting, 14);
            greeting.setMaxWidth(Math.round(LINE_MAX_DP * d));
        }
        for (View v : new View[]{clock, ampm, date, greeting, divider}) unclip(v);

        int ink = cfg.headerWhite ? 0xFFFFFFFF : cfg.topShelf ? CAPTION_INK : date.getCurrentTextColor();
        ViewGroup.LayoutParams dlp = date.getLayoutParams();
        dlp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        date.setLayoutParams(dlp);
        date.setMaxLines(1);
        date.setLineSpacing(0f, 1f);
        date.setMaxWidth(Math.round(LINE_MAX_DP * d));
        caption(date, 14);
        date.setTextColor(ink);
        if (tvos) {
            if (!cfg.topBarDate) date.setVisibility(View.GONE);
            clock.setTypeface(ampm.getTypeface());
            clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            clock.setLetterSpacing(0f);
            Fonts.applyClock(clock, cfg.font);   // tabular figures under Inter, so the time never jitters
            clock.setTextColor(0xFFF5F7FA);
            ampm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            ampm.setLetterSpacing(0.08f);
            ampm.setTextColor(0xC7FFFFFF);
            divider.setVisibility(View.GONE);
            styleCapsule();
        } else {
            String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM");
            ((TextClock) date).setFormat12Hour(pattern);
            ((TextClock) date).setFormat24Hour(pattern);
            caption(ampm, 14);
            ampm.setTextColor(ink);
            ViewGroup.LayoutParams lp = divider.getLayoutParams();
            lp.width = Math.max(1, Math.round(d));
            divider.setLayoutParams(lp);
            divider.setBackgroundColor(cfg.headerWhite ? 0xFFFFFFFF : 0x4DFFFFFF);
        }
        styled = true;
        View.OnLayoutChangeListener again = (v, l, t, r, b, ol, ot, or, ob) -> place();
        header.addOnLayoutChangeListener(again);
        for (View v : new View[]{clock, ampm, date, greeting, buttons}) v.addOnLayoutChangeListener(again);
    }

    private void caption(TextView v, float sp) {
        v.setTypeface(ampm.getTypeface());
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setLetterSpacing(Fonts.captionTracking(cfg.font));   // Outfit CAPTION_TRACKING (0.12), Inter 0.08 (21.8)
    }

    /** The capsule: items of 36 dp with a 4 dp gap, no resting background of their own; the VPN pill keeps its own. */
    private void styleCapsule() {
        buttons.setBackgroundResource(R.drawable.top_capsule_bg);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(4 * d);
        buttons.setPadding(0, pad, 0, pad);
        // The time is drawn over the capsule's trailing end; the clock row must paint above the capsule.
        leftBlock.setTranslationZ(1f);
        for (int i = 0; i < buttons.getChildCount(); i++) {
            View b = buttons.getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
            lp.height = Math.round(36 * d);
            lp.setMarginStart(pad);
            boolean iconOnly = !(b instanceof TextView) || ((TextView) b).getText().length() == 0;
            if (iconOnly) {
                lp.width = Math.round(36 * d);
                b.setPaddingRelative(Math.round(7 * d), 0, Math.round(7 * d), 0);
            } else {
                b.setPaddingRelative(Math.round(12 * d), 0, Math.round(14 * d), 0);
            }
            if (b.getId() != R.id.btnVpn) b.setBackgroundResource(R.drawable.top_capsule_item_bg);
            b.setLayoutParams(lp);
        }
    }

    /** Lets translated views draw outside their parents' bounds, up to the header container. */
    private void unclip(View v) {
        for (View p = (View) v.getParent(); p != null && p != header.getParent(); p = (View) p.getParent()) {
            if (p instanceof ViewGroup) ((ViewGroup) p).setClipChildren(false);
            if (p == header) break;
        }
    }

    // ---- on every layout: translations from measured bounds ----

    private int layoutX(View v) {
        int x = 0;
        for (View p = v; p != root; p = (View) p.getParent()) x += p.getLeft();
        return x;
    }

    private int layoutY(View v) {
        int y = 0;
        for (View p = v; p != root; p = (View) p.getParent()) y += p.getTop();
        return y;
    }

    /** The text as drawn (all caps applied). */
    private static String shown(TextView v) {
        CharSequence t = v.getText();
        TransformationMethod tm = v.getTransformationMethod();
        return (tm == null ? t : tm.getTransformation(t, v)).toString();
    }

    private static void ink(TextView v, Rect out) {
        String s = shown(v);
        if (s.isEmpty()) { out.set(0, 0, 0, 0); return; }
        v.getPaint().getTextBounds(s, 0, s.length(), out);
    }

    private static int cap(TextView v, String glyph) {
        Rect r = new Rect();
        v.getPaint().getTextBounds(glyph, 0, glyph.length(), r);
        return r.height();
    }

    /** Puts v's ink left edge on x and its baseline on y (screen px). */
    private void put(TextView v, Rect ink, int x, int baseline) {
        setTranslation(v, x - (layoutX(v) + v.getCompoundPaddingLeft() + ink.left), baseline - (layoutY(v) + v.getBaseline()));
    }

    private static void setTranslation(View v, float x, float y) {
        if (v.getTranslationX() != x) v.setTranslationX(x);
        if (v.getTranslationY() != y) v.setTranslationY(y);
    }

    private void place() {
        if (!styled || clock.getWidth() == 0) return;
        int centre = TopBarLayout.centreY(mode, d), m = TopBarLayout.marginX(d), right = screenW - m;
        ink(clock, rc);
        ink(ampm, ra);
        ink(date, rd);
        ink(greeting, rg);
        int clockCap = cap(clock, "1"), captionCap = cap(date, "H");
        probe(clockCap, captionCap);
        int greetingBase = TopBarLayout.greetingBaselineY(mode, cfg.topBarDate, d);
        if (greeting.getVisibility() != View.GONE) put(greeting, rg, m, greetingBase);

        if (tvos) {
            int ampmW = ra.width(), clockW = rc.width(), end = Math.round(16 * d), gap = Math.round(4 * d);
            int ampmX = right - end - ampmW;
            int clockX = (ampmW > 0 ? ampmX - gap : right - end) - clockW;
            int base = TopBarLayout.baselineY(centre, clockCap);
            put(clock, rc, clockX, base);
            put(ampm, ra, ampmX, base);
            if (date.getVisibility() != View.GONE) put(date, rd, m, TopBarLayout.baselineY(centre, captionCap));
            int padEnd = Math.round(10 * d) + (right - clockX);
            if (buttons.getPaddingRight() != padEnd) buttons.setPadding(0, buttons.getPaddingTop(), padEnd, buttons.getPaddingBottom());
        } else {
            int base = TopBarLayout.baselineY(centre, clockCap);
            int dateMax = TopBarLayout.dateMaxWidth(0, base, TopBarLayout.MAX_SCALE_X, d);
            TopBarLayout.SplitRow row = TopBarLayout.splitRow(m, rc.width(), ra.width(), rd.width(), d);
            dateMax = Math.max(0, dateMax - row.dateX);
            if (date.getMaxWidth() != dateMax) date.setMaxWidth(dateMax);
            int dateW = Math.min(rd.width(), dateMax);
            row = TopBarLayout.splitRow(m, rc.width(), ra.width(), dateW, d);
            put(clock, rc, m, base);
            put(ampm, ra, row.ampmX, base);
            put(date, rd, row.dateX, base);
            ViewGroup.LayoutParams lp = divider.getLayoutParams();
            if (lp.height != clockCap) { lp.height = clockCap; divider.setLayoutParams(lp); }
            setTranslation(divider, row.sepX - layoutX(divider), base - clockCap - layoutY(divider));
            fitScrim(row.end, base, greetingBase);
        }
        setTranslation(buttons, right - (layoutX(buttons) + buttons.getWidth()), centre - (layoutY(buttons) + buttons.getHeight() / 2f));
    }

    /** split: the corner scrim is stretched until the date and the greeting end inside its 4.5:1 zone (21.6). */
    private void fitScrim(int dateEnd, int base, int greetingBase) {
        int dx = Math.round(TopBarLayout.DRIFT_X_DP * d), dy = Math.round(TopBarLayout.DRIFT_Y_DP * d);
        float s = TopBarLayout.cornerScaleX(dateEnd + dx, base + dy,
                TopBarLayout.marginX(d) + Math.round(LINE_MAX_DP * d) + dx, greetingBase + dy);
        s = (float) Math.ceil(s * 100f) / 100f;   // whole hundredths: a new bitmap only when the need really moves
        if (Math.abs(s - scaleX) < 0.005f || cfg.topShelfScrim <= 0) return;
        Drawable bg = root.findViewById(R.id.heroScrim).getBackground();
        if (!(bg instanceof LayerDrawable) || ((LayerDrawable) bg).getNumberOfLayers() < 2) return;
        scaleX = s;
        ((LayerDrawable) bg).setDrawable(1, ShelfScrim.corner(root.getResources(), cfg.topShelfScrim, false, s));
    }

    /** Debug (removed before T7): the measured cap heights, bearings and widths, once, for `adb logcat -s TopBar`. */
    private void probe(int clockCap, int captionCap) {
        if (probed || shown(clock).isEmpty() || shown(date).isEmpty()) return;
        probed = true;
        Log.d(TAG, "probe mode=" + mode + " density=" + d + " clock='" + shown(clock) + "' cap=" + clockCap + " ink=" + rc
                + " ampm='" + shown(ampm) + "' ink=" + ra + " date='" + shown(date) + "' cap=" + captionCap + " ink=" + rd
                + " greeting='" + shown(greeting) + "' ink=" + rg);
    }
}
