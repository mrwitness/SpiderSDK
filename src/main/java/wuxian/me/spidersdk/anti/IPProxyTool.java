package wuxian.me.spidersdk.anti;

import okhttp3.*;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidercommon.util.FileUtil;
import wuxian.me.spidersdk.util.OkhttpProvider;
import wuxian.me.spidersdk.JobManagerConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static wuxian.me.spidersdk.util.ShellUtil.*;

/**
 * Created by wuxian on 9/4/2017.
 * http://www.xdaili.cn/freeproxy.html   --> 还行
 * <p>
 * http://www.xicidaili.com/ http://www.kxdaili.com/ 貌似已阵亡 --> 拉勾会屏蔽这个网站的ip
 * http://www.ip181.com/  --> 稳定性太差了 可能是国内用它的人太多？
 */
public class IPProxyTool {

    CountDownLatch countDownLatch = new CountDownLatch(2);

    public static final String CUT = ";";
    public static final String SEPRATE = ":";

    private static List<Proxy> ipPortList;
    public Proxy currentProxy = null;

    static {
        ipPortList = new ArrayList<Proxy>();
    }

    Request request = null;

    private boolean inited = false;

    public IPProxyTool() {

    }

    public void init() {
        if (inited) {
            return;
        }
        inited = true;

        FileUtil.writeToFile(getOpenProxyShellPath(), "open -t " + getProxyFilePath());

        HttpUrl.Builder urlBuilder = HttpUrl.parse("http://www.ip138.com/ip2city.asp").newBuilder();
        Headers.Builder builder = new Headers.Builder();
        builder.add("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");
        //CacheControl cc = new CacheControl.Builder().noCache().build();
        request = new Request.Builder()
                .cacheControl(CacheControl.FORCE_NETWORK)
                .headers(builder.build())
                .url(urlBuilder.build().toString())
                .build();

        if (!FileUtil.checkFileExist(getProxyFilePath())) {
            FileUtil.writeToFile(getProxyFilePath(), "");
        }

        if (JobManagerConfig.enableInitProxyFromFile) {
            LogManager.info("Using Proxy,Try read from File");
            ipPortList.clear();
            readProxyFromFile();

            currentProxy = forceSwitchProxyTillSuccess();
            FileUtil.writeToFile(getProxyFilePath(), "");  //清空文件
        }
    }

    private FutureTask<String> getFuture() {
        return new FutureTask<String>(getCallable());
    }

    private Callable<String> getCallable() {
        return new Callable<String>() {
            public String call() throws Exception {
                try {
                    Response response = OkhttpProvider.getClient().newCall(request).execute();

                    return response.body().string();
                } catch (IOException e) {
                    return null;
                }
            }
        };
    }

    String getProxyFilePath() {
        return JobManagerConfig.ipproxyFile;
    }

    public static boolean isVaildIpPort(String[] ipport) {
        if (ipport == null || ipport.length != 2) {
            return false;
        }

        String reg1 = "[0-9]+[.][0-9]+[.][0-9]+[.][0-9]+";
        Pattern pattern = Pattern.compile(reg1);
        Matcher matcher = pattern.matcher(ipport[0]);
        if (!matcher.matches()) {
            return false;
        }

        String reg2 = "[0-9]+";
        pattern = Pattern.compile(reg2);
        matcher = pattern.matcher(ipport[1]);
        return matcher.matches();
    }

    private void readProxyFromFile() {
        if (!FileUtil.checkFileExist(getProxyFilePath())) {
            return;
        }
        String content = FileUtil.readFromFile(getProxyFilePath());
        LogManager.info("Content From ProxyFile "+content);
        if (content != null) {
            String[] proxys = content.split(CUT);
            if (proxys == null || proxys.length == 0) {
                String[] proxy = content.split(SEPRATE);
                if (proxy != null && proxy.length == 2) {
                    if (isVaildIpPort(proxy)) {
                        ipPortList.add(new Proxy(proxy[0], Integer.parseInt(proxy[1])));
                    }
                }
                return;
            }
            for (int i = 0; i < proxys.length; i++) {
                String[] proxy = proxys[i].split(SEPRATE);

                if (proxy != null && proxy.length == 2) {
                    LogManager.info("ip: " + proxy[0] + " port: " + proxy[1]);
                    if (isVaildIpPort(proxy)) {
                        LogManager.info("proxy is valid");
                        ipPortList.add(new Proxy(proxy[0], Integer.parseInt(proxy[1])));
                    }
                }
            }
        }
    }

    public boolean ipSwitched(final Proxy proxy) {
        try {
            boolean ret = ensureIpSwitched(proxy);
            return ret;
        } catch (InterruptedException e1) {
            return false;
        } catch (ExecutionException e) {
            return false;
        }
    }

    public Proxy forceSwitchProxyTillSuccess() {
        while (true) {  //每个ip尝试三次 直到成功或没有proxy
            Proxy proxy = switchNextProxy();
            if (proxy == null) {
                LogManager.info("ProxyList is Empty,Open Text");
                openShellAndEnsureProxyInputed();
                proxy = switchNextProxy();
            }
            LogManager.info("We Try To Switch To Ip: " + proxy.ip + " Port: " + proxy.port);
            int ensure = 0;
            boolean success = false;
            while (!(success = ipSwitched(proxy)) && ensure < JobManagerConfig.everyProxyTryTime) {  //每个IP尝试三次
                ensure++;
                LogManager.info("Switch Proxy Fail Times: " + ensure);
            }
            if (success) {
                LogManager.info("Success Switch Proxy");
                return proxy;
            }
        }

    }

    public Proxy switchNextProxy() {
        if (ipPortList.size() == 0) {
            return null;
        }
        Proxy proxy = ipPortList.get(0);  //get and remove
        currentProxy = proxy;
        ipPortList.remove(0);

        System.setProperty("http.proxySet", "true");
        System.getProperties().setProperty("http.proxyHost", proxy.ip);
        System.getProperties().setProperty("http.proxyPort", String.valueOf(proxy.port));

        System.getProperties().setProperty("https.proxyHost", proxy.ip);
        System.getProperties().setProperty("https.proxyPort", String.valueOf(proxy.port));

        return proxy;
    }

    public boolean ensureIpSwitched(final Proxy proxy)
            throws InterruptedException, ExecutionException {
        FutureTask<String> future = getFuture();
        new Thread(future).start();
        if (future.get() == null) {
            return false;
        }

        boolean b = future.get().contains(proxy.ip);
        return b;
    }

    //支持运行时手工输入最新的proxy
    public void openShellAndEnsureProxyInputed() {
        if (countDownLatch.getCount() != 2) {
            return;
        }
        //logger().info("Begin openShellAndEnsureProxyInputed");
        countDownLatch.countDown();

        new Thread() {
            @Override
            public void run() {
                if (textEditState() == 1) {
                    LogManager.info("Begin OpenTextEdit");
                    openTextEdit();
                } else {
                    LogManager.info("TextEdit is open...");
                }

                boolean b = true;
                do {
                    ipPortList.clear();
                    try {
                        sleep(JobManagerConfig.shellCheckProxyFileSleepTime);    //每过10s检测文件是否有新的proxy ip写入,若没有,一直重试直到成功
                    } catch (InterruptedException e) {
                        ;
                    }
                    readProxyFromFile();

                    if (ipPortList.size() == 0) {
                        b = false;
                    } else {
                        if (currentProxy != null && ipPortList.get(0).equals(currentProxy)) {
                            b = false;
                        } else {
                            b = true;
                        }
                    }

                    if (!b) {
                        LogManager.info("Still No Proxy wrote,try open again");
                        if (textEditState() == 1) { //重新打开文件
                            openTextEdit();
                        }
                    } else {
                        LogManager.info("Valid Proxy Readed,Try to Switch to it... clear proxy file...");
                    }
                } while (!b);

                FileUtil.writeToFile(getProxyFilePath(), "");  //清空文件
                countDownLatch.countDown();
            }
        }.start();

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            ;
        }

        //logger().info("Return from openShellAndEnsureProxyInputed");
        countDownLatch = new CountDownLatch(2);
    }

}
