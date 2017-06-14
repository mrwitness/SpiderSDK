package wuxian.me.spidersdk.anti;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by wuxian on 24/4/2017.
 */
public class UserAgentManager {

    private static List<String> browserAgentList = new ArrayList<String>();

    private static List<String> spiderAgentList = new ArrayList<String>();

    private static List<String> mobileAgentList = new ArrayList<String>();


    static {
        browserAgentList.add("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");
        browserAgentList.add("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.6; rv:2.0.1) Gecko/20100101 Firefox/4.0.1");
        browserAgentList.add("Opera/9.80 (Windows NT 6.1; U; en) Presto/2.8.131 Version/11.11");
        browserAgentList.add("Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_6_8; en-us) AppleWebKit/534.50 (KHTML, like Gecko) Version/5.1 Safari/534.50");

        //测试移动agent不会被封
        mobileAgentList.add("Mozilla/5.0 (iPhone; U; CPU iPhone OS 4_3_3 like Mac OS X; en-us) AppleWebKit/533.17.9 (KHTML, like Gecko) Version/5.0.2 Mobile/8J2 Safari/6533.18.5");
        mobileAgentList.add("Mozilla/5.0 (Linux; U; Android 2.3.7; en-us; Nexus One Build/FRF91) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1");
        mobileAgentList.add("Mozilla/5.0 (SymbianOS/9.4; Series60/5.0 NokiaN97-1/20.0.019; Profile/MIDP-2.1 Configuration/CLDC-1.1) AppleWebKit/525 (KHTML, like Gecko) BrowserNG/7.1.18124");

        //测试发现拉勾会把爬虫的agent封掉 因此不能用这些user-agent
        spiderAgentList.add("Googlebot/2.1 (+http://www.googlebot.com/bot.html)");
        spiderAgentList.add("Googlebot/2.1 (+http://www.google.com/bot.html)");
        spiderAgentList.add("Mozilla/5.0 (compatible; Yahoo! Slurp China; http://misc.yahoo.com.cn/help.html)");
        spiderAgentList.add("Mozilla/5.0 (compatible; Yahoo! Slurp; http://help.yahoo.com/help/us/ysearch/slurp)");
        spiderAgentList.add("Mozilla/5.0 (compatible; iaskspider/1.0; MSIE 6.0)");
        spiderAgentList.add("Sogou web spider/3.0(+http://www.sogou.com/docs/help/webmasters.htm#07)");
        spiderAgentList.add("Sogou Push Spider/3.0(+http://www.sogou.com/docs/help/webmasters.htm#07)");
        spiderAgentList.add("Mozilla/5.0 (compatible; YodaoBot/1.0;http://www.yodao.com/help/webmaster/spider/;)");
        spiderAgentList.add("msnbot/1.0 (+http://search.msn.com/msnbot.htm)");
    }

    private UserAgentManager() {
    }

    private static int spiderIndex = -1;

    public static String nextSpiderAgent() {
        if (spiderAgentList.size() == 0) {
            return null;
        }

        return spiderAgentList.get((++spiderIndex) % spiderAgentList.size());
    }

    private static int browserIndex = -1;

    public static String nextBrowserAgent() {
        if (browserAgentList.size() == 0) {
            return null;
        }

        return browserAgentList.get((++browserIndex) % browserAgentList.size());
    }

    private static int mobIndex = -1;

    public static String nextMobileAgent() {
        if (mobileAgentList.size() == 0) {
            return null;
        }

        return mobileAgentList.get((++mobIndex) % mobileAgentList.size());
    }

    static Random random = new Random();

    public static void switchIndex() {
        currentIndex = random.nextInt(browserAgentList.size() + mobileAgentList.size());
    }

    private static int currentIndex = 0;

    public static String getAgent() {
        if (currentIndex <= browserAgentList.size() - 1) {
            return browserAgentList.get(currentIndex);
        } else {
            return mobileAgentList.get(currentIndex - browserAgentList.size());
        }

    }
}
