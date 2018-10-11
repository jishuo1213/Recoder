package com.inspur.icity.recoder.utils.net.exception;

/**
 * Created by fanjsh on 2017/8/15.
 */


public class CallCancelException extends Exception {

    public CallCancelException() {
        super("Call is canceled by user");
    }
}
