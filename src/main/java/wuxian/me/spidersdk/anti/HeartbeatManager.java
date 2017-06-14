package wuxian.me.spidersdk.anti;

import com.sun.istack.internal.NotNull;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidersdk.manager.JobManagerFactory;
import wuxian.me.spidersdk.manager.PlainJobManager;
import wuxian.me.spidersdk.JobManagerConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wuxian on 20/4/2017.
 * <p>
 * 每隔一段时间去ping一下http://www.ip138.com 确保proxy有效
 */
public class HeartbeatManager implements Runnable {

    long frequency;

    private Thread heartbeatThread = null;
    private int heartBeatTime = 0;
    private Proxy proxy;

    public HeartbeatManager() {
        frequency = JobManagerConfig.proxyHeartbeatInterval;

    }

    public void setHeartbeatFrequency(long frequency) {
        this.frequency = frequency;
    }

    public void beginHeartBeat(Proxy proxy) {
        this.proxy = proxy;

        for (IHeartBeat heartBeat : heartBeatList) {
            heartBeat.onHeartBeatBegin();
        }

        heartbeatThread = new Thread(this);
        heartbeatThread.start();

    }

    //这个方法是给外部强制停止heartbeat用的 比如判定自己被屏蔽了 需要进行切换ip操作
    public void stopHeartBeat() {
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
    }

    private List<IHeartBeat> heartBeatList = new ArrayList<IHeartBeat>();

    public void addHeartBeatCallback(@NotNull IHeartBeat heartBeat) {
        heartBeatList.add(heartBeat);
    }

    public void run() {
        if (!JobManagerConfig.enableProxyHeartbeat) {
            return;
        }

        boolean proxyLive = true;
        while (!Thread.interrupted()) {
            try {
                Thread.sleep(frequency);

            } catch (InterruptedException e) {
                break;
            }

            heartBeatTime++;
            for (IHeartBeat heartBeat : heartBeatList) {
                heartBeat.onHeartBeat(heartBeatTime);
            }

            proxyLive = JobManagerFactory.getJobManager().ipSwitched(proxy);  //检查下当前的proxy是否是正在使用的proxy
            if (!proxyLive) {
                for (IHeartBeat heartBeat : heartBeatList) {
                    heartBeat.onHeartBeatFail();
                }
                return;
            }
        }
        for (IHeartBeat heartBeat : heartBeatList) {
            heartBeat.onHeartBeatInterrupt();
        }
    }


    public interface IHeartBeat {

        void onHeartBeatBegin();

        //这是第几个心跳包
        void onHeartBeat(int time);

        //没有对应的heartBeatSuccess,因为没有意义
        void onHeartBeatFail();

        void onHeartBeatInterrupt();
    }

}
