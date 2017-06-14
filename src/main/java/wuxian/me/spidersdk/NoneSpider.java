package wuxian.me.spidersdk;

import okhttp3.Request;
import wuxian.me.spidercommon.model.HttpUrlNode;

/**
 * Created by wuxian on 16/5/2017.
 */
public class NoneSpider extends BaseSpider {
    public static BaseSpider fromUrlNode(HttpUrlNode node) {
        if (node.baseUrl.contains("hello_world")) {
            return new NoneSpider();
        }
        return null;
    }

    //同上
    public static HttpUrlNode toUrlNode(NoneSpider spider) {
        HttpUrlNode node = new HttpUrlNode();
        node.baseUrl = "hello_world";
        return node;
    }

    protected SpiderCallback getCallback() {
        return null;
    }

    protected Request buildRequest() {
        return null;
    }

    public int parseRealData(String data) {
        return 0;
    }

    protected boolean checkBlockAndFailThisSpider(String html) {
        return false;
    }

    public String name() {
        return hashString();
    }

    public String hashString() {
        return "NoneSpider";
    }
}
