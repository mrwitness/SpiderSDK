package wuxian.me.spidersdk.util;

import okhttp3.OkHttpClient;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidersdk.JobManagerConfig;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Created by wuxian on 1/4/2017.
 * <p>
 * usage: OkHttpClient client = OkhttpProvider.getClent();
 */
public final class OkhttpProvider {

    private static OkHttpClient client;

    private OkhttpProvider() {
    }

    public static OkHttpClient getClient() {

        if (client == null) {
            client = new OkHttpClient.Builder().readTimeout(
                    JobManagerConfig.okhttpClientSocketReadTimeout,
                    TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return client;
    }
}
