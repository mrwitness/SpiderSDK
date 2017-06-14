package wuxian.me.spidersdk.anti;

import com.sun.istack.internal.NotNull;
import wuxian.me.spidersdk.JobManagerConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by wuxian on 20/4/2017.
 * <p>
 * 用于判断是否被block
 * 注意实现线程安全
 *
 */
public class BlockHelper {

    private Map<Runnable, Fail> fail404Map = new ConcurrentHashMap<Runnable, Fail>();
    private AtomicLong last404 = new AtomicLong(0);
    private AtomicLong current404 = last404;

    private Map<Runnable, Fail> failNeterrMap = new ConcurrentHashMap<Runnable, Fail>();
    private AtomicLong lastNeterr = new AtomicLong(0);
    private AtomicLong currentNeterr = lastNeterr;

    private Map<Runnable, Fail> failMayblockMap = new ConcurrentHashMap<Runnable, Fail>();
    private AtomicLong lastMayblock = new AtomicLong(0);
    private AtomicLong currentMayblock = lastMayblock;

    private Map<Runnable, Fail> failBlockMap = new ConcurrentHashMap<Runnable, Fail>();
    private AtomicLong lastBlock = new AtomicLong(0);
    private AtomicLong currentBlock = lastBlock;

    public BlockHelper() {
        init();
    }

    public void reInit() {
        init();
    }

    public void init() {
        fail404Map.clear();
        failNeterrMap.clear();
        failMayblockMap.clear();
        failBlockMap.clear();
    }

    public void removeFail(@NotNull Runnable runnable) {
        fail404Map.remove(runnable);
        failNeterrMap.remove(runnable);
        failMayblockMap.remove(runnable);
        failBlockMap.remove(runnable);
    }

    public void addFail(@NotNull Runnable runnable, @NotNull Fail fail) {
        if (fail.is404()) {
            fail404Map.put(runnable, fail);

            last404.set(current404.get());
            current404.set(fail.millis);

        } else if (fail.isNetworkErr()) {
            failNeterrMap.put(runnable, fail);

            lastNeterr.set(currentNeterr.get());
            currentNeterr.set(fail.millis);

        } else if (fail.isMaybeBlock()) {
            failMayblockMap.put(runnable, fail);

            lastMayblock.set(currentMayblock.get());
            currentMayblock.set(fail.millis);
        } else if (fail.isBlock()) {
            failBlockMap.put(runnable, fail);

            lastBlock.set(currentBlock.get());
            currentBlock.set(fail.millis);
        }

    }

    public boolean isBlocked() {
        if (failBlockMap.size() >= JobManagerConfig.considerBlockedBlockNum) {
            return true;
        }
        if (fail404Map.size() >= JobManagerConfig.considerBlocked404Num) {
            return true;
        }

        if (failMayblockMap.size() >= JobManagerConfig.considerBlockedMayblockNum) {
            return true;
        }

        if (failNeterrMap.size() >= JobManagerConfig.considerBlockedNeterr) {
            return true;
        }
        return false;
    }
}
