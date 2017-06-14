package wuxian.me.spidersdk;

import com.sun.istack.internal.NotNull;
import okhttp3.Request;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.HttpUrlNode;
import wuxian.me.spidersdk.distribute.MethodCheckException;
import wuxian.me.spidersdk.distribute.SpiderMethodManager;
import wuxian.me.spidersdk.manager.JobManagerFactory;
import wuxian.me.spidersdk.util.OkhttpProvider;
import wuxian.me.spidersdk.util.SerializeFullLogHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 * Created by wuxian on 13/4/2017.
 * <p>
 * 一个spider其实就是两部分功能：
 * 1 生成httpRequest,或者说url,或者HttpUrlNode,这仨在逻辑上是等效的
 * 2 解析callback
 * 现在这俩被耦合到了一个叫做@BaseSpider的类上,未来有需求的时候可以解耦
 */
public abstract class BaseSpider implements Runnable {

    //如果启用了redis 子类必须实现这个函数 这个会在runtime被检查
    public static BaseSpider fromUrlNode(HttpUrlNode node) {
        return null;
    }

    //同上
    public static HttpUrlNode toUrlNode(BaseSpider spider) {
        return null;
    }

    //Used in distributted mode
    public final HttpUrlNode toUrlNode() {
        if (!SpiderMethodManager.contains(getClass())) {
            if (!JobManagerConfig.noMethodCheckingException) {
                throw new MethodCheckException();
            } else {
                return null;
            }
        }

        try {
            Method method = SpiderMethodManager.getToUrlMethod(getClass());
            return (HttpUrlNode) method.invoke(null, this);
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }

        return null;
    }

    public static final int RET_SUCCESS = 0; //成功

    //parsing error --> 指new Parser(html) 是不是因为parser能力太弱 这个网页有些tag不支持
    //这种情况认为任务失败了(FullLog处理),但不进行重试
    public static final int RET_PARSING_ERR = 1;

    public static final int RET_MAYBE_BLOCK = 2; //可能被block

    private SpiderCallback callback;
    private Request request;

    public BaseSpider() {
        callback = getCallback();
    }

    @NotNull
    protected abstract SpiderCallback getCallback();

    public void run() {
        LogManager.info("BaseSpider.run");

        Request request = getRequest();
        if (request == null || !JobManagerConfig.enableDispatchSpider) {
            return;
        }

        //LogManager.info("Success dispatch");
        JobManagerFactory.getJobManager().onDispatch(this);
        OkhttpProvider.getClient().newCall(request).enqueue(callback);
    }


    private final Request getRequest() {
        if (request == null) {
            request = buildRequest();
        }
        return request;
    }

    @NotNull
    protected abstract Request buildRequest();

    /**
     * 即使HttpCode返回200 页面也有可能是伪造的或者数据是造假的或者是一个You are blocked(Fuck you spider)页面
     *
     * @return state
     * 若返回 @RET_SUCCESS,认为本次抓取成功
     * 若返回 @RET_ERROR,认为本次抓取失败
     * 若返回 @RET_PARSING_ERR,可能是被屏蔽了 由@checkBlockAndFailThisSpider(String html)决定是否被屏蔽
     */
    public abstract int parseRealData(String data);


    //根据HttpCode判定是否被block
    protected boolean checkBlockAndFailThisSpider(int httpCode) {
        if (httpCode == -1 || httpCode == 404) {
            return true;
        }
        return false;
    }

    //根据网页内容判定是否被block
    protected abstract boolean checkBlockAndFailThisSpider(String html);

    public final String simpleName() {
        return getClass().getSimpleName();
    }

    //For Log
    public abstract String name();

    //For toPatternKey code
    public abstract String hashString();

    //将详细错误码包括Http Request,Response写到本地文件
    public final void serializeFullLog() {
        SerializeFullLogHelper.seriazeFullLog(callback, buildRequest(), toString(), name());
    }

    @Override
    public final String toString() {
        return hashString();
    }

    @Override
    public final int hashCode() {
        return hashString().hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof BaseSpider) {
            boolean ret = hashString().equals(((BaseSpider) obj).hashString());
            return ret;
        }
        return super.equals(obj);
    }

}
