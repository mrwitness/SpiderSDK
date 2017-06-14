package wuxian.me.spidersdk.manager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.istack.internal.NotNull;
import okhttp3.Dispatcher;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.HttpUrlNode;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidercommon.util.FileUtil;
import wuxian.me.spidercommon.util.SignalManager;
import wuxian.me.spidersdk.BaseSpider;
import wuxian.me.spidersdk.IJobManager;
import wuxian.me.spidersdk.JobManagerConfig;
import wuxian.me.spidersdk.anti.*;
import wuxian.me.spidersdk.control.IQueue;
import wuxian.me.spidersdk.control.RedisJobQueue;
import wuxian.me.spidersdk.control.WorkThread;
import wuxian.me.spidersdk.distribute.*;
import wuxian.me.spidersdk.job.IJob;
import wuxian.me.spidersdk.job.JobProvider;
import wuxian.me.spidersdk.util.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by wuxian on 18/5/2017.
 * <p>
 * 分布式下(且没有身份的)的job manager
 */
public class DistributeJobManager implements IJobManager, HeartbeatManager.IHeartBeat, ProcessLifecycle {

    private Gson gson = new Gson();
    private IQueue queue;
    private ClassHelper.CheckFilter checkFilter;
    private WorkThread workThread = new WorkThread(this);
    private boolean started = false;

    private JobManagerMonitor monitor = new JobManagerMonitor();
    private HeartbeatManager heartbeatManager = new HeartbeatManager();
    private Dispatcher dispatcher = OkhttpProvider.getClient().dispatcher();

    private List<BaseSpider> successSpiderList = Collections.synchronizedList(new ArrayList<BaseSpider>());

    private List<BaseSpider> failureSpiderList = Collections.synchronizedList(new ArrayList<BaseSpider>());
    private BlockHelper blockHelper = new BlockHelper();

    private AtomicBoolean isSwitchingIP = new AtomicBoolean(false);
    private IPProxyTool ipProxyTool;//= new IPProxyTool();
    private boolean inited = false;

    //位于okHttpClient的缓存池中
    private List<BaseSpider> dispatchedSpiderList = Collections.synchronizedList(new ArrayList<BaseSpider>());

    private SignalManager processManager = new SignalManager();

    public List<BaseSpider> getDispatchedSpiderList() {
        return dispatchedSpiderList;
    }

    public void setSpiderChecker(@NotNull ClassHelper.CheckFilter filter) {
        this.checkFilter = filter;
    }

    public DistributeJobManager() {

    }

    protected void init() {

        LogManager.info("Begin To Init JobManager");

        processManager.init();

        ShellUtil.init();   //check ipProxy需要用到shell因此要先初始化

        LogManager.info("Begin To Collect Spiders...");
        checkAndColloectSubSpiders();

        String clazzStr = SpiderMethodManager.getSpiderClassString();
        if (clazzStr != null) {
            LogManager.info(clazzStr);
        }

        LogManager.info("Init RedisJobQueue...");
        queue = new RedisJobQueue();
        queue.init();

        processManager.registerOnSystemKill(new SignalManager.OnSystemKill() {
            public void onSystemKilled() {
                LogManager.error("DistributeJobManager, OnProcessKilled");
                DistributeJobManager.this.onPause();
            }
        });

        ipProxyTool = new IPProxyTool();
        ipProxyTool.init();
        if (ipProxyTool.currentProxy != null) {
            heartbeatManager.beginHeartBeat(ipProxyTool.currentProxy);
        }

        LogManager.info("JobManager Inited");

        Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                LogManager.error("uncaughtExceptionHandler e:" + e.getMessage());
                //onUncaughtException(t, e);
            }
        });

        onResume();
    }

    /**
     * 收集本jar包下的所有合法的@BaseSpider子类
     */
    private void checkAndColloectSubSpiders() {
        Set<Class<?>> classSet = null;
        if (JobManagerConfig.spiderScan != null) {
            classSet = ClassHelper.getSpiderFromPackage(JobManagerConfig.spiderScan);
        } else {
            classSet = ClassHelper.getSpiderFromJar(checkFilter);
        }

        if (classSet == null) {
            return;
        }
        for (Class<?> clazz : classSet) {
            SpiderMethodTuple tuple = SpiderClassChecker.performCheckAndCollect(clazz);
            if (tuple != null) {
                SpiderMethodManager.put(clazz, tuple);
            }
        }

    }

    public boolean ipSwitched(final Proxy proxy) {
        return ipProxyTool.currentProxy.equals(proxy);
    }

    public void success(Runnable runnable) {

        LogManager.info("Job Success: " + ((BaseSpider) runnable).name());
        dispatchedSpiderList.remove((BaseSpider) runnable);
        blockHelper.removeFail(runnable);
        failureSpiderList.remove((BaseSpider) runnable);

        successSpiderList.add((BaseSpider) runnable);
    }

    public void fail(@NotNull Runnable runnable, @NotNull Fail fail) {
        fail(runnable, fail, true);
    }

    public void fail(@NotNull Runnable runnable, @NotNull Fail fail, boolean retry) {

        //若是在switch ip,那么什么都不做
        if (!isSwitchingIP.get()) {
            blockHelper.addFail(runnable, fail);
            dispatchedSpiderList.remove(runnable);

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

    }

    public boolean putJob(@NotNull IJob job) {
        if (!started) {
            return false;
        }
        return queue.putJob(job, IJob.STATE_INIT);
    }

    public boolean putJob(@NotNull IJob job, boolean forceDispatch) {
        if (!started) {
            return false;
        }
        return queue.putJob(job, forceDispatch);
    }

    public IJob getJob() {
        if(!started) {
            return null;
        }
        IJob job = queue.getJob();
        return job;
    }

    public void onDispatch(@NotNull BaseSpider spider) {
        dispatchedSpiderList.add(spider);
    }

    public void start() {
        if (!inited) {
            init();
            inited = true;
        }

        if (!started) {
            started = true;
            heartbeatManager.addHeartBeatCallback(this);
            monitor.recordStartTime();

            LogManager.info("Starting WorkThread...");
            workThread.start();
        }
    }

    private void dealBlock() {
        if (JobManagerConfig.enableSwitchProxy) {
            LogManager.info("We begin to switch IP...");
            doSwitchIp();
        } else {        //被block后就停止了 --> 主要用于测试
            isSwitchingIP.set(true);

            LogManager.info("We will not switch IP ");
            dispatcher.cancelAll();
            workThread.pauseWhenSwitchIP();
        }
    }

    private void doSwitchIp() {
        isSwitchingIP.set(true);
        LogManager.info("Pausing WorkThread...");
        workThread.pauseWhenSwitchIP();

        LogManager.info("Cancelling Running Request...");
        dispatcher.cancelAll();

        heartbeatManager.stopHeartBeat();
        Proxy proxy = ipProxyTool.forceSwitchProxyTillSuccess();

        if(JobManagerConfig.reInitConfigAfterSwitchProxy) {
            JobManagerConfig.readConfigFromFile();  //Fixme: 这里修改的有些值是不能改的 比如说redis client
        }

        if(JobManagerConfig.reReadCookieAfterSwitchProxy) {
            CookieManager.clear();
        }

        if (JobManagerConfig.switchAgentAfterSwitchProxy) {
            UserAgentManager.switchIndex();
        }

        heartbeatManager.beginHeartBeat(proxy);

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
        monitor.recordStartTime();
        LogManager.info("Resuming WorkThread...");
        workThread.resumeNow();
        isSwitchingIP.set(false);
    }


    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void onHeartBeatBegin() {
        //暂时不用做什么
    }

    public void onHeartBeat(int time) {
        LogManager.info("onHeartBeat: " + time);
    }


    public void onHeartBeatFail() {
        LogManager.info("onHeartBeatFail,Proxy Is Not Working!");
        dealBlock();  //代理失效 --> 等同于被block
    }

    //JobManager主动调用HeartbeatManager.stopxxx
    public void onHeartBeatInterrupt() {
        LogManager.info("onHeartBeatInterrupt");
    }

    public void onResume() {
        if (JobManagerConfig.newSpideMode) {  //全新抓取模式 返回
            FileUtil.writeToFile(FileUtil.getCurrentPath() + JobManagerConfig.serializedSpiderFile, "");
            return;
        }

        if (!FileUtil.checkFileExist(FileUtil.getCurrentPath() + JobManagerConfig.serializedSpiderFile)) {
            FileUtil.writeToFile(FileUtil.getCurrentPath() + JobManagerConfig.serializedSpiderFile, "");
        }

        String spiderStr = FileUtil.readFromFile(FileUtil.getCurrentPath() + JobManagerConfig.serializedSpiderFile);

        if (spiderStr == null) {
            return;
        }
        FileUtil.writeToFile(FileUtil.getCurrentPath() + JobManagerConfig.serializedSpiderFile, "");
        List<HttpUrlNode> nodeList = gson.fromJson(spiderStr, new TypeToken<List<HttpUrlNode>>() {
        }.getType());

        if (nodeList == null) {
            return;
        }

        for (HttpUrlNode urlNode : nodeList) {
            ((RedisJobQueue) queue).putJob(urlNode);
        }
    }

    public void onPause() {
        workThread.pauseWhenSwitchIP();
        dispatcher.cancelAll();

        if (JobManagerConfig.enableSeriazeSpider) {
            LogManager.info("Try Serialize SpiderList");
            List<HttpUrlNode> spiderList = new ArrayList<HttpUrlNode>();
            for (BaseSpider spider : dispatchedSpiderList) {
                HttpUrlNode str = spider.toUrlNode();
                if (str != null) {
                    spiderList.add(str);
                }
            }
            String spiderString = gson.toJson(spiderList);
            FileUtil.writeToFile(FileUtil.getCurrentPath() + JobManagerConfig.serializedSpiderFile, spiderString);

            LogManager.info("Serialize SpiderList Success");
        }
    }
}
