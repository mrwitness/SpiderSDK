package wuxian.me.spidersdk.anti;

/**
 * Created by wuxian on 7/4/2017.
 * <p>
 */
public class Fail {

    public static final int FAIL_NETWORK_ERROR = -1;

    public static final int FAIL_MAYBE_BLOCK = 102;

    public static final int FAIL_BLOCK = 101;

    public static final int FAIL_404 = 404;

    private int httpCode;
    private String message;

    long millis;

    public Fail(int code, String msg) {
        this.httpCode = code;
        this.message = msg;

        millis = System.currentTimeMillis();
    }

    public Fail(int code) {
        this(code, "");
    }

    public int getHttpCode() {
        return httpCode;
    }

    public boolean is404() {
        return httpCode == FAIL_404;
    }

    public boolean isMaybeBlock() {
        return httpCode == FAIL_MAYBE_BLOCK;
    }

    public boolean isNetworkErr() {
        return httpCode == FAIL_NETWORK_ERROR;
    }

    public boolean isBlock() {
        return httpCode == FAIL_BLOCK;
    }

    @Override
    public String toString() {
        return "Httpcode: " + httpCode + " ,Message: " + message;
    }

    public final static Fail MAYBE_BLOCK = new Fail(FAIL_MAYBE_BLOCK, "Maybe Blocked");

    public final static Fail BLOCK = new Fail(FAIL_BLOCK, "Blocked");

    public final static Fail NETWORK_ERR = new Fail(FAIL_NETWORK_ERROR, "Network Error");
}
