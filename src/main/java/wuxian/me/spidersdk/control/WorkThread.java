package wuxian.me.spidersdk.control;

import wuxian.me.spidersdk.IJobManager;
import wuxian.me.spidersdk.JobManagerConfig;
import wuxian.me.spidersdk.job.IJob;

import java.util.Random;

/**
 * Created by wuxian on 6/4/2017.
 *
 * 由@IJobManager管理
 *
 */
public class WorkThread extends Thread {
    private IJobManager jobManager;

    private int i = 0;
    private Random random = new Random();

    public WorkThread(IJobManager jobManager) {
        this.jobManager = jobManager;
    }

    private boolean pause = false;

    public void pauseWhenSwitchIP() {
        pause = true;
    }

    public synchronized void resumeNow() {
        pause = false;

        i = 0;
        notifyAll();
    }

    private synchronized boolean doIfShouldWait() {
        if (pause) {
            try {
                wait();
            } catch (InterruptedException e) {
                ;
            }
        }
        return true;
    }

    @Override
    public void run() {
        while (true) {
            while (!(jobManager.isEmpty())) {  //分布式模式下 isEmpty判断会失效
                //不使用任何策略 立即分发job模式
                if (JobManagerConfig.enableScheduleImmediately) {
                    doIfShouldWait();
                    IJob job = jobManager.getJob();

                    if (job == null) {
                        break;
                    } else {
                        job.run();
                        continue;
                    }

                }
                if (i >= JobManagerConfig.jobNumToSleep) {  //每隔10个任务休息10s
                    try {
                        sleep(JobManagerConfig.jobSleepTimeToSleep);
                    } catch (InterruptedException e) {
                        ;
                    }
                    doIfShouldWait();
                    IJob job = jobManager.getJob();
                    if (job == null) {
                        break;
                    } else {
                        job.run();
                        i = 0;
                        continue;
                    }

                } else {
                    i++;
                    int min = JobManagerConfig.jobSchedulerTimeMin;
                    int max = JobManagerConfig.jobSchedulerTimeMax;
                    int sleepTime = (int) (min + random.nextDouble() * (max - min)) * 1000;
                    try {
                        sleep(sleepTime);
                    } catch (InterruptedException e) {

                    }

                    doIfShouldWait();
                    IJob job = jobManager.getJob();
                    if (job == null) {
                        break;
                    }
                    try {
                        job.run();
                    } catch (IllegalArgumentException e) {
                        //ignore
                    }

                }
            }

            try {
                sleep(JobManagerConfig.jobQueueEmptySleepTime);
            } catch (InterruptedException e) {
                ;  //ignore
            }
        }
    }
}
