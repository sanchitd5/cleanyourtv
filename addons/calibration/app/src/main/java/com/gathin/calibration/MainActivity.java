package com.gathin.calibration;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/** The menu: the picture patterns, then each sound signal. D-pad and OK only. */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String[] rows = new String[1 + SignalActivity.IDS.length];
        rows[0] = getString(R.string.menu_patterns, Patterns.IDS.length);
        for (int i = 0; i < SignalActivity.IDS.length; i++) {
            rows[i + 1] = getString(R.string.menu_signal, getString(SignalActivity.TITLES[i]));
        }
        ListView list = new ListView(this);
        list.setBackgroundColor(Color.BLACK);
        list.setPadding(120, 80, 120, 80);
        list.setAdapter(new ArrayAdapter<String>(this, 0, rows) {
            @Override
            public View getView(int position, View convert, ViewGroup parent) {
                TextView t = convert instanceof TextView ? (TextView) convert : new TextView(getContext());
                t.setText(getItem(position));
                t.setTextSize(30);
                t.setTextColor(Color.WHITE);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(32, 28, 32, 28);
                return t;
            }
        });
        setContentView(list);
        // A row passes the signal's index, not its id: the screen then waits for OK instead of counting down.
        list.setOnItemClickListener((p, v, position, id) -> {
            if (position == 0) {
                startActivity(new Intent(this, PatternActivity.class));
            } else {
                startActivity(new Intent(this, SignalActivity.class).putExtra("pickedSignal", position - 1));
            }
        });
    }
}
