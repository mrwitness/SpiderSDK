package wuxian.me.spidersdk.proxy;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import wuxian.me.spidercommon.model.Proxy;

/**
 * Created by wuxian on 23/7/2017.
 */
public interface IProxyMaker {

    @Nullable
    Proxy make();

    @NotNull
    Proxy makeUntilSuccess();

    @Nullable
    Proxy make(int tryTime);

}
