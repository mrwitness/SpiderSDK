package wuxian.me.spidersdk.control;

import com.sun.istack.internal.NotNull;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidersdk.JobManagerConfig;
import wuxian.me.spidersdk.job.IJob;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by wuxian on 1/4/2017.
 * <p>
 * 任务队列:这个任务队列是要进行的任务队列
 */
public class JobQueue implements IQueue {

    private JobMonitor monitor;
    private Random random = new Random();

    List<IJob> queue = new ArrayList<IJob>();

    public JobQueue(@NotNull JobMonitor monitor) {
        this.monitor = monitor;
    }

    //Todo
    public boolean putJob(IJob job, boolean forceDispatch) {
        return false;
    }

    public boolean putJob(IJob job, int state) {
        LogManager.debug("putJob: " + job.toString());

        //通过检查job防止重复:比如说重复进行company主页的抓取
        if (!JobManagerConfig.enableInsertDuplicateJob && monitor.contains(job) && state != IJob.STATE_RETRY) {
            return true;
        }
        monitor.putJob(job, state);
        queue.add(job);

        return true;
    }

    public boolean putJob(@NotNull IJob job) {
        return putJob(job, IJob.STATE_INIT);
    }

    public IJob getJob() {
        IJob job = null;
        synchronized (queue) {
            if (queue.isEmpty()) {
                return null;
            } else {
                job = queue.get(0);
                queue.remove(0);
            }

        }
        return job;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int getJobNum() {
        return queue.size();
    }

    public void init() {

    }
}
