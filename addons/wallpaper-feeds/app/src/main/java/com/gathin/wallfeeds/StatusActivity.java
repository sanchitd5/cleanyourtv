package com.gathin.wallfeeds;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Which feeds to fetch, what the last refresh did, and a button to refresh now. */
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

        int pad = dp(48);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(16, 20, 36));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        root.addView(title);

        root.addView(toggle(R.string.source_aerials, Fetcher.KEY_AERIALS));
        root.addView(toggle(R.string.source_bing, Fetcher.KEY_BING));
        root.addView(toggle(R.string.source_wikimedia, Fetcher.KEY_WIKIMEDIA));

        Button refresh = new Button(this);
        refresh.setText(R.string.refresh_now);
        refresh.setOnClickListener(v -> {
            RefreshJob.runNow(this);
            showStatus();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        root.addView(refresh, lp);

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
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        status.setGravity(Gravity.START);
        status.setPadding(0, dp(24), 0, 0);
        root.addView(status);

        setContentView(root);
        refresh.requestFocus();
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
        progressText.setVisibility(on ? android.view.View.VISIBLE : android.view.View.GONE);
        bar.setVisibility(on ? android.view.View.VISIBLE : android.view.View.GONE);
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

    private CheckBox toggle(int label, String key) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(Color.WHITE);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        box.setChecked(prefs.getBoolean(key, true));
        box.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(key, on).apply());
        return box;
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
