package wuxian.me.spidersdk.proxy;

import com.sun.istack.internal.NotNull;
import wuxian.me.spidercommon.log.LogManager;
import wuxian.me.spidercommon.model.Proxy;
import wuxian.me.spidercommon.util.FileUtil;
import wuxian.me.spidercommon.util.IpPortUtil;
import wuxian.me.spidercommon.util.ShellUtil;
import wuxian.me.spidersdk.JobManagerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static wuxian.me.spidercommon.util.ShellUtil.openTextEdit;
import static wuxian.me.spidercommon.util.ShellUtil.textEditState;

/**
 * Created by wuxian on 23/7/2017.
 * <p>
 * 通过手动输入proxy的方式来获取一个proxy
 */
public class HandInputProxyMaker implements IProxyMaker {

    public HandInputProxyMaker() {

        init();
    }

    private void init() {
        String path = FileUtil.getCurrentPath() + "/util/shell/openproxy";
        FileUtil.writeToFile(path, "open -t " + getProxyFilePath());
        ShellUtil.chmod(path, 0777);

        if (!FileUtil.checkFileExist(getProxyFilePath())) {
            FileUtil.writeToFile(getProxyFilePath(), "");
        }

        if (JobManagerConfig.enableInitProxyFromFile) {
            LogManager.info("Using Proxy,Try read from File");
            proxyList = readProxyFromFile();

            FileUtil.writeToFile(getProxyFilePath(), "");
        }
    }

    public static final String CUT = ";";
    public static final String SEPRATE = ":";

    @NotNull
    private List<Proxy> readProxyFromFile() {

        List<Proxy> list = new ArrayList<Proxy>();

        if (!FileUtil.checkFileExist(getProxyFilePath())) {
            return list;
        }
        String content = FileUtil.readFromFile(getProxyFilePath());
        LogManager.info("Content From ProxyFile " + content);
        if (content != null) {
            String[] proxys = content.split(CUT);
            if (proxys == null || proxys.length == 0) {
                String[] proxy = content.split(SEPRATE);
                if (proxy != null && proxy.length == 2) {
                    if (IpPortUtil.isVaildIpAndPort(proxy)) {
                        list.add(new Proxy(proxy[0], Integer.parseInt(proxy[1])));
                    }
                }
                return list;
            }
            for (int i = 0; i < proxys.length; i++) {
                String[] proxy = proxys[i].split(SEPRATE);

                if (proxy != null && proxy.length == 2) {
                    LogManager.info("ip: " + proxy[0] + " port: " + proxy[1]);
                    if (IpPortUtil.isVaildIpAndPort(proxy)) {
                        LogManager.info("proxy is valid");
                        list.add(new Proxy(proxy[0], Integer.parseInt(proxy[1])));
                    }
                }
            }
        }
        return list;
    }

    String getProxyFilePath() {
        return JobManagerConfig.ipproxyFile;
    }

    CountDownLatch countDownLatch = new CountDownLatch(2);

    private List<Proxy> proxyList = null;

    public synchronized Proxy make() {
        return make(1);
    }

    public synchronized Proxy makeUntilSuccess() {

        if (proxyList != null && proxyList.size() != 0) {
            Proxy proxy = proxyList.get(0);
            proxyList.remove(0);
            return proxy;
        }

        countDownLatch = new CountDownLatch(1);

        new Thread() {
            @Override
            public void run() {
                if (textEditState() == 1) {
                    LogManager.info("Begin OpenTextEdit");
                    openTextEdit();
                } else {
                    LogManager.info("TextEdit is open");
                }

                boolean readProxySuccess = true;
                do {
                    try {
                        sleep(JobManagerConfig.shellCheckProxyFileSleepTime);
                    } catch (InterruptedException e) {
                        ;
                    }
                    proxyList = readProxyFromFile();

                    if (proxyList.size() == 0) {
                        readProxySuccess = false;
                    } else {//Todo:如果输入的是重复的已经失效的代理
                        readProxySuccess = true;
                    }

                    if (!readProxySuccess) {
                        LogManager.info("Still No Proxy wrote,try open again");
                        if (textEditState() == 1) { //重新打开文件
                            openTextEdit();
                        }
                    } else {
                        LogManager.info("Valid Proxy Readed,clear proxy file");
                    }
                } while (!readProxySuccess);

                FileUtil.writeToFile(getProxyFilePath(), "");  //清空文件
                countDownLatch.countDown();
            }
        }.start();

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            ;
        }

        countDownLatch = new CountDownLatch(2);
        return makeUntilSuccess();
    }

    public synchronized Proxy make(final int tryTime) {
        if (proxyList != null && proxyList.size() != 0) {
            Proxy proxy = proxyList.get(0);
            proxyList.remove(0);
            return proxy;
        }

        if (countDownLatch.getCount() != 2) {
            return null;
        }

        countDownLatch.countDown();
        new Thread() {
            @Override
            public void run() {
                proxyList = null;
                if (textEditState() == 1) {
                    LogManager.info("Begin OpenTextEdit");
                    openTextEdit();

                } else {
                    LogManager.info("TextEdit is open...");
                }
                boolean readProxySuccess = true;
                int time = tryTime <= 0 ? 1 : tryTime;
                do {
                    try {
                        sleep(JobManagerConfig.shellCheckProxyFileSleepTime);
                    } catch (InterruptedException e) {
                        ;
                    }
                    List<Proxy> list = readProxyFromFile();
                    if (list.size() == 0) {
                        readProxySuccess = false;
                    } else {
                        readProxySuccess = true;
                    }
                    if (!readProxySuccess) {
                        LogManager.info("Still No Proxy wrote,try open again");
                        if (textEditState() == 1) { //重新打开文件
                            openTextEdit();
                        }
                    } else {
                        LogManager.info("Valid Proxy Readed,Try to Switch to it... clear proxy file...");
                    }
                    time--;
                } while (!readProxySuccess && time > 0);

                FileUtil.writeToFile(getProxyFilePath(), "");  //清空文件
                countDownLatch.countDown();
            }
        }.start();

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            ;
        }

        countDownLatch = new CountDownLatch(2);
        if (proxyList != null && proxyList.size() != 0) {
            Proxy proxy = proxyList.get(0);
            proxyList.remove(0);
            return proxy;
        }
        return null;
    }
}
