package com.inspur.icity.recoder.utils.net.exception;

/**
 * Created by Fan on 2017/9/4.
 */

public class TokenException extends Exception {
    public TokenException() {
        super("Token已过期");
    }
}
