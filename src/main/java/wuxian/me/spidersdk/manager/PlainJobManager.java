package wuxian.me.spidersdk.manager;

import com.sun.istack.internal.NotNull;
import okhttp3.*;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidercommon.util.ShellUtil;
import wuxian.me.spidercommon.util.SignalManager;
import wuxian.me.spidersdk.BaseSpider;
import wuxian.me.spidersdk.IJobManager;
import wuxian.me.spidersdk.JobManagerConfig;
import wuxian.me.spidersdk.anti.Fail;
import wuxian.me.spidersdk.anti.BlockHelper;
import wuxian.me.spidersdk.anti.HeartbeatManager;
import wuxian.me.spidersdk.anti.IPProxyTool;
import wuxian.me.spidersdk.control.*;
import wuxian.me.spidersdk.job.IJob;
import wuxian.me.spidersdk.job.JobProvider;
import wuxian.me.spidersdk.util.OkhttpProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by wuxian on 9/4/2017.
 */
public class PlainJobManager implements HeartbeatManager.IHeartBeat,IJobManager {

    private SignalManager processManager = new SignalManager();

    private JobMonitor monitor = new JobMonitor();
    private IQueue queue;

    private WorkThread workThread = new WorkThread(this);

    private HeartbeatManager heartbeatManager = new HeartbeatManager();
    private IPProxyTool ipProxyTool = new IPProxyTool();
    private AtomicBoolean isSwitchingIP = new AtomicBoolean(false);

    private List<BaseSpider> todoSpiderList = Collections.synchronizedList(new ArrayList<BaseSpider>());
    private Dispatcher dispatcher = OkhttpProvider.getClient().dispatcher();

    private BlockHelper blockHelper = new BlockHelper();
    private AtomicInteger successJobNum = new AtomicInteger(0);
    private long workThreadStartTime;

    private boolean started = false;

    public PlainJobManager() {
        processManager.registerOnSystemKill(new SignalManager.OnSystemKill() {
            public void onSystemKilled() {
                onPause();
            }
        });
        processManager.init();

        ShellUtil.init();
        queue = new JobQueue(monitor);
        Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                LogManager.error("uncaughtExceptionHandler e:" + e.getMessage());
                onUncaughtException(t, e);
            }
        });
        onResume();
    }

    public void onUncaughtException(Thread t, Throwable e) {

    }

    public void onResume() {
        ;
    }

    public void onPause() {
        ;
    }

    //单独调用
    //MUST BE CALLED MANUALLY!
    public void start() {
        if (!started) {
            started = true;
            heartbeatManager.addHeartBeatCallback(this);
            workThreadStartTime = System.currentTimeMillis();
            LogManager.info("WorkThread started");
            workThread.start();
        }
    }

    public boolean putJob(@NotNull IJob job) {
        return queue.putJob(job, IJob.STATE_INIT);
    }

    public boolean putJob(@NotNull IJob job, boolean forceDispatch) {
        return false;
    }

    public IJob getJob() {
        IJob job = queue.getJob();
        return job;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void onDispatch(@NotNull BaseSpider spider) {
        todoSpiderList.add(spider);
    }

    public void success(Runnable runnable) {
        successJobNum.getAndIncrement();
        blockHelper.removeFail(runnable);

        IJob job = monitor.getJob(runnable);
        if (job == null) {
            return;
        }
        LogManager.info("Job Success: " + ((BaseSpider) (job.getRealRunnable())).name());
        todoSpiderList.remove(job);
        monitor.putJob(job, IJob.STATE_SUCCESS);

    }

    public void fail(@NotNull Runnable runnable, @NotNull Fail fail) {
        fail(runnable, fail, true);
    }

    public void fail(@NotNull Runnable runnable, @NotNull Fail fail, boolean retry) {

        if (isSwitchingIP.get()) {
            IJob job = monitor.getJob(runnable);
            if (job != null) {
                monitor.putJob(job, IJob.STATE_FAIL);
            }
        } else {
            blockHelper.addFail(runnable, fail);

            IJob job = monitor.getJob(runnable);
            if (job != null) {
                todoSpiderList.remove(job);

                job.fail(fail);
                monitor.putJob(job, IJob.STATE_FAIL);

                if (retry && JobManagerConfig.enableRetrySpider) {
                    LogManager.info("Retry Job: " + job.toString());
                    IJob next = JobProvider.getNextJob(job);
                    queue.putJob(next, IJob.STATE_RETRY);
                }
            }
            if (blockHelper.isBlocked()) {
                LogManager.error("WE ARE BLOCKED!");
                LogManager.error("Until now, We have success " + successJobNum.get() +
                        " jobs, We have run " + (System.currentTimeMillis() - workThreadStartTime) / 1000 + " seconds");
                heartbeatManager.stopHeartBeat();
                dealBlock();
            }
        }
    }

    private void dealBlock() {
        if (JobManagerConfig.enableSwitchProxy) {
            LogManager.info("We begin to switch IP...");
            doSwitchIp();
        } else {
            isSwitchingIP.set(true);

            LogManager.info("We will not switch IP ");
            LogManager.info("We have total " + monitor.getWholeJobNum() + " jobs, we have "
                    + queue.getJobNum() + " jobs in JobQueue, we have "
                    + todoSpiderList.size() + "jobs in todoSpiderList");
            LogManager.info("We have " + dispatcher.runningCallsCount() +
                    " request running and " + dispatcher.queuedCallsCount() + " request queue in OkHttpClient");

            dispatcher.cancelAll();
            workThread.pauseWhenSwitchIP();
            monitor.printAllJobStatus();
        }
    }

    private void doSwitchIp() {
        isSwitchingIP.set(true);
        workThread.pauseWhenSwitchIP();
        LogManager.info("WorkThread paused");

        dispatcher.cancelAll();

        LogManager.info("We will not switch IP ");
        LogManager.info("We have total " + monitor.getWholeJobNum() + " jobs, we have "
                + queue.getJobNum() + " jobs in JobQueue, we have "
                + todoSpiderList.size() + " jobs in todoSpiderList");
        LogManager.info("We have " + dispatcher.runningCallsCount() +
                " request running and " + dispatcher.queuedCallsCount() +
                " request queue in OkHttpClient");

        monitor.printAllJobStatus();  //监控用

        while (true) {  //每个ip尝试三次 直到成功或没有proxy
            Proxy proxy = ipProxyTool.switchNextProxy();
            if (proxy == null) {

                if (JobManagerConfig.enableRuntimeInputProxy) {
                    ipProxyTool.openShellAndEnsureProxyInputed();

                    proxy = ipProxyTool.switchNextProxy();
                } else {
                    throw new RuntimeException("Proxy is not available!");
                }

            }

            LogManager.info("We try to switch to Ip: " + proxy.ip + " Port: " + proxy.port);
            int ensure = 0;
            boolean success = false;
            while (!(success = ipSwitched(proxy)) && ensure < JobManagerConfig.everyProxyTryTime) {  //每个IP尝试三次
                ensure++;
            }
            if (success) {
                LogManager.info("Switch Proxy success");
                break;
            }
        }

        dispatcher.cancelAll();
        for (BaseSpider spider : todoSpiderList) {
            IJob job = monitor.getJob(spider);
            if (job != null) {
                queue.putJob(job, IJob.STATE_INIT);
            }
        }
        todoSpiderList.clear();

        blockHelper.reInit();
        workThreadStartTime = System.currentTimeMillis();
        LogManager.info("WorkThread resumed");
        workThread.resumeNow();
        isSwitchingIP.set(false);
    }

    public Proxy switchProxy() {
        return ipProxyTool.switchNextProxy();
    }

    private boolean ensureIpSwitched(final Proxy proxy)
            throws InterruptedException, ExecutionException {
        return ipProxyTool.ensureIpSwitched(proxy);
    }

    public boolean ipSwitched(final Proxy proxy) {
        return this.ipSwitched(proxy, false);
    }

    public boolean ipSwitched(final Proxy proxy, boolean beginHeartBeat) {
        try {
            boolean ret = ensureIpSwitched(proxy);
            if (ret && beginHeartBeat) {
                heartbeatManager.beginHeartBeat(proxy);
            }
            return ret;
        } catch (InterruptedException e1) {
            return false;
        } catch (ExecutionException e) {
            return false;
        }
    }


    public void onHeartBeatBegin() {

    }

    public void onHeartBeat(int time) {
        LogManager.info("onHeartBeat " + time);
    }


    public void onHeartBeatFail() {
        LogManager.info("onHeartBeatFail");
        dealBlock();  //这里和block差不多等价
    }

    //JobManager主动调用HeartbeatManager.stopxxx
    public void onHeartBeatInterrupt() {
        LogManager.info("onHeartBeatInterrupt");
    }
}
