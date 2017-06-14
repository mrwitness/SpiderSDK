package wuxian.me.spidersdk;

import wuxian.me.spidersdk.anti.Fail;
import wuxian.me.spidersdk.job.IJob;

/**
 * Created by wuxian on 31/3/2017.
 * <p>
 * 目前BaseJob的设计是:hashCode,equals都是调用的BaseSpider
 */
public abstract class BaseJob implements IJob {

    public final void fail(Fail fail) {
        //Do Nothing
    }

    private int state = STATE_INIT;

    public final int getCurrentState() {
        return state;
    }

    public final void setCurrentState(int state) {
        this.state = state;
    }

    protected Runnable realJob;

    public final void setRealRunnable(Runnable runnable) {
        realJob = runnable;
    }

    public final Runnable getRealRunnable() {
        return realJob;
    }

    @Override
    public int hashCode() {
        if (realJob != null) {
            return realJob.hashCode();
        }
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj instanceof BaseJob) {
            boolean ret = realJob.equals(((BaseJob) obj).realJob);
            return ret;
        }
        return super.equals(obj);
    }

    @Override
    public String toString() {
        if (realJob == null) {
            return "Invalid Job";
        }
        return "Job State: " + state + " " + realJob.toString();
    }
}
