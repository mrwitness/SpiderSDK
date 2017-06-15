package wuxian.me.spidersdk;

import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.util.FileUtil;
import wuxian.me.spidersdk.distribute.ClassHelper;
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

        JobManagerFactory.initCheckFilter(new ClassHelper.CheckFilter() {
            public boolean apply(String s) {
                boolean ret = true;
                if (s.contains("org/")) {
                    ret = false;
                } else if (s.contains("google")) {
                    ret = false;
                } else if (s.contains("squareup")) {
                    ret = false;
                }
                return ret;
            }
        });
    }

    public void start() {

        //JobManagerFactory.getJobManager().start();
    }

    public static void main(String[] args) {

        Main main = new Main();
        main.init();
        main.start();
    }
}
