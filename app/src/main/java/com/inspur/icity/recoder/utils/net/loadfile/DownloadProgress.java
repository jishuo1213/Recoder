package com.inspur.icity.recoder.utils.net.loadfile;

/**
 * Created by fanjsh on 2017/8/16.
 */


public class DownloadProgress<T> {

    public float progress;
    public T data;

    public DownloadProgress(float progress, T t) {
        this.progress = progress;
        this.data = t;
    }
}
