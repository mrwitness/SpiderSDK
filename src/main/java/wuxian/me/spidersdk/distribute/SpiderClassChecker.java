package wuxian.me.spidersdk.distribute;

import wuxian.me.spidercommon.model.HttpUrlNode;
import wuxian.me.spidersdk.BaseSpider;
import wuxian.me.spidersdk.JobManagerConfig;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * Created by wuxian on 12/5/2017.
 *
 * Runtime检查@BaseSpider的子类是否实现了@BaseSpider.fromUrlNode,toUrlNode,若没有,抛异常
 */
public class SpiderClassChecker {

    private SpiderClassChecker() {
    }

    public static void performCheckAndCollect(String pack) {
        try {
            Set<Class<?>> classSet = ClassHelper.getClasses(pack);

            for (Class clazz : classSet) {
                performCheckAndCollect(clazz);
            }
        } catch (IOException e) {
            ;
        }
    }

    public static SpiderMethodTuple performCheckAndCollect(Class clazz) {
        SpiderMethodTuple ret = null;
        try {
            if (Modifier.isAbstract(clazz.getModifiers())) { //跳过抽象类检查
                return ret;
            }
            clazz.asSubclass(BaseSpider.class);
            Method method1 = clazz.getMethod("toUrlNode", clazz);
            if (!(method1.getDeclaringClass().getSimpleName().equals(clazz.getSimpleName()))) {
                //if (!JobManagerConfig.noMethodCheckingException) {
                //    throw new MethodCheckException();
                //}

                method1 = null;
            }
            Method method = clazz.getMethod("fromUrlNode", HttpUrlNode.class);
            if (!(method.getDeclaringClass().getSimpleName().equals(clazz.getSimpleName()))) {
                //if (!JobManagerConfig.noMethodCheckingException) {
                //    throw new MethodCheckException();
                //}
                method = null;
            }

            //支持其中一个方法为null
            if(method == null && method1 == null) {
                return ret;
            }

            ret = new SpiderMethodTuple();
            ret.fromUrlNode = method;
            ret.toUrlNode = method1;
        } catch (ClassCastException e) {

        } catch (NoSuchMethodException e) {
            if (!JobManagerConfig.noMethodCheckingException) {
                throw new MethodCheckException();
            }
        }

        return ret;
    }
}
