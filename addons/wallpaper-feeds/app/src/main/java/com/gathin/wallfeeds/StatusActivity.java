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
import android.widget.TextView;

/** Which feeds to fetch, what the last refresh did, and a button to refresh now. */
public class StatusActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            showStatus();
            handler.postDelayed(this, 2000);
        }
    };
    private SharedPreferences prefs;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(Fetcher.PREFS, MODE_PRIVATE);
        RefreshJob.schedule(this);

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
        StringBuilder s = new StringBuilder();
        if (RefreshJob.running(this)) s.append("Refreshing…\n\n");
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
