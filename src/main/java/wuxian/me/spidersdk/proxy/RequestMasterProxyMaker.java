package wuxian.me.spidersdk.proxy;

import com.sun.istack.internal.NotNull;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidermaster.biz.agent.SpiderAgent;
import wuxian.me.spidermaster.biz.provider.Resource;
import wuxian.me.spidermaster.framework.agent.request.IRpcCallback;
import wuxian.me.spidermaster.framework.common.GsonProvider;
import wuxian.me.spidermaster.framework.rpc.RpcResponse;

import java.util.concurrent.CountDownLatch;

/**
 * Created by wuxian on 23/7/2017.
 * <p>
 * 通过向master申请的方式来获取一个proxy。
 */
public class RequestMasterProxyMaker implements IProxyMaker {
    private SpiderAgent agent;

    public RequestMasterProxyMaker(@NotNull SpiderAgent agent) {

        this.agent = agent;
    }

    private Proxy tmpProxy = null;

    public synchronized Proxy make() {

        return make(1);
    }

    public synchronized Proxy makeUntilSuccess() {
        requestOneProxy();
        return tmpProxy != null ? tmpProxy : makeUntilSuccess();
    }

    private void requestOneProxy() {
        if(agent == null) {
            return;
        }
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
    }

    public synchronized Proxy make(int tryTime) {
        if(tryTime <= 0) {
            return null;
        }
        requestOneProxy();
        tryTime --;
        return tmpProxy != null? tmpProxy:make(tryTime);
    }
}
