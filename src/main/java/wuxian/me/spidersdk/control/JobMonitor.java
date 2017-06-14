package wuxian.me.spidersdk.control;

import com.sun.istack.internal.NotNull;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidersdk.BaseSpider;
import wuxian.me.spidersdk.job.IJob;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wuxian on 7/4/2017.
 * 所有Job的状态表
 */
public class JobMonitor {

    private Map<Runnable, IJob> jobMap = new ConcurrentHashMap<Runnable, IJob>();

    public JobMonitor() {
    }

    public void putJob(@NotNull IJob job, int state) {
        job.setCurrentState(state);
        jobMap.put(job.getRealRunnable(), job);
    }

    public IJob getJob(@NotNull Runnable runnable) {

        if (jobMap.containsKey(runnable)) {
            return jobMap.get(runnable);
        }
        return null;
    }

    public boolean contains(@NotNull IJob job) {
        return jobMap.containsKey(job.getRealRunnable());
    }

    public int getWholeJobNum() {
        return jobMap.size();
    }

    private String getClassNameOfJob(@NotNull IJob job) {
        return job.getRealRunnable().getClass().getSimpleName();
    }

    private void increaseNum(@NotNull Map<String, Integer> map, @NotNull String key) {
        if (!map.containsKey(key)) {
            map.put(key, 1);
        } else {
            int num = map.get(key);
            map.put(key, num + 1);
        }
    }

    private String printMap(Map<String, Integer> map) {
        StringBuilder builder = new StringBuilder("");
        for (String str : map.keySet()) {
            builder.append(str + ":" + map.get(str) + " ,");
        }
        return builder.toString();
    }

    public String printAllJobStatus() {
        Map<String, Integer> init = new HashMap<String, Integer>();
        Map<String, Integer> success = new HashMap<String, Integer>();
        Map<String, Integer> fail = new HashMap<String, Integer>();
        Map<String, Integer> retry = new HashMap<String, Integer>();

        synchronized (jobMap) {
            for (Runnable runnable : jobMap.keySet()) {
                IJob job = jobMap.get(runnable);
                if (job == null) {
                    LogManager.error("Can't find job in JobMap for runnable: "
                            + ((BaseSpider) runnable).name());
                    continue;
                }
                String name = getClassNameOfJob(job);
                switch (job.getCurrentState()) {
                    case IJob.STATE_INIT:
                        increaseNum(init, name);
                        break;
                    case IJob.STATE_SUCCESS:
                        increaseNum(success, name);
                        break;
                    case IJob.STATE_FAIL:
                        increaseNum(fail, name);
                        break;
                    case IJob.STATE_RETRY:
                        increaseNum(retry, name);
                        break;

                }
            }
            return "Init Jobs: " + printMap(init) + " Success Jobs: " + printMap(success)
                    + " Fail Jobs: " + printMap(fail) + " Retry Jobs: " + printMap(retry);
        }
    }
}
