package com.gathin.wallfeeds;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Every picture and clip the add-on holds, as a grid of thumbnails. OK on a tile cycles it Normal, Pinned (kept
 * through every refresh), Hidden (deleted and never fetched again), Normal. "Show only pinned" empties Home's
 * feeds down to the pins.
 */
public class GalleryActivity extends Activity {
    private static final int COLUMNS = 4, THUMB_W = 480, THUMB_H = 270;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> thumbs = new LruCache<>(48);
    private final List<Picks.Entry> entries = new ArrayList<>();
    private SharedPreferences prefs;
    private TextView hint;
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(Fetcher.PREFS, MODE_PRIVATE);
        int pad = dp(40);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(16, 20, 36));

        TextView title = new TextView(this);
        title.setText("Choose wallpapers");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        root.addView(title);

        hint = new TextView(this);
        hint.setTextColor(Color.rgb(200, 206, 224));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        hint.setPadding(0, dp(8), 0, dp(8));
        hint.setText("OK on a picture: Pinned (always kept), then Hidden (deleted, never fetched again), then Normal.");
        root.addView(hint);

        CheckBox only = new CheckBox(this);
        only.setText("Show only pinned (Home shows just your picks)");
        only.setTextColor(Color.WHITE);
        only.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        only.setChecked(prefs.getBoolean(Fetcher.KEY_ONLY_PINNED, false));
        StatusActivity.focusRing(only);
        only.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean(Fetcher.KEY_ONLY_PINNED, on).apply();
            // On: the rest leaves Home now. Off: the next refresh fills it again.
            io.execute(() -> {
                if (on) Fetcher.applyNow(getApplicationContext());
                reload();
            });
        });
        root.addView(only);

        GridView grid = new GridView(this);
        grid.setNumColumns(COLUMNS);
        grid.setHorizontalSpacing(dp(12));
        grid.setVerticalSpacing(dp(12));
        grid.setDrawSelectorOnTop(true);
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(4), Color.rgb(140, 170, 255));
        ring.setCornerRadius(dp(6));
        grid.setSelector(ring);
        adapter = new Tiles();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, position, id) -> {
            String name = entries.get(position).name;
            io.execute(() -> {
                Fetcher.cycle(getApplicationContext(), name);
                reload();
            });
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        lp.topMargin = dp(12);
        root.addView(grid, lp);
        setContentView(root);
        grid.requestFocus();
        io.execute(this::reload);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    /** Re-read the held items (on the worker) and show them. */
    private void reload() {
        List<Picks.Entry> fresh = Fetcher.entries(getApplicationContext());
        runOnUiThread(() -> {
            entries.clear();
            entries.addAll(fresh);
            adapter.notifyDataSetChanged();
            hint.setText(fresh.isEmpty()
                    ? "Nothing yet: refresh from the previous screen first."
                    : "OK on a picture: Pinned (always kept), then Hidden (deleted, never fetched again), then Normal.");
        });
    }

    private final class Tiles extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int i) { return entries.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Tile t = convert != null && convert.getTag() instanceof Tile ? (Tile) convert.getTag() : new Tile();
            t.show(entries.get(position));
            return t.root;
        }

        /** A tile: thumbnail, caption on a dark strip, and a badge for Pinned or Hidden. */
        private final class Tile {
            final FrameLayout root = new FrameLayout(GalleryActivity.this);
            final ImageView image = new ImageView(GalleryActivity.this);
            final TextView caption = new TextView(GalleryActivity.this);
            final TextView badge = new TextView(GalleryActivity.this);

            Tile() {
                root.setTag(this);
                root.setBackgroundColor(Color.rgb(30, 36, 62));
                root.setLayoutParams(new android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                root.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                caption.setTextColor(Color.WHITE);
                caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                caption.setMaxLines(2);
                caption.setPadding(dp(8), dp(4), dp(8), dp(4));
                caption.setBackgroundColor(Color.argb(170, 0, 0, 0));
                root.addView(caption, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                badge.setTextColor(Color.WHITE);
                badge.setPadding(dp(10), dp(2), dp(10), dp(2));
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
                bp.setMargins(dp(6), dp(6), dp(6), dp(6));
                root.addView(badge, bp);
            }

            void show(Picks.Entry e) {
                caption.setText((e.clip ? "▶ " : "") + (e.caption.isEmpty() ? e.name : e.caption));
                badge.setVisibility(e.state == Picks.NORMAL ? View.GONE : View.VISIBLE);
                badge.setText(e.state == Picks.PINNED ? "Pinned" : "Hidden");
                badge.setBackgroundColor(e.state == Picks.PINNED ? Color.rgb(40, 130, 70) : Color.rgb(160, 50, 50));
                image.setAlpha(e.state == Picks.HIDDEN ? 0.25f : 1f);
                image.setTag(e.name);
                Bitmap cached = thumbs.get(e.name);
                image.setImageBitmap(cached);
                if (cached != null || e.state == Picks.HIDDEN) return;   // a hidden file is gone from the launcher
                io.execute(() -> {
                    Bitmap b = thumb(e);
                    if (b == null) return;
                    thumbs.put(e.name, b);
                    runOnUiThread(() -> { if (e.name.equals(image.getTag())) image.setImageBitmap(b); });
                });
            }
        }
    }

    /** A small picture of the item, from the launcher's file; null when it cannot be read (the tile keeps its caption). */
    private Bitmap thumb(Picks.Entry e) {
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(Fetcher.uri(e.name), "r")) {
            if (pfd == null) return null;
            if (e.clip) {
                if (Build.VERSION.SDK_INT < 27) return null;
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(pfd.getFileDescriptor());
                    return r.getScaledFrameAtTime(2_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB_W, THUMB_H);
                } finally {
                    r.release();
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= THUMB_W) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, o);
        } catch (Exception | OutOfMemoryError ex) {
            return null;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
