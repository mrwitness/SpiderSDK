package wuxian.me.spidersdk;

import com.sun.istack.internal.NotNull;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidersdk.anti.Fail;
import wuxian.me.spidersdk.manager.JobManagerFactory;
import wuxian.me.spidersdk.manager.PlainJobManager;

import java.io.IOException;

/**
 * Created by wuxian on 10/4/2017.
 * <p>
 */
public abstract class SpiderCallback implements Callback {
    private BaseSpider spider;

    private Response response;

    private String body;

    public String getBody() {
        return body;
    }

    public Response getResponse() {
        return response;
    }

    protected final BaseSpider getSpider() {
        return spider;
    }

    public SpiderCallback(@NotNull BaseSpider spider) {
        this.spider = spider;
        //PlainJobManager.getInstance().onDispatch(spider);
    }


    public final void onResponse(Call call, Response response) throws IOException {
        this.response = response;
        if (response.body() != null) {
            try {
                this.body = response.body().string();
            } catch (java.net.SocketException e) {
                //Fixme: 偶现的bug Todo: 重试
                return;
            }

        }

        if (!response.isSuccessful()) {
            LogManager.error("HttpCode: " + response.code() + " spider: " + spider.name()); //console尽量少log

            if (spider.checkBlockAndFailThisSpider(response.code())) {
                LogManager.error("We got BLOCKED, " + spider.name());
                JobManagerFactory.getJobManager().fail(spider, Fail.BLOCK);
            } else {
                JobManagerFactory.getJobManager().fail(spider, new Fail(response.code(), response.message()));
            }
            if (response.body() != null) {
                response.body().close();
            }
            spider.serializeFullLog();

        } else {
            int result = spider.parseRealData(body);
            if (result == BaseSpider.RET_SUCCESS) {
                JobManagerFactory.getJobManager().success(spider);

            } else if (result == BaseSpider.RET_PARSING_ERR) {

                JobManagerFactory.getJobManager().fail(spider, Fail.MAYBE_BLOCK, false);  //这个不放入重试队列
                spider.serializeFullLog();

            } else if (result == BaseSpider.RET_MAYBE_BLOCK) {

                if (spider.checkBlockAndFailThisSpider(body)) {
                    LogManager.error("We got BLOCKED, " + spider.name());
                    JobManagerFactory.getJobManager().fail(spider, Fail.BLOCK);
                    spider.serializeFullLog();
                } else {
                    JobManagerFactory.getJobManager().success(spider);
                }
            }
        }
    }
}
