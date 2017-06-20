package wuxian.me.spidersdk;

import org.junit.Test;
import wuxian.me.spidersdk.distribute.ClassHelper;
import wuxian.me.spidersdk.manager.JobManagerFactory;
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

}