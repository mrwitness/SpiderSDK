package wuxian.me.spidersdk.anti;

import okhttp3.*;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidersdk.util.OkhttpProvider;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;

/**
 * Created by wuxian on 9/4/2017.
 */
public class IPProxyTool {

    private Proxy currentProxy = null;
    private Request request = null;
    private boolean inited = false;

    public IPProxyTool() {

    }

    public Proxy getCurrentProxy() {
        return currentProxy;
    }

    private void buildValidProxyRequest() {
        HttpUrl.Builder urlBuilder = HttpUrl.parse("http://city.ip138.com/ip2city.asp").newBuilder();
        Headers.Builder builder = new Headers.Builder();
        builder.add("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");

        builder.add("Host", "city.ip138.com");
        builder.add("Cookie", "ASPSESSIONIDQSRBDCTB=KEBMKPPEHJPLEPEBEPJIBNKN");
        builder.add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");

        //builder.add("Cache-Control","no-cache");
        //builder.add("Connection","keep-alive");
        builder.add("Connection", "close");
        builder.add("Pragma", "no-cache");
        builder.add("Accept-Encoding", "gzip, deflate");
        builder.add("Accept-Language", "zh-CN,zh;q=0.8,en;q=0.6");
        builder.add("Upgrade-Insecure-Requests", "1");

        request = new Request.Builder()
                .cacheControl(CacheControl.FORCE_NETWORK)
                .headers(builder.build())
                .url(urlBuilder.build().toString())
                .build();
    }

    public void init() {
        if (inited) {
            return;
        }
        inited = true;
        buildValidProxyRequest();
    }

    public FutureTask<String> getProxyValidatorFuture() {
        return new FutureTask<String>(getProxyValidatorCallable());
    }

    private Callable<String> getProxyValidatorCallable() {
        return new Callable<String>() {
            public String call() throws Exception {
                try {
                    Response response = OkhttpProvider.getClient().newCall(request).execute();
                    String ret = null;//response.body().string();

                    byte[] res = response.body().bytes();
                    String encodeing = BytesCharsetDetector.getDetectedCharset(res);

                    if(encodeing != null) {
                        try{
                            return new String(res,encodeing);
                        } catch (Exception e) {
                            return null;
                        }
                    }

                    return null;
                } catch (IOException e) {

                    if (e instanceof SocketTimeoutException) {
                        LogManager.error("callable SocketTimeoutException:" + e.getMessage());
                    } else {
                        LogManager.error("callable IOException" + e.getMessage());
                    }
                    return null;
                }
            }
        };
    }

    public boolean isIpSwitchedSuccess(final Proxy proxy) {
        try {
            boolean ret = validateIfIpSwitchedSuccess(proxy);
            return ret;
        } catch (InterruptedException e1) {

            LogManager.error("isIpSwitchedSuccess interruptedException");
            return false;
        } catch (ExecutionException e) {

            LogManager.error("isIpSwitchedSuccess executionException");
            return false;
        }
    }

    public void switchToProxy(Proxy proxy) {
        currentProxy = proxy;
        System.setProperty("http.proxySet", "true");
        System.getProperties().setProperty("http.proxyHost", proxy.ip);
        System.getProperties().setProperty("http.proxyPort", String.valueOf(proxy.port));

        System.getProperties().setProperty("https.proxyHost", proxy.ip);
        System.getProperties().setProperty("https.proxyPort", String.valueOf(proxy.port));
        return;
    }

    public boolean validateIfIpSwitchedSuccess(final Proxy proxy)
            throws InterruptedException, ExecutionException {
        FutureTask<String> future = getProxyValidatorFuture();
        new Thread(future).start();
        if (future.get() == null) {
            LogManager.error("future.get is null");
            return false;
        }

        LogManager.info("returned html:" + future.get());

        boolean b = future.get().contains(proxy.ip);
        return b;
    }

}
