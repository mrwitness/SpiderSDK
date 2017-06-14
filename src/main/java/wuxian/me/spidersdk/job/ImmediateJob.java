package wuxian.me.spidersdk.job;

import wuxian.me.spidersdk.BaseJob;

/**
 * Created by wuxian on 31/3/2017.
 */
public class ImmediateJob extends BaseJob {

    public void run() {
        if (realJob != null) {
            realJob.run();
        }
    }
}
