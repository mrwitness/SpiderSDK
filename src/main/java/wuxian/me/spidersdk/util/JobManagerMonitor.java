package wuxian.me.spidersdk.util;

/**
 * Created by wuxian on 19/5/2017.
 */
public class JobManagerMonitor {

    private long startTime;
    private long stopTime;

    public JobManagerMonitor(){
        reset();
    }

    public void recordStartTime(){
        startTime = System.currentTimeMillis();
    }

    public void recordStopTime(){
        stopTime = System.currentTimeMillis();
    }

    public void reset(){
        startTime = -1;
        stopTime = -1;
    }
}
