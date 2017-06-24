package wuxian.me.spidersdk;

import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidersdk.manager.JobManagerFactory;

/**
 * Created by wuxian on 12/5/2017.
 */
public class Main {

    public Main() {
        ;
    }

    public void init() {
        LogManager.info("Main_static Begin.");

        JobManagerConfig.init();
    }

    public void start() {
        JobManagerFactory.getJobManager().start();
    }

    public static void main(String[] args) {

        Main main = new Main();
        main.init();
        main.start();
    }
}
