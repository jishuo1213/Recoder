package com.inspur.icity.recoder.utils.net.loadfile;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

/**
 * Created by fanjsh on 2017/8/16.
 */


public class ProgressResponseBody extends ResponseBody {

    private final ResponseBody responseBody;
    private final ProgressListener progressListener;
    private BufferedSource bufferedSource;

    public ProgressResponseBody(ResponseBody responseBody, ProgressListener progressListener) {
        this.responseBody = responseBody;
        this.progressListener = progressListener;
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return responseBody.contentType();
    }

    @Override
    public long contentLength() {
        return responseBody.contentLength();
    }

    @Override
    public BufferedSource source() {
        if (bufferedSource == null) {
            bufferedSource = Okio.buffer(source(responseBody.source()));
        }
        return bufferedSource;
    }

    private static final String TAG = "ProgressResponseBody";


    private Source source(Source source) {
        return new ForwardingSource(source) {
            long totalBytesRead = 0L;
            long notifyTime;

            @Override
            public long read(@NonNull Buffer sink, long byteCount) throws IOException {
                long bytesRead = super.read(sink, byteCount);
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += bytesRead != -1 ? bytesRead : 0;

                long currentTime = System.currentTimeMillis();
                if (currentTime - notifyTime >= 1000) {
                    Log.i(TAG, "read: " + currentTime + "===========" + notifyTime + "bytesRead" + bytesRead);
                    progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1);
                    notifyTime = currentTime;
                } else if (bytesRead == -1) {
                    progressListener.update(totalBytesRead, responseBody.contentLength(), true);
                }
                return bytesRead;
            }
        };
    }

    public interface ProgressListener {
        void update(long bytesRead, long contentLength, boolean done);
    }
}
