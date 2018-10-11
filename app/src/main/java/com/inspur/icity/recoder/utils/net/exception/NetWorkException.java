package com.inspur.icity.recoder.utils.net.exception;

import java.io.IOException;

/**
 * Created by Fan on 2017/9/2.
 * 网络异常
 */

public class NetWorkException extends IOException {
    public NetWorkException() {
        super("当前无网络或网络异常");
    }

    public NetWorkException(Throwable cause) {
        super("当前无网络或网络异常", cause);
    }
}
