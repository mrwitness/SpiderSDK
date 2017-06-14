package wuxian.me.spidersdk;

import org.junit.Test;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.util.ProcessUtil;
import wuxian.me.spidersdk.distribute.ClassHelper;
import wuxian.me.spidersdk.job.IJob;
import wuxian.me.spidersdk.job.JobProvider;
import wuxian.me.spidersdk.manager.JobManagerFactory;
import wuxian.me.spidersdk.util.ShellUtil;

import java.util.Set;

/**
 * Created by wuxian on 12/5/2017.
 */
public class MainTest {

    @Test
    public void testIpproxyTool() {
        //new IPProxyTool();
        JobManagerFactory.getJobManager().start();
    }

    @Test
    public void testScanPackage() {
        System.out.println("begin to scan");
        Set<Class<?>> classSet = ClassHelper.getSpiderFromPackage(JobManagerConfig.spiderScan);

        if (classSet != null) {
            for (Class clazz : classSet) {
                System.out.println(clazz);
            }
        }
    }

    @Test
    public void testCurrentProcessId() {
        ShellUtil.init();
        System.out.println("begin");
        System.out.println(ShellUtil.killProcessBy(ProcessUtil.getCurrentProcessId()));
        while (true) {
            ;
        }
    }

    @Test
    public void testRedisJobQueue() {

        IJob job = JobProvider.getJob();
        NoneSpider spider = new NoneSpider();
        job.setRealRunnable(spider);
        JobManagerFactory.getJobManager().start();
        JobManagerFactory.getJobManager().putJob(job);

        try {
            Thread.sleep(1000);
        } catch (Exception e) {

        }

        job = JobManagerFactory.getJobManager().getJob();

        LogManager.info("job: " + job);

        while (true) {

        }

    }

}