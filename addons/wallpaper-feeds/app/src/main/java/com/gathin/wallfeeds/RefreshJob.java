package com.gathin.wallfeeds;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The refresh, run by the system: twice a day on an unmetered network with storage not low, and once at once
 * from "Refresh now". A job the system stops leaves its download part behind and the next run resumes it.
 */
public class RefreshJob extends JobService {
    private static final int PERIODIC = 1, NOW = 2;
    private static final long EVERY_MS = 12L * 60 * 60 * 1000;

    private final AtomicBoolean stop = new AtomicBoolean();

    /** Keep the twice-daily refresh scheduled; a no-op when it already is. */
    static void schedule(Context ctx) {
        JobScheduler js = ctx.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(PERIODIC) != null) return;
        js.schedule(new JobInfo.Builder(PERIODIC, new ComponentName(ctx, RefreshJob.class))
                .setPeriodic(EVERY_MS)
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
