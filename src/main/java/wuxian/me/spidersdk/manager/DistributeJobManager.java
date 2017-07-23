package wuxian.me.spidersdk.manager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.istack.internal.NotNull;
import okhttp3.Dispatcher;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.HttpUrlNode;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidercommon.util.ClassHelper;
import wuxian.me.spidercommon.util.FileUtil;
import wuxian.me.spidercommon.util.ShellUtil;
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
import wuxian.me.spidersdk.proxy.HandInputProxyMaker;
import wuxian.me.spidersdk.proxy.IProxyMaker;
import wuxian.me.spidersdk.util.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by wuxian on 18/5/2017.
 * <p>
 * 分布式下(且没有身份的)的job manager
 */
public class DistributeJobManager implements IJobManager, HeartbeatManager.IHeartBeat, ProcessLifecycle {

    protected IProxyMaker proxyMaker;
    private Gson gson = new Gson();
    private IQueue queue;
    private WorkThread workThread = new WorkThread(this);
    private boolean started = false;

    private JobManagerMonitor monitor = new JobManagerMonitor();
    private HeartbeatManager heartbeatManager = new HeartbeatManager();
    private Dispatcher dispatcher = OkhttpProvider.getClient().dispatcher();

    private List<BaseSpider> successSpiderList = Collections.synchronizedList(new ArrayList<BaseSpider>());

    private List<BaseSpider> failureSpiderList = Collections.synchronizedList(new ArrayList<BaseSpider>());
    private BlockHelper blockHelper = new BlockHelper();

    private AtomicBoolean isSwitchingIP = new AtomicBoolean(false);
    protected IPProxyTool ipProxyTool;
    private boolean inited = false;

    //位于okHttpClient的发送对列
    private List<BaseSpider> dispatchedSpiderList = Collections.synchronizedList(new ArrayList<BaseSpider>());

    private SignalManager processManager = new SignalManager();

    public List<BaseSpider> getDispatchedSpiderList() {
        return dispatchedSpiderList;
    }

    public DistributeJobManager() {

    }

    @NotNull
    protected IProxyMaker getProxyMaker() {
        return new HandInputProxyMaker();
    }

    protected void init() {

        LogManager.info("in DistributeJobManger.init");

        LogManager.info("init processManager");
        processManager.init();

        LogManager.info("init shellutil");
        ShellUtil.init();   //check ipProxy需要用到shell因此要先初始化

        LogManager.info("begin to find valid sub spiders");

        checkAndColloectSubSpiders(JobManagerConfig.spiderScan);

        LogManager.info(SpiderMethodManager.getSpiderClassString());

        LogManager.info("init redis jobqueue");
        queue = new RedisJobQueue();
        queue.init();

        processManager.registerOnSystemKill(new SignalManager.OnSystemKill() {
            public void onSystemKilled() {
                LogManager.error("DistributeJobManager, OnProcessKilled");
                DistributeJobManager.this.onPause();
            }
        });

        LogManager.info("init ipproxyTool");
        ipProxyTool = new IPProxyTool();
        ipProxyTool.init();

        if (ipProxyTool.getCurrentProxy() != null) {
            LogManager.info("HeartbeatManager begin heartbeat");
            heartbeatManager.beginHeartBeat(ipProxyTool.getCurrentProxy());
        }

        onResume();

        LogManager.info("DistributedJobManager.init END");
    }

    /**
     * 收集本jar包下的所有合法的@BaseSpider子类
     */
    public void checkAndColloectSubSpiders(String s) {

        Set<Class<?>> classSet = new HashSet<Class<?>>();
        if (s != null) {

            List<String> pkList = new ArrayList<String>();

            if (!s.contains(";")) {
                pkList.add(s);
            } else {
                String[] list = s.split(";");
                for (int i = 0; i < list.length; i++) {
                    pkList.add(list[i]);
                }
            }

            for (String pk : pkList) {
                Set<Class<?>> list = getSpidersUnder(pk);
                list.removeAll(classSet);
                classSet.addAll(list);
            }

        } else {
            throw new JobManagerInitErrorException("SpiderScan in jobmanager.properties isnot set");
        }

        if (classSet == null || classSet.size() == 0) {
            return;
        }
        for (Class<?> clazz : classSet) {
            SpiderMethodTuple tuple = SpiderClassChecker.performCheckAndCollect(clazz);
            if (tuple != null) {
                SpiderMethodManager.put(clazz, tuple);
            }
        }

    }

    private Set<Class<?>> getSpidersUnder(String packageName) {
        return ClassHelper.getSpiderFromPackage(packageName);
    }

    public boolean ipSwitched(final Proxy proxy) {

        if (ipProxyTool.getCurrentProxy() == null) {
            return true;
        }
        return ipProxyTool.getCurrentProxy().equals(proxy);
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
        if (!started) {
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
            LogManager.info("in DistributeJobManager.start");

            started = true;
            heartbeatManager.addHeartBeatCallback(this);
            monitor.recordStartTime();

            LogManager.info("start workThread");
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

    protected void switchProxyTillSuccuss() {
        if (proxyMaker == null) {
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
        switchProxyTillSuccuss();

        if (JobManagerConfig.reInitConfigAfterSwitchProxy) {
            JobManagerConfig.readConfigFromFile();  //Fixme: 这里修改的有些值是不能改的 比如说redis client
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

        if (!FileUtil.checkFileExist(FileUtil.getCurrentPath() + JobManagerConfig.serializedSpiderFile)) {
            FileUtil.writeToFile(FileUtil.getCurrentPath() + JobManagerConfig.serializedSpiderFile, "");
        }

        String spiderStr = FileUtil.readFromFile(FileUtil.getCurrentPath() + JobManagerConfig.serializedSpiderFile);

        if (spiderStr == null || spiderStr.length() == 0) {
            return;
        }

        LogManager.info("read serialized spiders from local file system: " + spiderStr);

        FileUtil.writeToFile(FileUtil.getCurrentPath() + JobManagerConfig.serializedSpiderFile, "");
        List<HttpUrlNode> nodeList = gson.fromJson(spiderStr, new TypeToken<List<HttpUrlNode>>() {
        }.getType());

        if (nodeList == null) {
            return;
        }

        LogManager.info("put back urlnode from spiders");
        for (HttpUrlNode urlNode : nodeList) {
            LogManager.info(urlNode.toString());
            ((RedisJobQueue) queue).putJob(urlNode);
        }
        LogManager.info("put back urlnodes finished");
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
