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
import wuxian.me.spidersdk.anti.*;
import wuxian.me.spidersdk.control.*;
import wuxian.me.spidersdk.job.IJob;
import wuxian.me.spidersdk.job.JobProvider;
import wuxian.me.spidersdk.proxy.HandInputProxyMaker;
import wuxian.me.spidersdk.proxy.IProxyMaker;
import wuxian.me.spidersdk.util.CookieManager;
import wuxian.me.spidersdk.util.JobManagerMonitor;
import wuxian.me.spidersdk.util.OkhttpProvider;
import wuxian.me.spidersdk.util.ProcessLifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by wuxian on 9/4/2017.
 */
public class PlainJobManager implements HeartbeatManager.IHeartBeat, IJobManager, ProcessLifecycle {

    private IProxyMaker proxyMaker;
    private IQueue queue;
    private WorkThread workThread = new WorkThread(this);
    private boolean started = false;

    private JobManagerMonitor managerMonitor = new JobManagerMonitor();
    private HeartbeatManager heartbeatManager = new HeartbeatManager();
    private Dispatcher dispatcher = OkhttpProvider.getClient().dispatcher();

    private List<BaseSpider> successSpiderList = Collections.synchronizedList(new ArrayList<BaseSpider>());
    private List<BaseSpider> failureSpiderList = Collections.synchronizedList(new ArrayList<BaseSpider>());
    private BlockHelper blockHelper = new BlockHelper();

    private AtomicBoolean isSwitchingIP = new AtomicBoolean(false);
    private IPProxyTool ipProxyTool;

    private SignalManager signalManager = new SignalManager();

    private List<BaseSpider> dispatchedSpiderList = Collections.synchronizedList(new ArrayList<BaseSpider>());
    private boolean inited = false;

    private JobMonitor monitor = new JobMonitor();

    public PlainJobManager() {

    }

    @NotNull
    protected IProxyMaker getProxyMaker() {
        return new HandInputProxyMaker();
    }

    private void init() {

        signalManager.registerOnSystemKill(new SignalManager.OnSystemKill() {
            public void onSystemKilled() {
                onPause();
            }
        });
        signalManager.init();

        ShellUtil.init();
        queue = new JobQueue(monitor);
        queue.init();
        Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                LogManager.error("uncaughtExceptionHandler e:" + e.getMessage());

            }
        });

        ipProxyTool = new IPProxyTool();
        ipProxyTool.init();
        if (ipProxyTool.getCurrentProxy() != null) {
            heartbeatManager.beginHeartBeat(ipProxyTool.getCurrentProxy());
        }

        onResume();
    }

    //Todo:序列化？
    public void onResume() {

    }

    public void onPause() {
        workThread.pauseWhenSwitchIP();
        dispatcher.cancelAll();
    }

    public void start() {

        if (!inited) {
            init();
            inited = true;
        }

        if (!started) {
            started = true;
            heartbeatManager.addHeartBeatCallback(this);
            managerMonitor.recordStartTime();

            LogManager.info("WorkThread started");
            workThread.start();
        }
    }

    public boolean putJob(@NotNull IJob job) {
        if (!started) {
            return false;
        }
        return queue.putJob(job, IJob.STATE_INIT);
    }

    //这里不实现
    public boolean putJob(@NotNull IJob job, boolean runImmediately) {
        return putJob(job);
    }

    public IJob getJob() {
        if (!started) {
            return null;
        }
        IJob job = queue.getJob();
        return job;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void onDispatch(@NotNull BaseSpider spider) {
        dispatchedSpiderList.add(spider);
    }

    public void success(Runnable runnable) {
        LogManager.info("Job Success: " + ((BaseSpider) runnable).name());
        dispatchedSpiderList.remove((BaseSpider) runnable);
        blockHelper.removeFail(runnable);
        failureSpiderList.remove((BaseSpider) runnable);
        successSpiderList.add((BaseSpider) runnable);


        IJob job = monitor.getJob(runnable);
        if (job == null) {
            return;
        }
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
            return;
        }

        blockHelper.addFail(runnable, fail);
        dispatchedSpiderList.remove(runnable);

        IJob job = monitor.getJob(runnable);
        if (job != null) {
            monitor.putJob(job, IJob.STATE_FAIL);
        }

        if (retry && JobManagerConfig.enableRetrySpider) {
            IJob next = JobProvider.getJob();
            next.setRealRunnable(runnable);
            queue.putJob(next, IJob.STATE_RETRY);
        }

        if (blockHelper.isBlocked()) {
            LogManager.error("WE ARE BLOCKED!");
            heartbeatManager.stopHeartBeat();
            dealBlock();
        }

    }

    private void dealBlock() {
        if (JobManagerConfig.enableSwitchProxy) {
            LogManager.info("We begin to switch IP...");
            doSwitchIp();
        } else {
            isSwitchingIP.set(true);

            dispatcher.cancelAll();
            workThread.pauseWhenSwitchIP();
            monitor.printAllJobStatus();
        }
    }

    protected void switchProxyTillSuccuss() {

        if(proxyMaker == null) {  //lazy init
            proxyMaker = getProxyMaker();
        }

        boolean switchSuccess = false;
        do {
            Proxy proxy = proxyMaker.makeUntilSuccess();
            ipProxyTool.switchToProxy(proxy);

            int ensure = 0;

            while (!(switchSuccess = ipProxyTool.isIpSwitchedSuccess(proxy)) && ensure < JobManagerConfig.everyProxyTryTime) {  //每个IP尝试三次
                ensure++;
                LogManager.info("Switch Proxy Fail Times: " + ensure);
            }

        } while (!switchSuccess);

        return;
    }

    private void doSwitchIp() {

        isSwitchingIP.set(true);
        LogManager.info("Pausing WorkThread...");
        workThread.pauseWhenSwitchIP();

        LogManager.info("Cancelling Running Request...");
        dispatcher.cancelAll();

        heartbeatManager.stopHeartBeat();

        monitor.printAllJobStatus();  //监控用

        switchProxyTillSuccuss();

        if (JobManagerConfig.reInitConfigAfterSwitchProxy) {
            JobManagerConfig.readConfigFromFile();
        }

        if (JobManagerConfig.reReadCookieAfterSwitchProxy) {
            CookieManager.clear();
        }

        if (JobManagerConfig.switchAgentAfterSwitchProxy) {
            UserAgentManager.switchIndex();
        }

        heartbeatManager.beginHeartBeat(ipProxyTool.getCurrentProxy());

        dispatcher.cancelAll();
        for (BaseSpider spider : dispatchedSpiderList) {
            IJob job = JobProvider.getJob();
            job.setRealRunnable(spider);
            if (job != null) {
                queue.putJob(job, IJob.STATE_INIT);
            }
        }
        dispatchedSpiderList.clear();

        blockHelper.reInit();
        managerMonitor.recordStartTime();
        LogManager.info("Resuming WorkThread...");
        workThread.resumeNow();
        isSwitchingIP.set(false);


    }

    public boolean ipSwitched(final Proxy proxy) {
        if (ipProxyTool.getCurrentProxy() == null) {
            return true;
        }
        return ipProxyTool.getCurrentProxy().equals(proxy);
    }

    public void onHeartBeatBegin() {

    }

    public void onHeartBeat(int time) {
        LogManager.info("onHeartBeat " + time);
    }


    public void onHeartBeatFail() {
        LogManager.info("onHeartBeatFail");
        dealBlock();
    }

    //JobManager主动调用HeartbeatManager.stopxxx
    public void onHeartBeatInterrupt() {
        LogManager.info("onHeartBeatInterrupt");
    }
}
