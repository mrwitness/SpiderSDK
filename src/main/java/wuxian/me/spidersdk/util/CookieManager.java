package wuxian.me.spidersdk.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wuxian on 3/6/2017.
 */
public class CookieManager {

    private static Map<String, String> cookieList = new HashMap<String, String>();

    private CookieManager(){}

    public static void put(String key,String value) {
        if(key == null || key.length() == 0) {
            return;
        }

        if(!cookieList.containsKey(key)) {
            cookieList.put(key,value);
        }
    }

    public static boolean containsKey(String key) {
        if(key == null || key.length() == 0) {
            return false;
        }
        return cookieList.containsKey(key);
    }

    public static String get(String key) {
        if(key == null || key.length() == 0) {
            return null;
        }

        return cookieList.get(key);
    }

    public static void clear() {
        cookieList.clear();
    }
}
