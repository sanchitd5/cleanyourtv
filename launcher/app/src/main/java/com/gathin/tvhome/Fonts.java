package com.gathin.tvhome;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * The one place that picks the launcher's typeface family: Outfit (the default, exactly as the layouts say) or
 * Inter (theme key {@code font}, on-TV "Font" row). The layouts keep naming Outfit; with Inter chosen, every
 * activity's inflater swaps each text view's Outfit resource for the matching Inter weight (see {@link #wrap}),
 * and {@link UiFont} does the same for views built in code. With Outfit nothing is wrapped and nothing changes.
 *
 * Inter is a little wider and its capitals are already open, so the tracking the layouts give Outfit is adjusted
 * ({@link #tracking}); clocks switch on tabular figures so the seconds do not make the time jitter.
 */
final class Fonts {
    static final String OUTFIT = Config.FONT_OUTFIT, INTER = Config.FONT_INTER;
    /** Caption tracking (em) for the shared small-caps style of HOME-DESIGN.md section 21: Outfit 0.12, Inter 0.08. */
    static final float CAPTION_OUTFIT = 0.12f, CAPTION_INTER = 0.08f;
    /** Inter's large titles (40 sp and up) are not tracked tighter than this (em). */
    static final float TITLE_FLOOR = -0.02f;
    /** The size (sp) from which a text counts as a title for {@link #tracking}. */
    static final float TITLE_SP = 40f;
    /** OpenType features for clocks: tabular figures (fixed-width digits) so a changing time does not move. */
    static final String CLOCK_FEATURES = "tnum";
    /** The on-TV choice's two values, as saved in launcher_settings under {@link Config#HOME_FONT_KEY}. */
    static final int CHOICE_OUTFIT = 0, CHOICE_INTER = 1;
    static final int[] CHOICES = {CHOICE_OUTFIT, CHOICE_INTER};

    private static volatile String current = OUTFIT;

    private Fonts() {}

    /** A known font (any case, trimmed), or outfit for anything else. */
    static String font(String value) {
        return value != null && INTER.equalsIgnoreCase(value.trim()) ? INTER : OUTFIT;
    }

    /** The font a theme value amounts to when the TV's own choice (absent is -1) is applied over it. */
    static String chosen(int choice, String theme) {
        return choice == CHOICE_INTER ? INTER : choice == CHOICE_OUTFIT ? OUTFIT : font(theme);
    }

    static int choiceOf(String font) {
        return INTER.equals(font(font)) ? CHOICE_INTER : CHOICE_OUTFIT;
    }

    /** Set by {@link Config#load}: the font views built in code ask for. */
    static void use(String font) {
        current = font(font);
    }

    static String current() {
        return current;
    }

    /**
     * The font resource that draws {@code outfitFont} (an R.font.outfit* id) in {@code font}; the id itself for
     * Outfit and for any resource that is not one of Outfit's.
     */
    static int resource(String font, int outfitFont) {
        if (!INTER.equals(font(font))) return outfitFont;
        if (outfitFont == R.font.outfit_light || outfitFont == R.font.outfit_extralight) return R.font.inter_light;
        if (outfitFont == R.font.outfit_medium) return R.font.inter_medium;
        if (outfitFont == R.font.outfit_regular || outfitFont == R.font.outfit) return R.font.inter_regular;
        return outfitFont;
    }

    /**
     * Letter-spacing (em) for a text the layout tracks at {@code outfitTracking}. Outfit keeps it. Inter: wide
     * caption tracking (0.10 and up) shrinks by a third (0.12 gives {@link #CAPTION_INTER}, 0.22 gives 0.147),
     * and a title of {@link #TITLE_SP} or more is not tracked tighter than {@link #TITLE_FLOOR}.
     */
    static float tracking(String font, float outfitTracking, float sizeSp) {
        if (!INTER.equals(font(font))) return outfitTracking;
        if (outfitTracking >= 0.10f) return outfitTracking * (CAPTION_INTER / CAPTION_OUTFIT);
        if (sizeSp >= TITLE_SP && outfitTracking < TITLE_FLOOR) return TITLE_FLOOR;
        return outfitTracking;
    }

    /** The caption tracking of section 21 for a font, for views the top bar builds in code. */
    static float captionTracking(String font) {
        return tracking(font, CAPTION_OUTFIT, 14f);
    }

    /** The font features a clock view wants in this font, or null for none (Outfit draws its digits as before). */
    static String clockFeatures(String font) {
        return INTER.equals(font(font)) ? CLOCK_FEATURES : null;
    }

    /** Applies {@link #clockFeatures} to a clock built in code. */
    static void applyClock(TextView view, String font) {
        String features = clockFeatures(font);
        if (features != null) view.setFontFeatureSettings(features);
    }

    /** {@code base}, with an inflater that draws Inter when {@code font} says so; base itself for Outfit. */
    static Context wrap(Context base, String font) {
        use(font);
        if (!INTER.equals(font(font))) return base;
        return new ContextWrapper(base) {
            private LayoutInflater inflater;

            @Override
            public Object getSystemService(String name) {
                if (!LAYOUT_INFLATER_SERVICE.equals(name)) return super.getSystemService(name);
                if (inflater == null) {
                    inflater = LayoutInflater.from(getBaseContext()).cloneInContext(this);
                    inflater.setFactory2(new InterFactory());
                }
                return inflater;
            }
        };
    }

    /** Creates each view as the inflater would, then moves a text view's Outfit font to Inter. */
    private static final class InterFactory implements LayoutInflater.Factory2 {
        private static final String[] PREFIXES = {"android.widget.", "android.view.", "android.webkit."};
        private static final int[] FONT_FAMILY = {android.R.attr.fontFamily};

        @Override
        public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
            LayoutInflater inflater = LayoutInflater.from(context);
            View view = null;
            try {
                if (name.indexOf('.') >= 0) {
                    view = inflater.createView(name, null, attrs);
                } else {
                    for (String prefix : PREFIXES) {
                        try {
                            view = inflater.createView(name, prefix, attrs);
                            break;
                        } catch (ClassNotFoundException ignored) {
                            // try the next package
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                return null;   // not ours to build: the inflater's own path reports it
            }
            if (view instanceof TextView) restyle((TextView) view, context, attrs);
            return view;
        }

        /** The Outfit resource whose typeface this is (the fallback when the attribute does not resolve to an id), or 0. */
        private static int outfitOf(Context context, android.graphics.Typeface face) {
            if (face == null) return 0;
            for (int id : new int[]{R.font.outfit_light, R.font.outfit_extralight, R.font.outfit_medium, R.font.outfit_regular}) {
                try {
                    if (face == context.getResources().getFont(id)) return id;
                } catch (RuntimeException ignored) {
                    // not loadable: leave the view as it is
                }
            }
            return 0;
        }

        @Override
        public View onCreateView(String name, Context context, AttributeSet attrs) {
            return onCreateView(null, name, context, attrs);
        }

        @android.annotation.SuppressLint("ResourceType")   // android.R.attr.fontFamily is a styleable attr id, lint can't tell
        private static void restyle(TextView text, Context context, AttributeSet attrs) {
            TypedArray a = context.obtainStyledAttributes(attrs, FONT_FAMILY, 0, 0);
            int family;
            try {
                family = a.getResourceId(0, 0);   // the font resource the string reference finally resolves to
            } finally {
                a.recycle();
            }
            int inter = resource(INTER, family);
            if (inter == family) inter = resource(INTER, outfitOf(context, text.getTypeface()));
            if (inter == 0 || inter == family) return;   // a system family (values-vi) or no font: left as it is
            text.setTypeface(UiFont.load(context, inter));
            float scaled = context.getResources().getDisplayMetrics().scaledDensity;
            float sizeSp = scaled > 0 ? text.getTextSize() / scaled : 14f;
            text.setLetterSpacing(tracking(INTER, text.getLetterSpacing(), sizeSp));
            if (text instanceof TextClock) applyClock(text, INTER);
        }
    }
}
