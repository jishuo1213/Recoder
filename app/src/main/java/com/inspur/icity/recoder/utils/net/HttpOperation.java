package com.inspur.icity.recoder.utils.net;


import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.ArraySet;
import android.text.TextUtils;
import android.util.Log;

import com.inspur.icity.recoder.utils.FileUtils;
import com.inspur.icity.recoder.utils.net.exception.CallCancelException;
import com.inspur.icity.recoder.utils.net.exception.EmptyBodyException;
import com.inspur.icity.recoder.utils.net.exception.ErrorCodeException;
import com.inspur.icity.recoder.utils.net.loadfile.DownloadProgress;
import com.inspur.icity.recoder.utils.net.loadfile.ProgressRequestBody;
import com.inspur.icity.recoder.utils.net.loadfile.ProgressResponseBody;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Created by fanjsh on 2017/8/9.
 */
class HttpOperation {

    private static final String TAG = "HttpOperation";

    static final String KEY_return_type = "return_type";
    static final String KEY_return_value = "return_value";
    static final String KEY_request_id = "request_id";

    private static HttpOperation httpOperation;

    private static final long MB = 1024 * 1024;

    private static final int CONNECT_TIME_OUT = 10;
    private static final int IO_TIME_OUT = 15;


    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private OkHttpClient okHttpClient;

    private ArrayMap<String, Disposable> requestMap;

    @IntDef({Method.GET, Method.POST})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Method {
        int GET = 1;
        int POST = 2;
    }

    @IntDef({PostBodyType.FORM, PostBodyType.JSON})
    @Retention(RetentionPolicy.SOURCE)
    @interface PostBodyType {
        int FORM = 1;
        int JSON = 2;
    }

    @IntDef({ReturnType.STRING, ReturnType.FILE, ReturnType.CACHE})
    @Retention(RetentionPolicy.SOURCE)
    @interface ReturnType {
        int STRING = 1;
        int FILE = 2;
        int CACHE = 3;
    }

    @IntDef({LoadFileType.DOWNLOAD, LoadFileType.UPLOAD})
    @Retention(RetentionPolicy.SOURCE)
    @interface LoadFileType {
        int DOWNLOAD = 1;
        int UPLOAD = 2;
    }


    interface SuccessCallBack {
        /**
         * 在发起http请求时，如果返回的数据大于1MB，返回的数据会通过文件的
         * 方式存储，以避免内存溢出，此时返回类型type是文件类型，res是文件路径
         * ，反之，直接返回字符串类型的数据
         *
         * @param type            //返回类型 @ReturnType
         * @param res             //返回的数据或者一个文件地址
         * @param requestId//请求标示
         */
        void onSuccess(String requestId, int type, String res);
    }

    interface FailedCallBack {
        void onFailed(String requestId, Throwable e);
    }

    interface LoadFileSuccessCallBack {
        void onSuccess(String requestId, String res);
    }

    interface ProgressCallBack {
        void onProgress(String requestId, String filePath, float percent);
    }


    public static HttpOperation getInstance() {
        if (httpOperation == null)
            httpOperation = new HttpOperation();
        return httpOperation;
    }

    private HttpOperation() {
        getUnsafeOkHttpClient();
        requestMap = new ArrayMap<>();
    }


    private void getUnsafeOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder().cookieJar(new CookieJar() {
            private final ArrayMap<String, List<Cookie>> cookieStore = new ArrayMap<>();

            @Override
            public void saveFromResponse(@android.support.annotation.NonNull HttpUrl url, @android.support.annotation.NonNull List<Cookie> cookies) {
                cookieStore.put(url.host(), cookies);
            }

            @Override
            public List<Cookie> loadForRequest(@android.support.annotation.NonNull HttpUrl url) {
                List<Cookie> cookies = cookieStore.get(url.host());
                return cookies != null ? cookies : new ArrayList<>();
            }
        });

        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("TSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
            okHttpClient = builder.connectTimeout(CONNECT_TIME_OUT, TimeUnit.SECONDS)
                    .readTimeout(IO_TIME_OUT, TimeUnit.SECONDS)
                    .writeTimeout(IO_TIME_OUT, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            okHttpClient = builder.connectTimeout(CONNECT_TIME_OUT, TimeUnit.SECONDS)
                    .readTimeout(IO_TIME_OUT, TimeUnit.SECONDS)
                    .writeTimeout(IO_TIME_OUT, TimeUnit.SECONDS)
                    .build();
        }
    }

    private abstract static class HttpBuilder {

        String url;
        String requestId;
        Request.Builder okHttpBuilder;
        JSONObject headers;
        JSONObject params;
        ArrayList<String> keys;


        HttpBuilder() {
            okHttpBuilder = new Request.Builder();
        }

        public HttpBuilder url(String url) {
            this.url = url;
            okHttpBuilder.url(url);
            return this;
        }

        protected HttpBuilder requestId(String requestId) {
            this.requestId = requestId;
            okHttpBuilder.tag(requestId);
            return this;
        }

        protected HttpBuilder headers(JSONObject headers) {
            if (this.headers != null) {
                Iterator<String> it = headers.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    String value = headers.optString(key);
                    okHttpBuilder.header(key, value);
                    try {
                        this.headers.put(key, value);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                this.headers = headers;
            }
            return this;
        }

        protected HttpBuilder headers(ArrayMap<String, String> headers) {
            if (this.headers == null) {
                this.headers = new JSONObject();
            }
            if (headers != null) {
                for (String key : headers.keySet()) {
                    try {
                        this.headers.put(key, headers.get(key));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    okHttpBuilder.header(key, headers.get(key));
                }
            }
            return this;
        }

        /**
         * 携带的参数，在Get请求中这是附加在Url后面的参数，post请求中，是请求的
         * body，在下载和上传请求中，都是请求体
         * 可以是一个ArrayMap 或者JSON对象
         */
        public HttpBuilder params(ArrayMap<String, String> params) {
            JSONObject param = new JSONObject();
            ArrayList<String> keys = new ArrayList<>();
            for (String key : params.keySet()) {
                try {
                    param.put(key, params.get(key));
                    keys.add(key);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            signature(keys, param);
            return this;
        }

        public HttpBuilder params(JSONObject params) {
            ArrayList<String> keys = new ArrayList<>();
            Iterator iterator = params.keys();
            while (iterator.hasNext()) {
                keys.add((String) iterator.next());
            }
            signature(keys, params);
            return this;
        }

        private void signature(ArrayList<String> keys, JSONObject params) {
            Collections.sort(keys, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return o2.compareTo(o1);
                }
            });
            StringBuilder stringBuilder = new StringBuilder();
            for (String key : keys) {
                stringBuilder.append(params.optString(key));
            }
            this.params = params;
            this.keys = keys;
        }
    }


    /**
     * The type Request builder.
     */
    static class IRequestBuilder extends HttpBuilder {

        @Method
        private int method;
        @PostBodyType
        private int bodyType;

        boolean isCacheResponse;
        String cachePath;

        boolean isCacheHaveSend;

        private SuccessCallBack successCallBack;
        private FailedCallBack failedCallBack;

        IRequestBuilder() {
            super();
            bodyType = PostBodyType.FORM;
            method = Method.GET;
            isCacheResponse = false;
            isCacheHaveSend = false;
        }

        protected IRequestBuilder isCacheResponse(boolean isUseCache) {
            this.isCacheResponse = isUseCache;
            return this;
        }

        protected IRequestBuilder cachePath(String path) {
            this.cachePath = path;
            return this;
        }

        @Override
        public IRequestBuilder url(String url) {
            super.url(url);
            return this;
        }

        protected IRequestBuilder onSuccess(SuccessCallBack callBack) {
            this.successCallBack = callBack;
            return this;
        }

        protected IRequestBuilder onFailed(FailedCallBack callBack) {
            this.failedCallBack = callBack;
            return this;
        }

        @Override
        protected IRequestBuilder requestId(String requestId) {
            super.requestId(requestId);
            return this;
        }

        @Override
        protected IRequestBuilder headers(JSONObject headers) {
            super.headers(headers);
            return this;
        }

        @Override
        protected IRequestBuilder headers(ArrayMap<String, String> headers) {
            super.headers(headers);
            return this;
        }

        @Override
        public IRequestBuilder params(ArrayMap<String, String> params) {
            super.params(params);
            return this;
        }

        @Override
        public IRequestBuilder params(JSONObject params) {
            super.params(params);
            return this;
        }

        /**
         * 请求的方法 METHOD.GET 或者METHOD.POST
         *
         * @param method 请求的方法
         */
        IRequestBuilder method(@Method int method) {
            this.method = method;
            return this;
        }

        protected IRequestBuilder postBodyType(@PostBodyType int bodyType) {
            this.bodyType = bodyType;
            return this;
        }

        public IRequestBuilder get() {
            method(Method.GET);
            return this;
        }

        public IRequestBuilder post() {
            method(Method.POST);
            return this;
        }

        protected void exec() {
            checkRequestArgs(false);
            HttpOperation.getInstance().request(this);
        }


        protected void checkRequestArgs(boolean isRx) {
            url = checkNotNull(url, "url不能为空");
            if (!isRx)
                successCallBack = checkNotNull(successCallBack, "必须设置请求成功回调");
            if (method == Method.POST) {
                params = checkNotNull(params, "post请求参数不能为空");
            }
        }

        Observable<JSONObject> rxExec() {
            checkRequestArgs(true);
            return HttpOperation.getInstance().rxRequest(this);
        }


        @Override
        public String toString() {
            return "IRequestBuilder{" +
                    "url='" + url + '\'' +
                    ", method=" + method +
                    ", requestId='" + requestId + '\'' +
                    '}';
        }
    }


    static class LoadFileRequestBuilder extends HttpBuilder {
        @LoadFileType
        private int loadType;
        private Set<FileInfo> uploadFilePaths;
        private String destFilePath;
        private LoadFileSuccessCallBack successCallBack;
        private FailedCallBack failCallBack;
        private ProgressCallBack progressCallBack;


        LoadFileRequestBuilder() {
            super();
            loadType = LoadFileType.DOWNLOAD;
        }

        public LoadFileRequestBuilder download() {
            this.loadType = LoadFileType.DOWNLOAD;
            if (uploadFilePaths != null) {
                uploadFilePaths.clear();
            }
            return this;
        }

        public LoadFileRequestBuilder to(String destFilePath) {
            this.destFilePath = destFilePath;
            this.loadType = LoadFileType.DOWNLOAD;
            download();
            return this;
        }

        public LoadFileRequestBuilder upload() {
            this.loadType = LoadFileType.UPLOAD;
//            uploadFilePaths.add(filePath);
            this.destFilePath = null;
            return this;
        }


        public LoadFileRequestBuilder from(String localFilePath, String fileKey, String fileName) {
            upload();
            if (uploadFilePaths == null) {
                uploadFilePaths = new ArraySet<>();
            }
            uploadFilePaths.add(new FileInfo(localFilePath, fileKey, fileName));
            return this;
        }

        @Override
        public LoadFileRequestBuilder url(String url) {
            super.url(url);
            return this;
        }

        protected LoadFileRequestBuilder onSuccess(LoadFileSuccessCallBack callBack) {
            this.successCallBack = callBack;
            return this;
        }

        protected LoadFileRequestBuilder onFailed(FailedCallBack callBack) {
            this.failCallBack = callBack;
            return this;
        }

        protected LoadFileRequestBuilder onProgress(ProgressCallBack callBack) {
            this.progressCallBack = callBack;
            return this;
        }

        @Override
        public LoadFileRequestBuilder requestId(String requestId) {
            super.requestId(requestId);
            return this;
        }

        @Override
        public LoadFileRequestBuilder headers(JSONObject headers) {
            super.headers(headers);
            return this;
        }

        @Override
        public LoadFileRequestBuilder headers(ArrayMap<String, String> headers) {
            super.headers(headers);
            return this;
        }

        @Override
        public LoadFileRequestBuilder params(ArrayMap<String, String> params) {
            super.params(params);
            return this;
        }

        @Override
        public LoadFileRequestBuilder params(JSONObject params) {
            super.params(params);
            return this;
        }

        protected void exec() {
            url = checkNotNull(url);
            successCallBack = checkNotNull(successCallBack, "必须设置请求成功回调");
            if (loadType == LoadFileType.DOWNLOAD) {
                destFilePath = checkNotNull(destFilePath);
            } else {
                checkArgument(uploadFilePaths != null && !uploadFilePaths.isEmpty(), "No File to upload");
            }
            HttpOperation.getInstance().request(this);
        }

        Observable<JSONObject> rxExec() {
            url = checkNotNull(url);
            if (loadType == LoadFileType.DOWNLOAD) {
                destFilePath = checkNotNull(destFilePath);
            } else {
//                checkArgument(uploadFilePaths != null && !uploadFilePaths.isEmpty(), "No File to upload");
            }
            return HttpOperation.getInstance().rxRequest(this);

        }

        @Override
        public String toString() {
            return "LoadFileRequestBuilder{" +
                    "url='" + url + '\'' +
                    ", loadType=" + loadType +
                    ", uploadFilePaths=" + uploadFilePaths +
                    ", destFilePath='" + destFilePath + '\'' +
                    '}';
        }
    }

    private Observable<JSONObject> rxRequest(LoadFileRequestBuilder loadFileRequestBuilder) {
        printRequestInfo(loadFileRequestBuilder);
        switch (loadFileRequestBuilder.loadType) {
            case LoadFileType.DOWNLOAD:
                return rxDownload(loadFileRequestBuilder);
            case LoadFileType.UPLOAD:
                return rxUpload(loadFileRequestBuilder);
        }
        return null;
    }


    private void request(LoadFileRequestBuilder loadFileRequestBuilder) {
        switch (loadFileRequestBuilder.loadType) {
            case LoadFileType.DOWNLOAD:
                download(loadFileRequestBuilder);
                break;
            case LoadFileType.UPLOAD:
                upload(loadFileRequestBuilder);
                break;
        }
    }

    private static final String KEY_LOAD_STATE = "LOAD_STATE";//上传下载的状态。其中 0 是进度，1是成功 2是失败
    private static final String KEY_VALUE = "VALUE";
    private static final String KEY_EXTRA_DATA = "EXTRA_DATA";

    private static final int STATE_PROGRESS = 0;
    private static final int STATE_SUCCESS = 1;


    private Observable<JSONObject> rxUpload(LoadFileRequestBuilder loadFileRequestBuilder) {

        return Observable.create((ObservableOnSubscribe<JSONObject>) e -> {
            RequestBody body = buildRequestBody(loadFileRequestBuilder.params, loadFileRequestBuilder.uploadFilePaths, (bytesWrite, contentLength, done, path) -> {
                float progress = done ? 100f : (float) bytesWrite / contentLength * 100;
                JSONObject progressJson = new JSONObject();
                try {
                    progressJson.put(KEY_LOAD_STATE, STATE_PROGRESS);
                    progressJson.put(KEY_VALUE, progress);
                    progressJson.put(KEY_EXTRA_DATA, path);
                    e.onNext(progressJson);
                } catch (JSONException e1) {
                    e1.printStackTrace();
                }
            });

            Request request = loadFileRequestBuilder.okHttpBuilder.post(body).build();
            Call call = okHttpClient.newCall(request);
//            e.setCancellable(() -> {
//                if (!call.isCanceled()) {
//                    call.cancel();
//                }
//            });
//            e.setDisposable(new Disposable() {
//                @Override
//                public void dispose() {
//                    Log.i(TAG, ": ======================");
//                    if (!call.isCanceled()) {
//                        call.cancel();
//                    }
//                }
//
//                @Override
//                public boolean isDisposed() {
//                    return true;
//                }
//            });

            if (!call.isCanceled()) {

                Response response = call.execute();

                JSONObject res = dealWithResponse(response, loadFileRequestBuilder.requestId);
                JSONObject successJson = new JSONObject();
                successJson.put(KEY_LOAD_STATE, STATE_SUCCESS);
                successJson.put(KEY_VALUE, res.optString(KEY_return_value));
                successJson.put(KEY_EXTRA_DATA, loadFileRequestBuilder.requestId);
                e.onNext(successJson);
                e.onComplete();
            } else {
                e.onError(new Exception("未知的错误"));
            }
        }).subscribeOn(Schedulers.io());

    }

    private void upload(LoadFileRequestBuilder loadFileRequestBuilder) {
        Observable.create((ObservableOnSubscribe<DownloadProgress<String>>) e -> {
            RequestBody body = buildRequestBody(loadFileRequestBuilder.params, loadFileRequestBuilder.uploadFilePaths, (bytesWrite, contentLength, done, path) -> {
                float progress = done ? 1f : (float) bytesWrite / contentLength * 100;
                e.onNext(new DownloadProgress<>(progress, path));
            });
            Request request = loadFileRequestBuilder.okHttpBuilder.post(body).build();
            Call call = okHttpClient.newCall(request);
//            e.setCancellable(() -> {
//                if (!call.isCanceled()) {
//                    call.cancel();
//                }
//                requestMap.remove(loadFileRequestBuilder.requestId);
//            });

//            e.setDisposable(new Disposable() {
//                @Override
//                public void dispose() {
//                    if (!call.isCanceled()) {
//                        call.cancel();
//                    }
//                    requestMap.remove(loadFileRequestBuilder.requestId);
//                }
//
//                @Override
//                public boolean isDisposed() {
//                    return true;
//                }
//            });
            Response response = call.execute();
//                if (response.isSuccessful()) {
//
//                }
            JSONObject res = dealWithResponse(response, loadFileRequestBuilder.requestId);
            loadFileRequestBuilder.successCallBack.onSuccess(loadFileRequestBuilder.requestId, res.optString("result"));
            e.onComplete();
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).sample(1, TimeUnit.SECONDS).subscribe(downloadProgress -> {
                    if (loadFileRequestBuilder.progressCallBack != null) {
                        loadFileRequestBuilder.progressCallBack.onProgress(loadFileRequestBuilder.requestId, downloadProgress.data, downloadProgress.progress);
                    }
                },
                throwable -> {
                    if (loadFileRequestBuilder.failCallBack != null) {
                        loadFileRequestBuilder.failCallBack.onFailed(loadFileRequestBuilder.requestId, throwable);
                    }
                    requestMap.remove(loadFileRequestBuilder.requestId);
                }, () -> requestMap.remove(loadFileRequestBuilder.requestId), disposable -> {
                    if (!TextUtils.isEmpty(loadFileRequestBuilder.requestId)) {
                        requestMap.put(loadFileRequestBuilder.requestId, disposable);
                    }
                });
    }

    private RequestBody buildRequestBody(JSONObject params, Set<FileInfo> filePathList, ProgressRequestBody.ProgressListener listener) {
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder();
        bodyBuilder.setType(MultipartBody.FORM);
        if (params != null) {
            Iterator<String> it = params.keys();
            while (it.hasNext()) {
                String key = it.next();
                String value = params.optString(key);
                bodyBuilder.addPart(MultipartBody.Part.createFormData(key, value));
            }
        }

        RequestBody fileBody;
        if (filePathList != null) {
            for (FileInfo fileInfo : filePathList) {
                File file = new File(fileInfo.filePath);
                String fileName = file.getName();
                fileBody = new ProgressRequestBody(file, FileUtils.guessMimeType(fileName), listener);
//            bodyBuilder.addPart(MultipartBody.Part.createFormData(fileInfo.fileKey == null ? "uploadfile" : fileInfo.fileKey,
//                    fileInfo.fileName == null ? fileName : fileInfo.fileName, fileBody));

                Log.i(TAG, "buildRequestBody: " + fileInfo.toString());
                bodyBuilder.addFormDataPart(fileInfo.fileKey == null ? "uploadfile" : fileInfo.fileKey,
                        fileInfo.fileName == null ? fileName : fileInfo.fileName, fileBody);
            }
        }
        return bodyBuilder.build();
    }

    private static class FileInfo {
        String filePath;
        String fileKey;
        String fileName;


        public FileInfo(String filePath, String fileKey, String fileName) {
            this.filePath = filePath;
            this.fileKey = fileKey;
            this.fileName = fileName;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof FileInfo)) {
                return false;
            }
            FileInfo another = (FileInfo) obj;
            return filePath.equals(another.filePath) && fileName.equals(another.fileName) && fileKey.equals(another.fileKey);
        }

        @Override
        public int hashCode() {
            return filePath.hashCode() + fileName.hashCode() + fileKey.hashCode();
        }

        @Override
        public String toString() {
            return "FileInfo{" +
                    "filePath='" + filePath + '\'' +
                    ", fileName='" + fileName + '\'' +
                    ", fileKey='" + fileKey + '\'' +
                    '}';
        }
    }


    private Observable<JSONObject> rxDownload(LoadFileRequestBuilder loadFileRequestBuilder) {
        return Observable.create((ObservableOnSubscribe<JSONObject>) e -> {
            if (loadFileRequestBuilder.params != null) {
                RequestBody body = getRequestBody(loadFileRequestBuilder.params);
                loadFileRequestBuilder.okHttpBuilder.post(body);
            }
            Request request = loadFileRequestBuilder.okHttpBuilder.build();
            Call call = okHttpClient.newCall(request);
//            e.setCancellable(() -> {
//                if (!call.isCanceled()) {
//                    call.cancel();
//                }
//                FileUtils.deleteFile(loadFileRequestBuilder.destFilePath + ".download");
//            });

//            e.setDisposable(new Disposable() {
//                @Override
//                public void dispose() {
//                    if (!call.isCanceled()) {
//                        call.cancel();
//                    }
//                    FileUtils.deleteFile(loadFileRequestBuilder.destFilePath + ".download");
//                }
//
//                @Override
//                public boolean isDisposed() {
//                    return true;
//                }
//            });

            if (!call.isCanceled()) {
                Response response = call.execute();
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        JSONObject progressJson = new JSONObject();
                        ProgressResponseBody progressResponseBody = new ProgressResponseBody(body, (bytesRead, contentLength, done) -> {
                            float progress = done ? 100f : (float) bytesRead / contentLength * 100;
                            Log.d(TAG, "rxDownload() called with: loadFileRequestBuilder = [" + loadFileRequestBuilder + "]");
                            try {
                                progressJson.put(KEY_LOAD_STATE, STATE_PROGRESS);
                                progressJson.put(KEY_VALUE, progress);
                                e.onNext(progressJson);
                            } catch (JSONException e1) {
                                e1.printStackTrace();
                            }
                        });
                        String tempFilePath = loadFileRequestBuilder.destFilePath + ".download";
                        BufferedSink sink = Okio.buffer(Okio.sink(new File(tempFilePath)));
                        sink.writeAll(progressResponseBody.source());
                        sink.close();
                        JSONObject successJson = new JSONObject();
                        successJson.put(KEY_LOAD_STATE, STATE_SUCCESS);
                        e.onNext(successJson);
                        if (FileUtils.renameFile(tempFilePath, loadFileRequestBuilder.destFilePath)) {
                            e.onComplete();
                        } else {
                            e.onError(new Exception("ReName File failed"));
                        }
                    } else {
                        throw new EmptyBodyException();
                    }
                } else {
                    throw new ErrorCodeException(response.code());
                }
            } else {
                e.onError(new Exception("未知的错误"));
            }
        }).subscribeOn(Schedulers.io());
    }


    private void download(LoadFileRequestBuilder loadFileRequestBuilder) {
        Observable.create((ObservableOnSubscribe<DownloadProgress>) e -> {
            if (loadFileRequestBuilder.params != null) {
                RequestBody body = getRequestBody(loadFileRequestBuilder.params);
                loadFileRequestBuilder.okHttpBuilder.post(body);
            }
            Request request = loadFileRequestBuilder.okHttpBuilder.build();
            Call call = okHttpClient.newCall(request);
//            e.setCancellable(() -> {
//                if (!call.isCanceled()) {
//                    call.cancel();
//                }
//                requestMap.remove(loadFileRequestBuilder.requestId);
//                FileUtils.deleteFile(loadFileRequestBuilder.destFilePath + ".download");
//            });
            e.setDisposable(new Disposable() {
                @Override
                public void dispose() {
                    if (!call.isCanceled()) {
                        call.cancel();
                    }
                    requestMap.remove(loadFileRequestBuilder.requestId);
                    FileUtils.deleteFile(loadFileRequestBuilder.destFilePath + ".download");
                }

                @Override
                public boolean isDisposed() {
                    return true;
                }
            });

            Response response = call.execute();
            if (response.isSuccessful()) {
                ResponseBody body = response.body();
                if (body != null) {
                    ProgressResponseBody progressResponseBody = new ProgressResponseBody(body, (bytesRead, contentLength, done) -> {
                        float progress = done ? 1f : (float) bytesRead / contentLength;
                        e.onNext(new DownloadProgress<>(progress, null));
                    });
                    String tempFilePath = loadFileRequestBuilder.destFilePath + ".download";
                    BufferedSink sink = Okio.buffer(Okio.sink(new File(tempFilePath)));
                    sink.writeAll(progressResponseBody.source());
                    sink.close();
                    if (FileUtils.renameFile(tempFilePath, loadFileRequestBuilder.destFilePath)) {
                        e.onComplete();
                    } else {
                        e.onError(new Exception("ReName File failed"));
                    }
                } else {
                    throw new EmptyBodyException();
                }
            } else {
                throw new ErrorCodeException(response.code());
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).sample(1, TimeUnit.SECONDS)
                .subscribe(downloadProgress -> {
                    if (loadFileRequestBuilder.progressCallBack != null) {
                        loadFileRequestBuilder.progressCallBack.onProgress(loadFileRequestBuilder.requestId,
                                loadFileRequestBuilder.destFilePath, downloadProgress.progress);
                    }
                }, throwable -> {
                    if (loadFileRequestBuilder.failCallBack != null) {
                        loadFileRequestBuilder.failCallBack.onFailed(loadFileRequestBuilder.requestId, throwable);
                    }
                    FileUtils.deleteFile(loadFileRequestBuilder.destFilePath + ".download");
                    requestMap.remove(loadFileRequestBuilder.requestId);
                }, () -> {
                    loadFileRequestBuilder.successCallBack.onSuccess(loadFileRequestBuilder.requestId, loadFileRequestBuilder.destFilePath);
                    requestMap.remove(loadFileRequestBuilder.requestId);
                }, disposable -> {
                    if (!TextUtils.isEmpty(loadFileRequestBuilder.requestId)) {
                        requestMap.put(loadFileRequestBuilder.requestId, disposable);
                    }
                });
    }

    private void request(IRequestBuilder iRequestBuilder) {
        switch (iRequestBuilder.method) {
            case Method.GET:
                get(iRequestBuilder);
                break;
            case Method.POST:
                post(iRequestBuilder);
                break;
        }
    }


    private Observable<JSONObject> multiRequest(ArrayList<IRequestBuilder> builderArrayList) {
        ArrayList<Observable<JSONObject>> requestList = new ArrayList<>();
        for (IRequestBuilder builder : builderArrayList) {
            requestList.add(builder.rxExec());
        }
        return Observable.mergeDelayError(new Iterable<Observable<JSONObject>>() {
            @android.support.annotation.NonNull
            @Override
            public Iterator<Observable<JSONObject>> iterator() {
                return requestList.iterator();
            }
        });
    }

    private Observable<JSONObject> rxRequest(IRequestBuilder builder) {
        printRequestInfo(builder);
        switch (builder.method) {
            case Method.GET:
                return rxGet(builder);
            case Method.POST:
                return rxPost(builder);
        }
        return null;
    }

    private Observable<JSONObject> rxPost(IRequestBuilder iRequestBuilder) {
        return Observable.create((ObservableEmitter<JSONObject> e) -> {
            RequestBody requestBody = null;
            switch (iRequestBuilder.bodyType) {
                case PostBodyType.FORM:
                    requestBody = getRequestBody(iRequestBuilder);
                    break;
                case PostBodyType.JSON:
                    requestBody = RequestBody.create(JSON_TYPE, iRequestBuilder.params.toString());
                    break;
            }
            final Request.Builder builder = iRequestBuilder.okHttpBuilder;
            assert requestBody != null;
            Request request = builder.post(requestBody).build();
//            Request request = builder.post(requestBody).addHeader("Connection","close").build();

            if (!TextUtils.isEmpty(iRequestBuilder.cachePath) && !iRequestBuilder.isCacheHaveSend) {
                File file = new File(iRequestBuilder.cachePath);
                Log.i(TAG, "rxPost: create response");
                Response response = createResponseFromLocalFile(request, file);
                iRequestBuilder.isCacheHaveSend = true;
                e.onNext(dealWithResponse(response, iRequestBuilder.requestId));
            }


            Call call = okHttpClient.newCall(request);
//            e.setCancellable(() -> {
//                Log.i(TAG, ": cancel=========" + iRequestBuilder.url);
//                if (!call.isCanceled()) {
//                    call.cancel();
//                }
//            });
//            e.setDisposable(new Disposable() {
//                @Override
//                public void dispose() {
//                    Log.i(TAG, ": cancel=========" + iRequestBuilder.url);
//                    if (!call.isCanceled()) {
//                        call.cancel();
//                    }
//                }
//
//                @Override
//                public boolean isDisposed() {
//                    return true;
//                }
//            });

            if (!call.isCanceled()) {
                Response response = call.execute();
                e.onNext(dealWithResponse(response, iRequestBuilder.requestId));
                e.onComplete();
            } else {
                e.onError(new Exception("未知的错误"));
            }
//            call.enqueue(new Callback() {
//                @Override
//                public void onFailure(@android.support.annotation.NonNull Call call, @android.support.annotation.NonNull IOException ex) {
//                    e.onError(ex);
//                }
//
//                @Override
//                public void onResponse(@android.support.annotation.NonNull @NonNull Call call, @android.support.annotation.NonNull Response response) throws IOException {
//                    if (!call.isCanceled()) {
//                        e.onNext(response);
//                        e.onComplete();
//                    } else {
//                        e.onError(new CallCancelException());
//                    }
//                }
//            });
        }).subscribeOn(Schedulers.io());
    }

    private Response createResponseFromLocalFile(Request request, final File file) throws FileNotFoundException {
        Source fileSource = Okio.source(file);
        return new Response.Builder().request(request).body(new ResponseBody() {
            @Nullable
            @Override
            public MediaType contentType() {
                return JSON_TYPE;
            }

            @Override
            public long contentLength() {
                return file.length();
            }

            @Override
            public BufferedSource source() {
                return Okio.buffer(fileSource);
            }
        }).protocol(Protocol.HTTP_1_1).code(200).message("Cache").build();
    }

    private Observable<JSONObject> rxGet(IRequestBuilder builder) {
        return Observable.create((ObservableEmitter<JSONObject> em) -> {
            Request request;

//            HttpUrl.Builder urlBuilder = new HttpUrl.Builder();
            String url = builder.url;
            if (builder.params != null) {
                url = builder.url + "?" + getRequestUrl(builder);
//                builder.url(url);
            }
            request = builder.okHttpBuilder.get().url(url).build();
//            request = builder.okHttpBuilder.get().url(url).addHeader("Connection","close").build();

            if (!TextUtils.isEmpty(builder.cachePath) && !builder.isCacheHaveSend) {
                File file = new File(builder.cachePath);
                Response response = createResponseFromLocalFile(request, file);
                builder.isCacheHaveSend = true;
                em.onNext(dealWithResponse(response, builder.requestId));
            }

            Call call = okHttpClient.newCall(request);
//            em.setCancellable(() -> {
//                if (!call.isCanceled())
//                    call.cancel();
//            });
//            em.setDisposable(new Disposable() {
//                @Override
//                public void dispose() {
//                    if (!call.isCanceled())
//                        call.cancel();
//                }
//
//                @Override
//                public boolean isDisposed() {
//                    return true;
//                }
//            });

            if (!call.isCanceled()) {
                Response response = call.execute();
                Log.i(TAG, "rxGet: " + (response == null));
                em.onNext(dealWithResponse(response, builder.requestId));
                em.onComplete();
            } else {
                em.onError(new Exception("未知的错误"));
            }
//            call.enqueue(new Callback() {
//                @Override
//                public void onFailure(@android.support.annotation.NonNull Call call, @android.support.annotation.NonNull IOException ex) {
//                    em.onError(ex);
//                }
//
//                @Override
//                public void onResponse(@android.support.annotation.NonNull @NonNull Call call, @android.support.annotation.NonNull Response response) throws IOException {
//                    if (!call.isCanceled()) {
//                        em.onNext(response);
//                        em.onComplete();
//                    } else {
//                        em.onError(new CallCancelException());
//                    }
//                }
//            });

        }).subscribeOn(Schedulers.io());
    }

    private void post(IRequestBuilder iRequestBuilder) {
        Observable.create((ObservableOnSubscribe<Response>) e -> {
            RequestBody requestBody = null;
            switch (iRequestBuilder.bodyType) {
                case PostBodyType.FORM:
                    requestBody = getRequestBody(iRequestBuilder);
                    break;
                case PostBodyType.JSON:
                    requestBody = RequestBody.create(JSON_TYPE, iRequestBuilder.params.toString());
                    break;
            }
            final Request.Builder builder = iRequestBuilder.okHttpBuilder;
            assert requestBody != null;
            Request request = builder.post(requestBody).build();
            Call call = okHttpClient.newCall(request);
//            e.setCancellable(() -> {
//                if (!call.isCanceled()) {
//                    call.cancel();
//                }
//                requestMap.remove(iRequestBuilder.requestId);
//            });

            e.setDisposable(new Disposable() {
                @Override
                public void dispose() {
                    if (!call.isCanceled()) {
                        call.cancel();
                    }
                    requestMap.remove(iRequestBuilder.requestId);
                }

                @Override
                public boolean isDisposed() {
                    return true;
                }
            });

            call.enqueue(new Callback() {
                @Override
                public void onFailure(@android.support.annotation.NonNull Call call, @android.support.annotation.NonNull IOException ex) {
                    e.onError(ex);
                }

                @Override
                public void onResponse(@android.support.annotation.NonNull @NonNull Call call, @android.support.annotation.NonNull Response response) throws IOException {
                    if (!call.isCanceled()) {
                        e.onNext(response);
                        e.onComplete();
                    } else {
                        e.onError(new CallCancelException());
                    }
                }
            });
        }).map(response -> dealWithResponse(response, iRequestBuilder.requestId)).subscribe(jsonObject -> iRequestBuilder.successCallBack.onSuccess(iRequestBuilder.requestId, jsonObject.optInt("type"), jsonObject.optString("result")), throwable -> {
            if (iRequestBuilder.failedCallBack != null)
                iRequestBuilder.failedCallBack.onFailed(iRequestBuilder.requestId, throwable);
            requestMap.remove(iRequestBuilder.requestId);
        }, () -> requestMap.remove(iRequestBuilder.requestId), disposable -> {
            if (!TextUtils.isEmpty(iRequestBuilder.requestId)) {
                requestMap.put(iRequestBuilder.requestId, disposable);
            }
        });
    }

    @android.support.annotation.NonNull
    private RequestBody getRequestBody(IRequestBuilder iRequestBuilder) {
        RequestBody requestBody;

        FormBody.Builder builder = new FormBody.Builder();
        for (String key : iRequestBuilder.keys) {
            builder.add(key, iRequestBuilder.params.optString(key));
        }
        requestBody = builder.build();
        return requestBody;
    }

    @android.support.annotation.NonNull
    private RequestBody getRequestBody(JSONObject params) {
        RequestBody requestBody;
        FormBody.Builder builder = new FormBody.Builder();
        Iterator<String> it = params.keys();
        while (it.hasNext()) {
            String key = it.next();
            builder.add(key, params.optString(key));
        }
        requestBody = builder.build();
        return requestBody;
    }


    /**
     * 同步http get方法
     */
    private void get(IRequestBuilder builder) {
        Observable.create((ObservableOnSubscribe<JSONObject>) em -> {
            Request request;
            if (builder.params != null) {
                String url = builder.url + "?" + getRequestUrl(builder.params);
                builder.url(url);
            }
            request = builder.okHttpBuilder.build();
            Response response;
            try {
                Call call = okHttpClient.newCall(request);
//                em.setCancellable(() -> {
//                    if (!call.isCanceled())
//                        call.cancel();
//                    requestMap.remove(builder.requestId);
//                });
                em.setDisposable(new Disposable() {
                    @Override
                    public void dispose() {
                        if (!call.isCanceled())
                            call.cancel();
                        requestMap.remove(builder.requestId);
                    }

                    @Override
                    public boolean isDisposed() {
                        return true;
                    }
                });
                response = call.execute();
                if (call.isCanceled()) {
                    em.onError(new CallCancelException());
                } else {
                    JSONObject res = dealWithResponse(response, builder.requestId);
                    em.onNext(res);
                    em.onComplete();
                }
            } catch (IOException | JSONException e) {
                em.onError(e);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(
                jsonObject -> builder.successCallBack.onSuccess(builder.requestId, jsonObject.optInt("type"), jsonObject.optString("result")),
                throwable -> {
                    if (builder.failedCallBack != null)
                        builder.failedCallBack.onFailed(builder.requestId, throwable);
                    requestMap.remove(builder.requestId);
                }, () -> requestMap.remove(builder.requestId), disposable -> {
                    if (!TextUtils.isEmpty(builder.requestId))
                        requestMap.put(builder.requestId, disposable);
                });
    }

    private String getRequestUrl(IRequestBuilder iRequestBuilder) {
        String url = "";
        for (int i = 0; i < iRequestBuilder.keys.size(); i++) {
            String key = iRequestBuilder.keys.get(i);
            String value = iRequestBuilder.params.optString(key);
            if (i == 0)
                url = url + key + "=" + value;
            else
                url = url + "&" + key + "=" + value;
        }
        return url;
    }

    private String getRequestUrl(JSONObject jsonParam) {
        Iterator<String> keys = jsonParam.keys();
        String url = "";
        int i = 0;
        while (keys.hasNext()) {
            String key = keys.next();
            String value = jsonParam.optString(key);
            if (i == 0)
                url = url + key + "=" + value;
            else
                url = url + "&" + key + "=" + value;
            i++;
        }
        return url;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private JSONObject dealWithResponse(Response response, String requestId) throws IOException, JSONException {
        String responseMsg = TextUtils.isEmpty(response.message()) ? "" : response.message();
        if (response.isSuccessful() || response.code() == 700) {
            ResponseBody body = response.body();
            if (body != null) {
                long length = body.contentLength();
                Log.i(TAG, "dealWithResponse: " + length);
//                if (length != -1 && length < MB) {
                String result = body.string();
                if (TextUtils.isEmpty(result))
                    result = "";
                JSONObject res = new JSONObject();
                if (responseMsg.equals("Cache")) {
                    res.put(KEY_return_type, ReturnType.CACHE);
                } else {
                    res.put(KEY_return_type, ReturnType.STRING);
                }
                res.put(KEY_return_value, result);
                if (!TextUtils.isEmpty(requestId)) {
                    res.put(KEY_request_id, requestId);
                }
                return res;
//                } else {
//                    File file = new File(FileUtils.getHttpCachePath() + EncryptUtil.md5(TextUtils.isEmpty(builder.requestId) ? builder.url : builder.requestId));
//                    BufferedSink sink = Okio.buffer(Okio.sink(file));
//                    sink.writeAll(body.source());
//                    sink.close();
//                    JSONObject res = new JSONObject();
//                    Log.i(TAG, "dealWithResponse: " + file.length());
//                    if (file.length() < MB) {
//                        Source source = Okio.source(file);
//                        String result = Okio.buffer(source).readUtf8();
//                        source.close();
//                        if (responseMsg.equals("Cache")) {
//                            res.put(KEY_return_type, ReturnType.CACHE);
//                        } else {
//                            res.put(KEY_return_type, ReturnType.STRING);
//                        }
//                        res.put(KEY_return_value, result);
//                        file.delete();
//                        Log.i(TAG, "dealWithResponse: " + res);
//                        return res;
//                    }
//
//                    if (responseMsg.equals("Cache")) {
//                        res.put(KEY_return_type, ReturnType.CACHE);
//                    } else {
//                        res.put(KEY_return_type, ReturnType.FILE);
//                        res.put(KEY_return_value, file.getAbsolutePath());
//                    }
//                    Log.i(TAG, "dealWithResponse: " + res);
//                    return res;
//                }
            } else {
                throw new EmptyBodyException();
            }
        } else {
            Log.i(TAG, "dealWithResponse: " + response.code() + response.body().string());
            throw new ErrorCodeException(response.code());
        }
    }

    public void cancelRequest(String requestId) {
        Disposable disposable = requestMap.get(requestId);
        if (disposable != null) {
            disposable.dispose();
            requestMap.remove(requestId);
        }
    }

    private void printRequestInfo(IRequestBuilder builder) {
        Log.d(TAG, "request:" + builder.url);
        Log.d(TAG, "method:" + (builder.method == Method.GET ? "Get" : "Post"));
        if (builder.params != null)
            Log.d(TAG, "params:" + builder.params.toString());
        if (builder.headers != null) {
            Log.d(TAG, "headers:" + builder.headers.toString());
        }
    }

    private void printRequestInfo(LoadFileRequestBuilder builder) {
        Log.d(TAG, "request:" + builder.url);
        Log.d(TAG, "type:" + (builder.loadType == LoadFileType.DOWNLOAD ? "Download" : "Upload"));
        Log.d(TAG, "path:" + (builder.loadType == LoadFileType.DOWNLOAD ? builder.destFilePath : builder.uploadFilePaths));
    }
}
