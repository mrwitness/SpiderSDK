package wuxian.me.spidersdk.manager;

import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.HttpUrlNode;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidermaster.biz.agent.SpiderAgent;
import wuxian.me.spidermaster.biz.provider.Resource;
import wuxian.me.spidermaster.framework.agent.request.IRpcCallback;
import wuxian.me.spidermaster.framework.common.GsonProvider;
import wuxian.me.spidermaster.framework.rpc.RpcResponse;
import wuxian.me.spidersdk.distribute.SpiderMethodManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Created by wuxian on 18/5/2017.
 * <p>
 * 分布式模式下的agent jobManager
 */
public class AgentJobManger extends DistributeJobManager {

    private SpiderAgent agent;

    @Override
    protected void init() {
        super.init();

        SpiderAgent.init();
        agent = new SpiderAgent();
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
        agent.registerToMaster(clazzList, urlList);
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

        agent.requestProxy(new IRpcCallback() {
            public void onSent() {

            }

            public void onResponseSuccess(RpcResponse rpcResponse) {

                Resource resource = GsonProvider.gson().fromJson((String) rpcResponse.result, Resource.class);

                if (resource != null) {

                    LogManager.info("AgentJobManager.onResponsSuccess " + resource.toString());

                    Proxy proxy = GsonProvider.gson().fromJson((String) resource.data, Proxy.class);
                    if (proxy != null) {
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
            currentProxy = tmpProxy;
            return tmpProxy;
        }

        //Todo make sure proxy is valid
        return getProxyTillSuccuss();  //if fail,try again
    }
}
