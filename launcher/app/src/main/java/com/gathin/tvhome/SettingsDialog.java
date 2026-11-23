package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.IntConsumer;

/**
 * The glass dialog that replaces the settings' AlertDialogs when glass is on (HOME-DESIGN.md 19.6): a 27 dp glass panel, a 20 sp
 * title, a list of chip rows (the current choice has the ice rim and a check) and capsule buttons. It opens and closes with a
 * {@link GlassMotionController}: the open spring, the 110 ms Back fade, row focus scale. Use only while {@link Glass#on()}.
 */
final class SettingsDialog {
    private final Activity act;
    private final float dp;
    private final LinearLayout panel;
    private final LinearLayout list;
    private final LinearLayout buttons;
    private final Dialog dialog;
    private GlassMotionController motion;
    private View initialFocus;
    private boolean closing;

    private SettingsDialog(Activity act, CharSequence title, CharSequence message) {
        this.act = act;
        dp = act.getResources().getDisplayMetrics().density;
        panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * dp);
        panel.setPadding(pad, pad, pad, Math.round(18 * dp));
        panel.addView(label(title, 20f, R.string.font_medium, 0xFFF5F7FA));
        if (message != null) {
            TextView m = label(message, 14f, R.string.font_regular, 0xC7FFFFFF);
            ((LinearLayout.LayoutParams) m.getLayoutParams()).topMargin = Math.round(10 * dp);
            panel.addView(m);
        }
        final int maxHeight = Math.round(act.getResources().getDisplayMetrics().heightPixels * 0.55f);
        ScrollView scroll = new ScrollView(act) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Math.round(12 * dp);
        panel.addView(scroll, sp);
        buttons = new LinearLayout(act);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Math.round(14 * dp);
        panel.addView(buttons, bp);
        if (act.getResources().getBoolean(R.bool.rtl_menu_bar)) panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        dialog = new Dialog(act) {
            @Override
            public void onBackPressed() {
                back();
            }
        };
    }

    /** A single-choice list: {@code checked} (or -1) is marked, a pick closes at once then calls {@code onPick}; an optional neutral button, and Cancel. */
    static void choose(Activity act, int title, String[] labels, int checked, IntConsumer onPick, int neutral, Runnable onNeutral) {
        SettingsDialog d = new SettingsDialog(act, act.getString(title), null);
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            TextView row = d.row(i == checked ? labels[i] + "  ✓" : labels[i], 48);
            row.setSelected(i == checked);
            row.setOnClickListener(v -> d.finish(() -> onPick.accept(which)));
            d.list.addView(row);
            if (i == checked || d.initialFocus == null) d.initialFocus = row;
        }
        if (neutral != 0) d.button(neutral, onNeutral);
        d.button(android.R.string.cancel, null);
        d.show();
    }

    /** A confirmation: message, the positive button and Cancel. */
    static void confirm(Activity act, int title, int message, int positive, Runnable onPositive) {
        SettingsDialog d = new SettingsDialog(act, act.getString(title), act.getString(message));
        d.button(positive, onPositive);
        d.button(android.R.string.cancel, null);
        d.show();
    }

    private TextView label(CharSequence text, float sp, int font, int color) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(UiFont.get(act, font));
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private TextView row(CharSequence text, int heightDp) {
        TextView t = new TextView(act, null, 0, R.style.SettingsRow);
        t.setText(text);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(heightDp * dp));
        p.topMargin = Math.round(6 * dp);
        t.setLayoutParams(p);
        t.setTypeface(UiFont.get(act, R.string.font_regular));
        return t;
    }

    private void button(int text, Runnable action) {
        TextView b = row(act.getString(text), 48);
        b.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Math.round(48 * dp), 1f);
        p.setMarginEnd(Math.round(8 * dp));
        b.setLayoutParams(p);
        b.setOnClickListener(v -> finish(action));
        buttons.addView(b);
        if (initialFocus == null) initialFocus = b;
    }

    /** A chosen row or button closes at once (no exit animation), then does its thing. */
    private void finish(Runnable then) {
        if (closing) return;
        closing = true;
        if (motion != null) motion.cancel();
        dialog.dismiss();
        if (then != null) then.run();
    }

    private void back() {
        if (closing) return;
        closing = true;
        motion.close(dialog::dismiss);
    }

    private void show() {
        Glass.apply(panel, GlassSurface.DIALOG_RADIUS_DP, true);
        Glass.chips(panel);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Math.round(480 * dp), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        motion = new GlassMotionController(panel, Glass.animations(), Glass.reducedMotion());
        motion.watch(panel, false);
        motion.prepareOpen(0.5f, 0.5f);
        dialog.show();
        motion.runOpen();
        if (initialFocus != null) initialFocus.requestFocus();
    }
}
