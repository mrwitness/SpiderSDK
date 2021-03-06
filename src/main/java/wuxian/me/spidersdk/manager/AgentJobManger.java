package wuxian.me.spidersdk.manager;

import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.HttpUrlNode;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidercommon.util.ProcessUtil;
import wuxian.me.spidercommon.util.ShellUtil;
import wuxian.me.spidermaster.biz.agent.SpiderAgent;
import wuxian.me.spidermaster.biz.agent.provider.ProviderScanner;
import wuxian.me.spidermaster.biz.provider.Resource;
import wuxian.me.spidermaster.framework.agent.request.IRpcCallback;
import wuxian.me.spidermaster.framework.common.GsonProvider;
import wuxian.me.spidermaster.framework.rpc.RpcResponse;
import wuxian.me.spidersdk.JobManagerConfig;
import wuxian.me.spidersdk.distribute.SpiderMethodManager;
import wuxian.me.spidersdk.proxy.IProxyMaker;
import wuxian.me.spidersdk.proxy.RequestMasterProxyMaker;

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

    protected IProxyMaker getProxyMaker() {
        return new RequestMasterProxyMaker(agent);
    }

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
            node.baseUrl = "";   //Todo:没有意义
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
                    "we will shut down the whole process...");
            ShellUtil.killProcessBy(ProcessUtil.getCurrentProcessId());
        }

        if(JobManagerConfig.enableSwitchProxy && JobManagerConfig.switchProxyWhenStart) {
            switchProxyTillSuccuss();
        }
    }

    public boolean ipSwitched(final Proxy proxy) {

        if (ipProxyTool.getCurrentProxy() == null) {
            return true;
        }
        return ipProxyTool.getCurrentProxy().equals(proxy);
    }


    //如果本agent是一个Proxy的IProvider,那么先从本地拿
    private Proxy getLocalProxyIfPossible() {
        Resource resource = ProviderScanner.provideResource("proxy");
        if(resource != null) {
            try{
                Proxy proxy = GsonProvider.gson().fromJson((String)resource.data,Proxy.class);

                return proxy;
            } catch (Exception e) {

            }
        }
        return null;
    }

    protected void switchProxyTillSuccuss() {
        if (proxyMaker == null) {
            proxyMaker = getProxyMaker();
        }
        boolean switchSuccess = false;
        do {

            Proxy proxy = getLocalProxyIfPossible();
            if(proxy == null) {
                proxy = proxyMaker.make(2);
            }

            if(proxy == null) {
                break;
            }
            ipProxyTool.switchToProxy(proxy);

            int ensure = 0;

            while (ensure < JobManagerConfig.everyProxyTryTime && !(switchSuccess = ipProxyTool.isIpSwitchedSuccess(proxy))) {  //每个IP尝试三次
                ensure++;
                LogManager.info("Switch Proxy Fail Times: " + ensure);
            }

        } while (!switchSuccess);

        if (switchSuccess) {
            getProxyTime = 0;
            return;
        }

        getProxyTime++;
        try {
            getProxyTime = getProxyTime >= 1 ? 1 : getProxyTime;

            LogManager.info("get valid proxy fail,sleep "
                    + getProxyTime * 5 + " seconds then try again...");

            Thread.sleep(getProxyTime * 5 * 1000);

        } catch (InterruptedException e) {
            ;
        }
        switchProxyTillSuccuss();//if fail,try again
    }

    private int getProxyTime = 0;
}
