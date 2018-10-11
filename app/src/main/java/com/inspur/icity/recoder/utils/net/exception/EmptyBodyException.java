package com.inspur.icity.recoder.utils.net.exception;

import java.io.IOException;

/**
 * Created by fanjsh on 2017/8/10.
 */


public class EmptyBodyException extends IOException {
    private static final String TAG = "EmptyBodyException";

//    public EmptyBodyException() {
//        super();
//    }

    public EmptyBodyException() {
        super("response body is null");
    }
}
