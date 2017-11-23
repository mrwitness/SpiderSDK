package wuxian.me.spidersdk.util;

/**
 * Created by wuxian on 20/5/2017.
 * <p>
 * 进程恢复,进程被杀死时调用一下
 */
public interface ProcessLifecycle {

    void onResume();

    void onPause();
}
