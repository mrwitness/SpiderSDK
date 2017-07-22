package wuxian.me.spidersdk;

import org.junit.Test;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidersdk.anti.IPProxyTool;
import wuxian.me.spidersdk.manager.JobManagerFactory;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Created by wuxian on 12/5/2017.
 */
public class MainTest {

    @Test
    public void testIpproxyTool() {

        JobManagerConfig.init();
        IPProxyTool tool = new IPProxyTool();

        JobManagerConfig.okhttpClientSocketReadTimeout = 60 * 1000;
        JobManagerConfig.enableInitProxyFromFile = false;
        tool.init();

        tool.putProxy(new Proxy("115.213.203.157", 808));


        Proxy proxy = tool.switchNextProxy();
        int ensure = 0;
        boolean success = false;
        while (!(success = tool.ipSwitched(proxy)) && ensure < 3) {  //每个IP尝试三次
            ensure++;
            LogManager.info("Switch Proxy Fail Times: " + ensure);
        }

        if (success) {
            LogManager.info("success");
        } else {
            LogManager.info("fail");
        }


        /*
        FutureTask<String> future = tool.getFuture();
        new Thread(future).start();

       try{

           if (future.get() == null) {
               LogManager.error("future.get is null");
           } else {
               LogManager.info(future.get());
           }
       } catch (InterruptedException e) {
           LogManager.info("test interrupted "+e.getMessage());
           e.printStackTrace();
       } catch (ExecutionException e) {
           LogManager.info("test executionException "+e.getMessage());
           e.printStackTrace();
       }
       */


        while (true) {

        }

    }


}