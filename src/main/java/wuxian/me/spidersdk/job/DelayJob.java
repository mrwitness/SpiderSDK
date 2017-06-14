package wuxian.me.spidersdk.job;

import wuxian.me.spidersdk.BaseJob;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by wuxian on 31/3/2017.
 */
public class DelayJob extends BaseJob {
    private long milltimes;

    public DelayJob(long milltimes) {
        this.milltimes = milltimes;
    }

    private static Timer timer = new Timer();

    TimerTask timerTask;

    public void run() {
        if (timerTask == null) {
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    if (realJob != null) {
                        realJob.run();
                    }
                }
            };
        }
        timer.schedule(timerTask, milltimes);
    }
}
