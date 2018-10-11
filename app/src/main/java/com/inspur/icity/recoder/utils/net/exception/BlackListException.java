package com.inspur.icity.recoder.utils.net.exception;

/**
 * Created by Fan on 2017/9/4.
 */

public class BlackListException extends Exception {

    public int type;

    public BlackListException(int type) {
        super("当前用户被加入黑名单");
        this.type = type;
    }
}
