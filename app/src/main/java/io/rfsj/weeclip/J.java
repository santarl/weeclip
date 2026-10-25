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
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js == null) return;
            js.schedule(new JobInfo.Builder(1, new ComponentName(c, J.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .build());
        } catch (Throwable t) {
            // the share-time fetch and the next app start still retry
        }
    }

    @Override public boolean onStartJob(JobParameters p) {
        new Thread(() -> {
            boolean again = true;
            try {
                F.pending(getApplicationContext());
                again = D.hasPending(getApplicationContext());
            } catch (Throwable t) {
                // retry later
            }
            jobFinished(p, again);
        }).start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters p) { return true; }
}
