package com.gathin.wallfeeds;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.IntConsumer;

/** Which feeds and how: sources, categories, quality, how many, how often; then refresh now and its status. */
public class StatusActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            showStatus();
            handler.postDelayed(this, 1000);
        }
    };
    private SharedPreferences prefs;
    private TextView status;
    private TextView progressText;
    private ProgressBar bar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(Fetcher.PREFS, MODE_PRIVATE);
        RefreshJob.schedule(this);
        // Just installed (Home opens this screen after its install): the first refresh now, not in 12 hours.
        if (prefs.getLong(Fetcher.KEY_LAST_RUN, 0) == 0 && !RefreshJob.running(this)) RefreshJob.runNow(this);

        boolean hevc4k = Fetcher.hevc(3840, 2160);
        int pad = dp(48);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        root.addView(title);

        root.addView(section("Sources"));
        root.addView(toggle(getString(R.string.source_aerials), Fetcher.KEY_AERIALS));
        root.addView(toggle(getString(R.string.source_bing), Fetcher.KEY_BING));
        root.addView(toggle(getString(R.string.source_wikimedia), Fetcher.KEY_WIKIMEDIA));

        root.addView(section("Aerial categories"));
        LinearLayout cats = new LinearLayout(this);
        cats.setOrientation(LinearLayout.HORIZONTAL);
        for (String[] c : Feeds.CATEGORIES) {
            cats.addView(toggle(c[1], Fetcher.KEY_CAT + c[0], true), new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        root.addView(cats);

        root.addView(section("Aerial quality"));
        String[] qualities = {Feeds.PREF_AUTO, Feeds.PREF_1080, Feeds.PREF_4K};
        root.addView(choices(new String[] {"Auto", "1080p", hevc4k ? "4K" : "4K (needs a 4K HEVC decoder)"},
                new boolean[] {true, true, hevc4k}, indexOf(qualities, prefs.getString(Fetcher.KEY_QUALITY, Feeds.PREF_AUTO)),
                i -> prefs.edit().putString(Fetcher.KEY_QUALITY, qualities[i]).apply()));

        root.addView(section("Aerial videos to keep"));
        String[] clipLabels = new String[Feeds.KEEP_CLIPS.length];
        for (int i = 0; i < clipLabels.length; i++) clipLabels[i] = Feeds.KEEP_CLIPS[i] == 0 ? "Auto" : String.valueOf(Feeds.KEEP_CLIPS[i]);
        root.addView(choices(clipLabels, null, indexOf(Feeds.KEEP_CLIPS, prefs.getInt(Fetcher.KEY_KEEP_CLIPS, 0)),
                i -> prefs.edit().putInt(Fetcher.KEY_KEEP_CLIPS, Feeds.KEEP_CLIPS[i]).apply()));

        root.addView(section("Pictures to keep"));
        String[] stillLabels = new String[Feeds.KEEP_STILLS.length];
        for (int i = 0; i < stillLabels.length; i++) stillLabels[i] = String.valueOf(Feeds.KEEP_STILLS[i]);
        root.addView(choices(stillLabels, null,
                indexOf(Feeds.KEEP_STILLS, Feeds.stillKeep(prefs.getInt(Fetcher.KEY_KEEP_STILLS, Feeds.MAX_STILLS))),
                i -> prefs.edit().putInt(Fetcher.KEY_KEEP_STILLS, Feeds.KEEP_STILLS[i]).apply()));

        root.addView(section("Refresh"));
        String[] refreshLabels = {"Every 6 hours", "Every 12 hours", "Daily", "Manual only"};
        root.addView(choices(refreshLabels, null, indexOf(Feeds.REFRESH_HOURS, prefs.getInt(Fetcher.KEY_REFRESH_HOURS, 12)),
                i -> {
                    prefs.edit().putInt(Fetcher.KEY_REFRESH_HOURS, Feeds.REFRESH_HOURS[i]).apply();
                    RefreshJob.schedule(this);
                }));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(28), 0, 0);
        Button choose = button("Choose wallpapers");
        choose.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
        buttons.addView(choose);
        Button refresh = button(getString(R.string.refresh_now));
        refresh.setOnClickListener(v -> {
            RefreshJob.runNow(this);
            showStatus();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(24);
        buttons.addView(refresh, lp);
        root.addView(buttons);

        progressText = new TextView(this);
        progressText.setTextColor(Color.WHITE);
        progressText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        progressText.setPadding(0, dp(28), 0, dp(10));
        root.addView(progressText);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        root.addView(bar, new LinearLayout.LayoutParams(dp(640), dp(10)));

        status = new TextView(this);
        status.setTextColor(Color.rgb(200, 206, 224));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        status.setGravity(Gravity.START);
        status.setPadding(0, dp(24), 0, 0);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(16, 20, 36));
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    /** The running refresh: step, item n of m, MB done of total, and the bar for the current file. */
    private void showProgress() {
        Fetcher.Progress p = Fetcher.PROGRESS;
        boolean on = p.running || RefreshJob.running(this);
        progressText.setVisibility(on ? View.VISIBLE : View.GONE);
        bar.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) return;
        if (!p.running) {
            progressText.setText("Waiting for the network…");
            bar.setIndeterminate(true);
            return;
        }
        StringBuilder t = new StringBuilder(p.step);
        if (p.count > 0) t.append("  ").append(p.index).append(" of ").append(p.count);
        if (!p.item.isEmpty()) t.append("\n").append(p.item);
        long done = p.done, total = p.total;
        if (total > 0) {
            int pct = (int) Math.min(100, done * 100 / total);
            t.append("\n").append(mb(done)).append(" of ").append(mb(total)).append(" MB  ·  ").append(pct).append('%');
            bar.setIndeterminate(false);
            bar.setProgress((int) Math.min(1000, done * 1000 / total));
        } else {
            if (done > 0) t.append("\n").append(mb(done)).append(" MB");
            bar.setIndeterminate(true);
        }
        progressText.setText(t);
    }

    private static String mb(long bytes) {
        long tenths = bytes * 10 / (1024 * 1024);
        return (tenths / 10) + "." + (tenths % 10);
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.rgb(140, 170, 255));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setPadding(0, dp(24), 0, dp(4));
        return t;
    }

    private CheckBox toggle(String label, String key) {
        return toggle(label, key, false);
    }

    /** A checkbox on a boolean preference (on by default). A category being turned off removes its clips now. */
    private CheckBox toggle(String label, String key, boolean removes) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(Color.WHITE);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        box.setChecked(prefs.getBoolean(key, true));
        box.setPadding(dp(8), dp(6), dp(16), dp(6));
        focusRing(box);
        box.setOnCheckedChangeListener((CompoundButton b, boolean on) -> {
            prefs.edit().putBoolean(key, on).apply();
            // Turning something off removes its files now; turning it on waits for the next refresh.
            if (!on) new Thread(() -> Fetcher.applyNow(getApplicationContext()), "wallfeeds-apply").start();
        });
        return box;
    }

    /** One row of radio buttons, left/right on the remote; {@code enabled} greys out what this TV cannot do. */
    private RadioGroup choices(String[] labels, boolean[] enabled, int selected, IntConsumer onPick) {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton[] buttons = new RadioButton[labels.length];
        for (int i = 0; i < labels.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setId(View.generateViewId());
            r.setText(labels[i]);
            r.setTextColor(Color.WHITE);
            r.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            r.setPadding(dp(8), dp(6), dp(24), dp(6));
            boolean ok = enabled == null || enabled[i];
            r.setEnabled(ok);
            r.setFocusable(ok);
            r.setAlpha(ok ? 1f : 0.4f);
            focusRing(r);
            buttons[i] = r;
            group.addView(r);
        }
        if (selected >= 0 && selected < buttons.length) group.check(buttons[selected].getId());
        group.setOnCheckedChangeListener((g, id) -> {
            for (int i = 0; i < buttons.length; i++) if (buttons[i].getId() == id) onPick.accept(i);
        });
        return group;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        b.setAllCaps(false);
        return b;
    }

    private static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return 0;
    }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return 0;
    }

    /** A rounded outline on the focused control, so the remote's position reads from the sofa. */
    static void focusRing(View v) {
        GradientDrawable on = new GradientDrawable();
        on.setCornerRadius(16);
        on.setColor(Color.argb(40, 255, 255, 255));
        on.setStroke(4, Color.rgb(140, 170, 255));
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[] {android.R.attr.state_focused}, on);
        d.addState(new int[] {}, new GradientDrawable());
        v.setBackground(d);
    }

    private void showStatus() {
        long last = prefs.getLong(Fetcher.KEY_LAST_RUN, 0);
        showProgress();
        StringBuilder s = new StringBuilder();
        if (last == 0) {
            s.append("No refresh yet.");
        } else {
            s.append("Last refresh ").append(DateUtils.getRelativeTimeSpanString(last)).append(":\n")
                    .append(prefs.getString(Fetcher.KEY_STATUS, ""));
        }
        s.append("\n\nThe launcher shows these with \"wallpaper_feeds\": true in its theme.");
        status.setText(s);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
