package com.gathin.calibration;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.Display;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

/**
 * Full-screen test patterns. The surface is fixed to the display mode's physical size, so one canvas pixel is
 * one panel pixel even when the window itself is 1080p on a 4K panel.
 *
 * Left/Right: next/previous pattern. Up/Down: limited or full range levels. OK: show or hide the help.
 * Start with {@code --es pattern <id>} (see {@link Patterns#IDS}).
 */
public class PatternActivity extends Activity implements SurfaceHolder.Callback, Choreographer.FrameCallback {
    private SurfaceView surface;
    private int index;
    private boolean help = true, limited = false, ready, ticking;
    private int width, height;
    private long startMs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Ask for the default colour mode: no wide gamut or HDR tone mapping under the pattern.
        getWindow().setColorMode(ActivityInfo.COLOR_MODE_DEFAULT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String id = getIntent().getStringExtra("pattern");
        index = Math.max(0, id == null ? 0 : Patterns.indexOf(id));
        surface = new SurfaceView(this);
        setContentView(surface);
        hideSystemUi();
        Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();
        int w = mode.getPhysicalWidth(), h = mode.getPhysicalHeight();
        if (h > w) { int t = w; w = h; h = t; }
        surface.getHolder().setFixedSize(w, h);
        surface.getHolder().addCallback(this);
        startMs = System.currentTimeMillis();
    }


    /** A second `am start --es pattern <id>` (calibrate tools, a script) switches the open screen. */
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String id = intent.getStringExtra("pattern");
        if (id == null) return;
        index = Math.max(0, Patterns.indexOf(id));
        startMs = System.currentTimeMillis();
        if (ready) redraw();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        surface.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        width = w;
        height = h;
        ready = true;
        redraw();
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) { ready = false; }

    @Override
    protected void onResume() {
        super.onResume();
        redraw();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ticking = false;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        ticking = false;
        redraw();
    }

    private void redraw() {
        if (!ready) return;
        Canvas c = null;
        SurfaceHolder holder = surface.getHolder();
        try {
            try {
                c = holder.lockHardwareCanvas();
            } catch (IllegalStateException e) {
                c = holder.lockCanvas();
            }
            if (c == null) return;
            Patterns.draw(c, width, height, index, limited, System.currentTimeMillis() - startMs);
            if (help) {
                String footer = getString(R.string.pattern_footer, index + 1, Patterns.IDS.length,
                    getString(limited ? R.string.range_limited : R.string.range_full), width, height);
                Patterns.overlay(c, width, height, getString(Patterns.TITLES[index]),
                    getString(Patterns.HELPS[index]), footer);
            }
        } finally {
            if (c != null) holder.unlockCanvasAndPost(c);
        }
        if (Patterns.animated(index) && !ticking) {
            ticking = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                index = (index + 1) % Patterns.IDS.length;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                index = (index + Patterns.IDS.length - 1) % Patterns.IDS.length;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                limited = !limited;
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                help = !help;
                break;
            default:
                return super.onKeyDown(keyCode, event);
        }
        redraw();
        return true;
    }
}
