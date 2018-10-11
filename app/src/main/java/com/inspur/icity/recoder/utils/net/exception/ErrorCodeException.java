package com.inspur.icity.recoder.utils.net.exception;

import java.io.IOException;

/**
 * Created by fanjsh on 2017/8/10.
 */


public class ErrorCodeException extends IOException {
    private static final String TAG = "ErrorCodeException";

    public ErrorCodeException(int code) {
        super("response failed response code is " + code);
    }
}
