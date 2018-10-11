package com.inspur.icity.recoder.utils.net.exception;

/**
 * Created by Fan on 2017/9/7.
 */

public class RetryFailedException extends Exception {
    public RetryFailedException(Throwable cause) {
        super(cause);
    }
}
