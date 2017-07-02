package wuxian.me.spidersdk.manager;

import wuxian.me.spidersdk.IJobManager;
import wuxian.me.spidersdk.JobManagerConfig;

/**
 * Created by wuxian on 18/5/2017.
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

    private static IJobManager getDistrubeJobManager() {
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

        if (JobManagerConfig.isAgent) {
            return getAgentJobManager();
        }

        if (JobManagerConfig.distributeMode) {
            return getDistrubeJobManager();
        } else {

            return getPlainJobManager();
        }
    }

}
