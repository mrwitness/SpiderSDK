package wuxian.me.spidersdk;

import org.junit.Test;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidersdk.anti.IPProxyTool;
import wuxian.me.spidersdk.proxy.HandInputProxyMaker;
import wuxian.me.spidersdk.proxy.IProxyMaker;


/**
 * Created by wuxian on 12/5/2017.
 */
public class MainTest {

    @Test
    public void testProxymaker() {
        JobManagerConfig.init();
        IProxyMaker proxyMaker = new HandInputProxyMaker();
        System.out.println(proxyMaker.makeUntilSuccess().toString());
    }

    @Test
    public void testIpproxyTool() {

        JobManagerConfig.init();
        IPProxyTool tool = new IPProxyTool();

        JobManagerConfig.okhttpClientSocketReadTimeout = 60 * 1000;
        JobManagerConfig.enableInitProxyFromFile = false;
        tool.init();

        Proxy proxy = new Proxy("115.213.203.157", 808);
        tool.switchToProxy(proxy);
        int ensure = 0;
        boolean success = false;
        while (!(success = tool.isIpSwitchedSuccess(proxy)) && ensure < 3) {
            ensure++;
            LogManager.info("Switch Proxy Fail Times: " + ensure);
        }

        if (success) {
            LogManager.info("success");
        } else {
            LogManager.info("fail");
        }

        while (true) {

        }
    }

}