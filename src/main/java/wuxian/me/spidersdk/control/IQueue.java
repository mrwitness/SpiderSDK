package wuxian.me.spidersdk.control;

import wuxian.me.spidersdk.job.IJob;

/**
 * Created by wuxian on 10/5/2017.
 */
public interface IQueue {

    boolean putJob(IJob job, int state);

    IJob getJob();

    boolean isEmpty();

    int getJobNum();

    void init();
}
