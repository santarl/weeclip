package io.rfsj.weeclip;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Backup for metadata fetching: runs once a network is available, in case the share-time thread got killed. */
public class J extends JobService {
    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        js.schedule(new JobInfo.Builder(1, new ComponentName(c, J.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build());
    }

    @Override public boolean onStartJob(JobParameters p) {
        new Thread(() -> {
            F.pending(getApplicationContext());
            jobFinished(p, D.hasPending(getApplicationContext()));
        }).start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters p) { return true; }
}
