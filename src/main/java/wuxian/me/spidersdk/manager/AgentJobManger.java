package wuxian.me.spidersdk.manager;

import sun.security.provider.ConfigFile;
import wuxian.me.spidercommon.model.HttpUrlNode;
import wuxian.me.spidermaster.agent.SpiderAgent;
import wuxian.me.spidersdk.distribute.SpiderMethodManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wuxian on 18/5/2017.
 *
 * 分布式模式下的agent jobManager
 */
public class AgentJobManger extends DistributeJobManager {

    private SpiderAgent agent;

    @Override
    protected void init() {
        super.init();

        SpiderAgent.init();

        agent = new SpiderAgent();
        agent.start();

        List<Class<?>> clazzList = new ArrayList<Class<?>>();
        for (Class<?> clz : SpiderMethodManager.getSpiderClasses()) {
            clazzList.add(clz);
        }

        List<HttpUrlNode> urlList = new ArrayList<HttpUrlNode>(clazzList.size());
        for (int i = 0; i < clazzList.size(); i++) {
            HttpUrlNode node = new HttpUrlNode();
            node.baseUrl = "hello_world";//Fixme:这个值需要反射拿到 现在为了先把rpc调通 因此先给个假值
        }
        agent.registerToMaster(clazzList, urlList);
    }
}
