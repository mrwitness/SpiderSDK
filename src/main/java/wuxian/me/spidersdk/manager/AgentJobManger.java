package wuxian.me.spidersdk.manager;

import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.HttpUrlNode;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidercommon.util.ProcessUtil;
import wuxian.me.spidercommon.util.ShellUtil;
import wuxian.me.spidermaster.biz.agent.SpiderAgent;
import wuxian.me.spidermaster.biz.provider.Resource;
import wuxian.me.spidermaster.framework.agent.request.IRpcCallback;
import wuxian.me.spidermaster.framework.common.GsonProvider;
import wuxian.me.spidermaster.framework.rpc.RpcResponse;
import wuxian.me.spidersdk.JobManagerConfig;
import wuxian.me.spidersdk.distribute.SpiderMethodManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by wuxian on 18/5/2017.
 * <p>
 * 分布式模式下的agent jobManager
 */
public class AgentJobManger extends DistributeJobManager {

    private SpiderAgent agent;

    private boolean registerSuccess = false;

    @Override
    protected void init() {
        registerSuccess = false;
        super.init();

        SpiderAgent.init();
        agent = new SpiderAgent();

        LogManager.info("start spider agent");
        agent.start();


        List<Class<?>> clazzList = new ArrayList<Class<?>>();
        for (Class<?> clz : SpiderMethodManager.getSpiderClasses()) {
            clazzList.add(clz);
        }

        List<HttpUrlNode> urlList = new ArrayList<HttpUrlNode>(clazzList.size());
        for (int i = 0; i < clazzList.size(); i++) {
            HttpUrlNode node = new HttpUrlNode();
            node.baseUrl = "hello_world";

            urlList.add(node);
        }

        LogManager.info("spider agent begin to registToMaster");
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        agent.registerToMaster(clazzList, urlList, new IRpcCallback() {
            public void onSent() {

            }

            public void onResponseSuccess(RpcResponse rpcResponse) {

                LogManager.info("spider agent registerToMaster success");
                countDownLatch.countDown();

                registerSuccess = true;
            }

            public void onResponseFail() {

                countDownLatch.countDown();
            }

            public void onTimeout() {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {

        }

        if (!registerSuccess) {
            LogManager.error("seems agent fail to register to master ! " +
                    "we will shut down the whole process..."); //Fixme:这时候应该关闭程序?
            ShellUtil.killProcessBy(ProcessUtil.getCurrentProcessId());
        }
    }

    private Proxy currentProxy = null;

    public boolean ipSwitched(final Proxy proxy) {

        if (currentProxy == null) {
            return true;
        }
        return currentProxy.equals(proxy);
    }

    private Proxy tmpProxy = null;

    protected Proxy getProxyTillSuccuss() {
        tmpProxy = null;
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        LogManager.info("begin request proxy from master...");
        agent.requestProxy(new IRpcCallback() {
            public void onSent() {

            }

            public void onResponseSuccess(RpcResponse rpcResponse) {

                Resource resource = GsonProvider.gson().fromJson((String) rpcResponse.result, Resource.class);

                if (resource != null) {

                    LogManager.info("RequestProxy.onResponsSuccess " + resource.toString());

                    Proxy proxy = GsonProvider.gson().fromJson((String) resource.data, Proxy.class);
                    if (proxy != null) {

                        LogManager.info("getProxy: " + proxy.toString());
                        tmpProxy = proxy;
                    }
                }

                countDownLatch.countDown();
            }

            public void onResponseFail() {

                countDownLatch.countDown();
            }

            public void onTimeout() {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            ;
        }

        if (tmpProxy != null) {

            int ensure = 0;
            boolean success = false;
            while (!(success = ipProxyTool.ipSwitched(tmpProxy)) && ensure < JobManagerConfig.everyProxyTryTime) {  //每个IP尝试三次
                ensure++;
                LogManager.info("Switch Proxy Fail Times: " + ensure);
            }

            if (success) {
                getProxyTime = 0;
                currentProxy = tmpProxy;
                return tmpProxy;
            }
        }

        getProxyTime++;

        try {
            getProxyTime = getProxyTime >= 3 ? 3 : getProxyTime;

            LogManager.info("get valid proxy fail,sleep "
                    + getProxyTime * 5 + " seconds then try again...");

            Thread.sleep(getProxyTime * 5 * 1000);  //每失败多一次 多sleep 10s,最多休息120s

        } catch (InterruptedException e) {
            ;
        }

        return getProxyTillSuccuss();  //if fail,try again
    }

    private int getProxyTime = 0;
}
