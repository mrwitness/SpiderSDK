package wuxian.me.spidersdk.distribute;

/**
 * Created by wuxian on 13/5/2017.
 */
public class MethodCheckException extends RuntimeException {

    public MethodCheckException(){
        super();
    }

    public MethodCheckException(String err){
        super(err);
    }
}
