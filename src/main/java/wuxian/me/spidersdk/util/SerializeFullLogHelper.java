package wuxian.me.spidersdk.util;

import okhttp3.Request;
import okhttp3.Response;
import wuxian.me.spidercommon.util.FileUtil;
import wuxian.me.spidersdk.JobManagerConfig;
import wuxian.me.spidersdk.SpiderCallback;

import java.text.SimpleDateFormat;
import java.util.Date;

import static wuxian.me.spidercommon.util.FileUtil.getCurrentPath;


/**
 * Created by wuxian on 15/5/2017.
 */
public class SerializeFullLogHelper {
    private SerializeFullLogHelper() {
    }

    private static final String LINTFEED = "/r/n";
    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static SimpleDateFormat fullLogSdf = new SimpleDateFormat("yyyy_MM_dd_HH:mm:ss");


    public static void seriazeFullLog(SpiderCallback callback, Request request, String spiderString, String nameStr) {
        StringBuilder builder = new StringBuilder("");
        Date date = new Date();
        String time = sdf.format(date);
        builder.append(time);

        builder.append(" [" + Thread.currentThread().getName() + "]" + LINTFEED);
        builder.append("Spider: " + spiderString + LINTFEED);

        builder.append("Request: " + request + LINTFEED);
        Response response = callback.getResponse();
        if (response != null) {
            builder.append("Response: HttpCode: " + response.code() + " isRedirect: " + response.isRedirect() + " Message: " + response.message() + LINTFEED);
            builder.append("Header: " + response.headers().toString() + LINTFEED);
            builder.append(LINTFEED + "Body: " + callback.getBody());
        }

        String name = nameStr.length() > 25 ? nameStr.substring(0, 25) : nameStr;
        String fileName = fullLogSdf.format(date) + name; //simpleName只有一个类名

        FileUtil.writeToFile(getFullLogFilePath(fileName), builder.toString());
    }

    private static String getFullLogFilePath(String filename) {
        return getCurrentPath() + JobManagerConfig.fulllogFile + filename + JobManagerConfig.fulllogPost;
    }
}
