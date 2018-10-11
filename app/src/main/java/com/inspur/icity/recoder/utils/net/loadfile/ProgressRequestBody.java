package com.inspur.icity.recoder.utils.net.loadfile;

import android.support.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.internal.Util;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

/**
 * Created by fanjsh on 2017/8/16.
 */


public class ProgressRequestBody extends RequestBody {
    private static final String TAG = "ProgressRequestBody";

    private static final int SEGMENT_SIZE = 4096; // okio.Segment.SIZE

    private final File file;

    private final String contentType;

    private final ProgressListener progressListener;

    private long contentLength;

    private String path;

    public ProgressRequestBody(File file, String contentType, ProgressListener listener) {
        this.file = file;
        this.contentType = contentType;
        this.progressListener = listener;
        path = file.getAbsolutePath();
    }

    @Override
    public long contentLength() {
        contentLength = file.length();
        return contentLength;
    }

    @Override
    public MediaType contentType() {
        return MediaType.parse(contentType);
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        Source source = null;
        try {
            source = Okio.source(file);
            long total = 0;
            long read;

            long notifyTime = System.currentTimeMillis();

            while ((read = source.read(sink.buffer(), SEGMENT_SIZE)) != -1) {
                total += read;
                sink.flush();
//                    writeBytes += read;
                long currentTime = System.currentTimeMillis();
                if (currentTime - notifyTime >= 500) {
                    progressListener.update(total, contentLength, total == contentLength, path);
                    notifyTime = currentTime;
                } else if (total == contentLength) {
                    progressListener.update(total, contentLength, true, path);
                }
            }
        } finally {
            Util.closeQuietly(source);
        }
    }

    public interface ProgressListener {
        void update(long bytesWrite, long contentLength, boolean done, String path);
    }
}
