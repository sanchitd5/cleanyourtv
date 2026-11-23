package com.gathin.calibration;

import android.app.Activity;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Sound test signals, generated in memory: pink noise, the seven EQ bands in turn, a log sweep and a left-right
 * check. Peaks stay at -12 dBFS and every signal fades in and out over 50 ms.
 *
 * OK starts or stops. From the menu it waits for OK; started with {@code --es signal <id>} (pink, bands, sweep,
 * lr) it starts by itself after a 3 second countdown, long enough for Back to cancel.
 */
public class SignalActivity extends Activity {
    static final String[] IDS = {"pink", "bands", "sweep", "lr"};
    static final int[] TITLES = {R.string.s_pink_title, R.string.s_bands_title, R.string.s_sweep_title, R.string.s_lr_title};
    static final int[] HELPS = {R.string.s_pink_help, R.string.s_bands_help, R.string.s_sweep_help, R.string.s_lr_help};
    private static final int COUNTDOWN = 3;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView title, big, small;
    private int signal;
    private volatile boolean playing;
    private AudioTrack track;
    private Thread writer;
    private int framesTotal;
    private boolean stereo;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String id = getIntent().getStringExtra("signal");
        signal = id != null ? Math.max(0, indexOf(id)) : Math.max(0, getIntent().getIntExtra("pickedSignal", 0));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(80, 40, 80, 40);
        TextView warn = text(34, Color.rgb(255, 90, 70));
        warn.setText(R.string.volume_warning);
        title = text(40, Color.WHITE);
        big = text(96, Color.rgb(255, 200, 80));
        small = text(26, Color.LTGRAY);
        root.addView(warn);
        root.addView(title);
        root.addView(big);
        root.addView(small);
        setContentView(root);
        showIdle();
        if (id != null) countdown(COUNTDOWN);
    }

    /** A second `am start --es signal <id>` (tools/calibrate_sound.py) restarts with that signal. */
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (intent.getStringExtra("signal") == null) return;
        setIntent(intent);
        recreate();   // onStop stops the running signal
    }

    private TextView text(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 16, 0, 16);
        return t;
    }

    static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    private void showIdle() {
        title.setText(TITLES[signal]);
        big.setText(R.string.press_ok);
        small.setText(HELPS[signal]);
    }

    private void countdown(int left) {
        if (playing) return;
        if (left == 0) {
            start();
            return;
        }
        big.setText(getString(R.string.starting_in, left));
        ui.postDelayed(() -> countdown(left - 1), 1000);
    }

    private short[] samples() {
        switch (IDS[signal]) {
            case "pink": return Tones.pinkNoise(60, 1);
            case "bands": return Tones.bandSequence();
            case "sweep": return Tones.sweep(20, 20000, 20);
            default: return Tones.leftRight();
        }
    }

    private void start() {
        stop();
        ui.removeCallbacksAndMessages(null);
        stereo = "lr".equals(IDS[signal]);
        final short[] pcm = samples();
        int channels = stereo ? 2 : 1;
        framesTotal = pcm.length / channels;
        int mask = stereo ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int buffer = Math.max(AudioTrack.getMinBufferSize(Tones.RATE, mask, AudioFormat.ENCODING_PCM_16BIT), 16384);
        track = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(Tones.RATE).setChannelMask(mask).build())
            .setBufferSizeInBytes(buffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build();
        final AudioTrack t = track;
        playing = true;
        t.play();
        writer = new Thread(() -> {
            int at = 0;
            while (playing && at < pcm.length) {
                int n = t.write(pcm, at, Math.min(4096, pcm.length - at));
                if (n <= 0) break;
                at += n;
            }
            if (playing) t.stop();   // let the buffer drain, then report
        }, "tone-writer");
        writer.start();
        tick();
    }

    private void tick() {
        if (!playing || track == null) return;
        double t = track.getPlaybackHeadPosition() / (double) Tones.RATE;
        if (t * Tones.RATE >= framesTotal || track.getPlayState() == AudioTrack.PLAYSTATE_STOPPED) {
            playing = false;
            releaseTrack();
            title.setText(TITLES[signal]);
            big.setText(R.string.finished);
            small.setText(R.string.press_ok_again);
            return;
        }
        render(t);
        ui.postDelayed(this::tick, 100);
    }

    private void render(double t) {
        title.setText(TITLES[signal]);
        switch (IDS[signal]) {
            case "bands": {
                int band = Tones.bandAt(t);
                big.setText(band < 0 ? getString(R.string.silence) : getString(R.string.hz, Tones.BANDS[band]));
                small.setText(getString(R.string.band_progress, band < 0 ? Math.min(7, (int) (t / 5) + 1) : band + 1, 7));
                break;
            }
            case "lr": {
                int side = Tones.sideAt(t);
                big.setText(side < 0 ? getString(R.string.silence) : getString(side == 0 ? R.string.left : R.string.right));
                small.setText(HELPS[signal]);
                break;
            }
            case "sweep":
                big.setText(getString(R.string.hz, (int) Math.round(20 * Math.pow(1000, Math.min(1, t / 20)))));
                small.setText(R.string.s_sweep_now);
                break;
            default:
                big.setText(R.string.s_pink_now);
                small.setText(getString(R.string.seconds_left, Math.max(0, (int) (60 - t))));
        }
    }

    private void stop() {
        playing = false;
        if (writer != null) {
            try { writer.join(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            writer = null;
        }
        releaseTrack();
    }

    private void releaseTrack() {
        if (track != null) {
            try { track.stop(); } catch (IllegalStateException ignored) { /* already stopped */ }
            track.release();
            track = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
            || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            if (playing) {
                stop();
                showIdle();
            } else {
                start();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        ui.removeCallbacksAndMessages(null);
        stop();
    }
}
