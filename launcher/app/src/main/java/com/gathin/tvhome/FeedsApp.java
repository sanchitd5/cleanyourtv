package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * wallpaper_feeds: the Wallpaper feeds app, offered for install from Home. Its install script stages the APK in
 * feeds/ (through ThemeProvider); Home asks once per staged file, and Install hands it to the system installer,
 * which shows its own confirmation. Nothing is installed without the person pressing Install there too.
 */
final class FeedsApp {
    static final String PACKAGE = "com.gathin.wallfeeds";
    static final String APK = "wallfeeds.apk";
    private static final String TAG = "FeedsApp";
    private static final String PREFS = "feeds_app";
    private static final String DECLINED = "declined";
    private static final String ACTION = "com.gathin.tvhome.FEEDS_APP_INSTALL";

    private static volatile boolean installing;

    private FeedsApp() {}

    static File staged(Context ctx) {
        File dir = Config.dir(ctx);
        return dir == null ? null : new File(new File(dir, ThemeFiles.FEEDS), APK);
    }

    static boolean installed(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** One stat and one binder call: a staged APK, not installed, not being installed, not declined as it is. */
    static boolean offer(Context ctx) {
        File apk = staged(ctx);
        if (installing || apk == null || !apk.isFile() || apk.length() == 0) return false;
        if (installed(ctx)) {
            apk.delete();   // installed some other way (adb): the staged copy has done its job
            return false;
        }
        return !stamp(apk).equals(prefs(ctx).getString(DECLINED, ""));
    }

    /** "Not now": not asked again until a different APK is staged. */
    static void decline(Context ctx) {
        File apk = staged(ctx);
        if (apk != null) prefs(ctx).edit().putString(DECLINED, stamp(apk)).apply();
    }

    /** Hands the staged APK to the system installer, off the UI thread; the Receiver takes it from there. */
    static void install(Context ctx) {
        Context app = ctx.getApplicationContext();
        File apk = staged(app);
        if (apk == null || !apk.isFile() || installing) return;
        installing = true;
        new Thread(() -> {
            PackageInstaller pi = app.getPackageManager().getPackageInstaller();
            int id = -1;
            try {
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(PACKAGE);
                id = pi.createSession(params);
                try (PackageInstaller.Session session = pi.openSession(id)) {
                    try (InputStream in = new FileInputStream(apk);
                         OutputStream out = session.openWrite(APK, 0, apk.length())) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        session.fsync(out);
                    }
                    Intent done = new Intent(app, Receiver.class).setAction(ACTION);
                    PendingIntent pending = PendingIntent.getBroadcast(app, id, done,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    session.commit(pending.getIntentSender());
                }
            } catch (Exception e) {
                Log.w(TAG, "install failed", e);
                if (id >= 0) {
                    try { pi.abandonSession(id); } catch (Exception ignored) { }
                }
                installing = false;
            }
        }, "feeds-app-install").start();
    }

    private static String stamp(File f) {
        return f.length() + "@" + f.lastModified();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The installer's answers: its confirmation screen first, then the outcome. */
    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        ctx.startActivity(confirm);
                        return;
                    } catch (RuntimeException e) {
                        Log.w(TAG, "could not open the installer confirmation screen", e);
                    }
                }
                installing = false;
                return;
            }
            installing = false;
            if (status == PackageInstaller.STATUS_SUCCESS) {
                File apk = staged(ctx);
                if (apk != null) apk.delete();
                // Its first screen schedules the refreshes and starts the first one.
                Intent open = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(PACKAGE);
                if (open != null) {
                    try {
                        ctx.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException ignored) {
                        // Home stays; the app is in the app list
                    }
                }
            } else if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                String why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(ctx, ctx.getString(R.string.feeds_app_failed, why == null ? String.valueOf(status) : why),
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
