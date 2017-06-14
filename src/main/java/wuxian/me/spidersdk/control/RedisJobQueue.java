package wuxian.me.spidersdk.control;

import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.HttpUrlNode;
import wuxian.me.spidersdk.BaseSpider;
import wuxian.me.spidersdk.JobManagerConfig;
import wuxian.me.spidersdk.distribute.*;
import wuxian.me.spidersdk.job.IJob;
import wuxian.me.spidersdk.job.JobProvider;
import wuxian.me.spidersdk.util.ShellUtil;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by wuxian on 10/5/2017.
 * <p>
 * 使用url存入RedisJobQueue的方式,pull的时候加一层解析的方式来实现分布式JobQueue
 */
public class RedisJobQueue implements IQueue {

    private static final String JOB_QUEUE = "jobqueue";

    //HttpUrlNode pattern --> Class
    private Map<Long, Class> urlPatternMap = new HashMap<Long, Class>();
    private List<Long> unResolveList = new ArrayList<Long>();

    private Jedis jedis;
    private Gson gson;


    public RedisJobQueue() {

    }

    public void init() {
        gson = new Gson();
        boolean redisRunning = false;
        try {
            redisRunning = ShellUtil.isRedisServerRunning();
        } catch (IOException e) {
            ;
        }
        LogManager.info("Check RedisServer running: " + redisRunning);

        if (!redisRunning) {
            throw new RedisConnectionException();
        }

        LogManager.info("Init Jedis client...");
        jedis = new Jedis(JobManagerConfig.redisIp,
                Ints.checkedCast(JobManagerConfig.redisPort));

        try {
            jedis.exists(JOB_QUEUE);
        } catch (JedisConnectionException e) {
            LogManager.error("JedisConnectionException e:" + e.getMessage());
        }

        LogManager.info("RedisJobQueue Inited");

    }

    public boolean putJob(IJob job, boolean forceDispatch) {
        if (!JobManagerConfig.enablePutSpiderToQueue) {
            return false;
        }

        BaseSpider spider = (BaseSpider) job.getRealRunnable();
        HttpUrlNode urlNode = spider.toUrlNode();

        if (urlNode == null) {
            return false;
        }

        String key = String.valueOf(urlNode.toRedisKey());
        jedis.set(key, "true");
        String json = gson.toJson(urlNode);
        jedis.lpush(JOB_QUEUE, json);

        LogManager.info("Success Put Spider: " + spider.name());
        return true;
    }

    //抛弃state --> 分布式下没法管理一个job的状态:是新开始的任务还是重试的任务
    public boolean putJob(IJob job, int state) {

        if (!JobManagerConfig.enablePutSpiderToQueue) {
            return false;
        }

        BaseSpider spider = (BaseSpider) job.getRealRunnable();
        HttpUrlNode urlNode = spider.toUrlNode();

        if (urlNode == null) {
            return false;
        }
        LogManager.info("try to Put Spider");
        if (state == IJob.STATE_INIT) {
            String key = String.valueOf(urlNode.toRedisKey());
            if (jedis.exists(key) && !JobManagerConfig.enableInsertDuplicateJob) {
                LogManager.info("Spider is dulpilicate,so abandon");
                return false;  //重复任务 抛弃
            }
            jedis.set(key, "true");
        }
        String json = gson.toJson(urlNode);
        jedis.lpush(JOB_QUEUE, json);  //会存在一点并发的问题 但认为可以接受
        LogManager.info("Success Put Spider: " + spider.name());
        LogManager.info("Current redis job num is " + getJobNum());
        return true;
    }

    //use in @DistributeJobManager.onResume: putting back job to redisjobqueue,
    //so we will ignore if jedis.exists(key)
    public boolean putJob(HttpUrlNode urlNode) {
        if (urlNode == null) {
            return false;
        }

        jedis.lpush(JOB_QUEUE, gson.toJson(urlNode));
        return true;
    }

    private String lastUnsolvedStr = null;

    public IJob getJob() {
        if (!JobManagerConfig.enableGetSpiderFromQueue) {
            return null;
        }

        LogManager.info("try Get Spider ");
        String spiderStr = jedis.rpop(JOB_QUEUE);
        if (spiderStr == null) {
            return null;
        }

        HttpUrlNode node = gson.fromJson(spiderStr, HttpUrlNode.class);
        long hash = node.toPatternKey();

        if (unResolveList.contains(hash)) {  //避免多次调用getHandleableClassOf
            LogManager.info("Get Spider, Can't resolve node: " + node.toString() + " ,get another one");
            jedis.lpush(JOB_QUEUE, spiderStr);

            if (lastUnsolvedStr == null) {
                lastUnsolvedStr = spiderStr;
                return getJob();
            } else if (lastUnsolvedStr.equals(spiderStr)) { //走了一个循环了
                LogManager.info("Seems run into a loop,sleep...");
                return null;
            } else {
                return getJob();
            }
        }

        if (!urlPatternMap.containsKey(hash)) {
            Class clazz = getHandleableClassOf(node);
            if (clazz == null) {
                unResolveList.add(hash);

                LogManager.info("Get Spider, Can't resolve node: " + node.toString() + " ,get another one");
                jedis.lpush(JOB_QUEUE, spiderStr);

                if (lastUnsolvedStr == null) {
                    lastUnsolvedStr = spiderStr;
                    return getJob();
                } else if (lastUnsolvedStr.equals(spiderStr)) { //走了一个循环了
                    LogManager.info("Seems run into a loop,sleep...");
                    return null;
                } else {
                    return getJob();
                }

            } else {
                urlPatternMap.put(hash, clazz);
            }
        }

        Method fromUrl = SpiderMethodManager.getFromUrlMethod(urlPatternMap.get(hash));
        if (fromUrl == null) {   //有可能为null
            unResolveList.add(hash);
            LogManager.info("Get Spider, Can't resolve node: " + node.toString() + " ,get another one");
            jedis.lpush(JOB_QUEUE, spiderStr);

            if (lastUnsolvedStr == null) {
                lastUnsolvedStr = spiderStr;
                return getJob();
            } else if (lastUnsolvedStr.equals(spiderStr)) { //走了一个循环了
                LogManager.info("Seems run into a loop,sleep...");
                return null;
            } else {
                return getJob();
            }
        }

        try {
            BaseSpider spider = (BaseSpider) fromUrl.invoke(null, node);
            LogManager.info("Get Spider: " + spider.name());
            LogManager.info("Current redis job num is " + getJobNum());
            IJob job = JobProvider.getJob();
            job.setRealRunnable(spider);

            return job;
        } catch (IllegalAccessException e) {

        } catch (InvocationTargetException e) {

        }

        return null;
    }


    private Class getHandleableClassOf(HttpUrlNode node) {


        for (Class clazz : SpiderMethodManager.getSpiderClasses()) {
            Method fromUrl = SpiderMethodManager.getFromUrlMethod(clazz);
            if (fromUrl == null) {
                continue;
            }
            try {
                BaseSpider spider = (BaseSpider) fromUrl.invoke(null, node);
                if (spider != null) {
                    return clazz;
                } else {
                    continue;
                }
            } catch (IllegalAccessException e) {


            } catch (InvocationTargetException e) {


            }
        }

        return null;
    }

    public boolean isEmpty() {
        return getJobNum() == 0;
    }

    public int getJobNum() {
        return Ints.checkedCast(jedis.llen(JOB_QUEUE));
    }

}
