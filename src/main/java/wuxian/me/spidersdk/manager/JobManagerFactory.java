package wuxian.me.spidersdk.manager;

import com.sun.istack.internal.NotNull;
import wuxian.me.spidersdk.IJobManager;
import wuxian.me.spidersdk.JobManagerConfig;
import wuxian.me.spidersdk.distribute.ClassHelper;

/**
 * Created by wuxian on 18/5/2017.
 * <p>
 * 根据配置文件的选项给工作模式不同的jobmanager
 * 1 单机模式
 * 2 分布式(没有身份)
 * 3 分布式下的master  --> 未实现
 * 4 分布式下的agent   --> 未实现
 */
public class JobManagerFactory {

    private static PlainJobManager plainJobManager;
    private static DistributeJobManager normalJobManager;
    private static AgentJobManger agentJobManger;

    private JobManagerFactory() {

    }

    public static IJobManager getAgentJobManager() {
        if (agentJobManger == null) {
            synchronized (JobManagerFactory.class) {
                if (agentJobManger == null) {
                    agentJobManger = new AgentJobManger();
                }
            }
        }
        return agentJobManger;
    }

    public static IJobManager getPlainJobManager() {
        if (plainJobManager == null) {
            synchronized (JobManagerFactory.class) {
                if (plainJobManager == null) {
                    plainJobManager = new PlainJobManager();
                }
            }
        }
        return plainJobManager;
    }

    private static IJobManager getNormalJobManager() {
        if (normalJobManager == null) {
            synchronized (JobManagerFactory.class) {
                if (normalJobManager == null) {
                    normalJobManager = new DistributeJobManager();
                }
            }
        }
        return normalJobManager;
    }

    public static IJobManager getJobManager() {
        if(!JobManagerConfig.distributeMode){
            return getPlainJobManager();
        } else {

            if (JobManagerConfig.isAgent) {
                return getAgentJobManager();

            } else if (JobManagerConfig.isMaster) {

            }

            return getNormalJobManager();

        }
    }

    //Called by biz service
    public static void initCheckFilter(@NotNull ClassHelper.CheckFilter filter) {
        ((DistributeJobManager) getNormalJobManager()).setSpiderChecker(filter);
    }

}
