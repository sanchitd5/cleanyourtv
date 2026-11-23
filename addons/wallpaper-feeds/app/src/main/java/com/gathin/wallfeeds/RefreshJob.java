package com.gathin.wallfeeds;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The refresh, run by the system: every 6, 12 (default) or 24 hours, or never, on an unmetered network with storage not low, and once at once
 * from "Refresh now". A job the system stops leaves its download part behind and the next run resumes it.
 */
public class RefreshJob extends JobService {
    private static final int PERIODIC = 1, NOW = 2;

    private final AtomicBoolean stop = new AtomicBoolean();

    /**
     * Keep the periodic refresh scheduled at the chosen period (12 hours unless changed); a no-op when it already
     * is. Manual only cancels it. Call again after the choice changes.
     */
    static void schedule(Context ctx) {
        JobScheduler js = ctx.getSystemService(JobScheduler.class);
        if (js == null) return;
        int hours = ctx.getSharedPreferences(Fetcher.PREFS, Context.MODE_PRIVATE).getInt(Fetcher.KEY_REFRESH_HOURS, 12);
        long every = Feeds.periodMs(hours);
        JobInfo pending = js.getPendingJob(PERIODIC);
        if (hours == 0) {
            if (pending != null) js.cancel(PERIODIC);
            return;
        }
        if (pending != null && pending.getIntervalMillis() == every) return;
        js.schedule(new JobInfo.Builder(PERIODIC, new ComponentName(ctx, RefreshJob.class))
                .setPeriodic(every)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setRequiresStorageNotLow(true)
                .setPersisted(true)
                .build());
    }

    static void runNow(Context ctx) {
        JobScheduler js = ctx.getSystemService(JobScheduler.class);
        if (js == null) return;
        js.schedule(new JobInfo.Builder(NOW, new ComponentName(ctx, RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build());
    }

    static boolean running(Context ctx) {
        JobScheduler js = ctx.getSystemService(JobScheduler.class);
        return js != null && js.getPendingJob(NOW) != null;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        stop.set(false);
        new Thread(() -> {
            Fetcher.run(this, stop);
            jobFinished(params, false);
        }, "wallfeeds-refresh").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stop.set(true);
        return true;   // reschedule: the download part is resumed
    }
}
