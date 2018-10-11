package com.inspur.icity.recoder.utils;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.inspur.icity.recoder.BuildConfig;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.FileNameMap;
import java.net.URLConnection;

import okio.BufferedSink;
import okio.Okio;

/**
 * Created by fanjsh on 2017/8/9.
 */


public class FileUtils {
    private static final String TAG = "FileUtils";

    private static String SDCardRoot, cacheFilePath, privatePath;
    private final static String ENV_SECONDARY_STORAGE = "SECONDARY_STORAGE";


    public static String getLogPath() {
        return cacheFilePath + "iCity" + File.separator + "log" + File.separator;
    }

    public static void init(Context context) {
        privatePath = context.getFilesDir().getAbsolutePath();
        if (Build.VERSION.SDK_INT >= 19 && Build.VERSION.SDK_INT < 24) {
            File[] externalFileDirs = context.getExternalFilesDirs(null);
            File[] exiernalCacheDirs = context.getExternalCacheDirs();
            if (externalFileDirs.length > 1) {
                if (externalFileDirs[1] != null) {
                    SDCardRoot = externalFileDirs[1].getAbsolutePath() + File.separator;
                    cacheFilePath = exiernalCacheDirs[1].getAbsolutePath() + File.separator;
                } else if (externalFileDirs[0] != null) {
                    SDCardRoot = externalFileDirs[0].getAbsolutePath() + File.separator;
                    cacheFilePath = exiernalCacheDirs[0].getAbsolutePath() + File.separator;
                } else {
                    SDCardRoot = context.getFilesDir().getAbsolutePath() + File.separator;
                    cacheFilePath = context.getCacheDir() + File.separator;
                }
            } else if (externalFileDirs.length == 1) {
                if (exiernalCacheDirs[0] != null) {
                    SDCardRoot = externalFileDirs[0].getAbsolutePath() + File.separator;
                    cacheFilePath = exiernalCacheDirs[0].getAbsolutePath() + File.separator;
                } else {
                    SDCardRoot = context.getFilesDir().getAbsolutePath() + File.separator;
                    cacheFilePath = context.getCacheDir() + File.separator;
                }
            } else {
                SDCardRoot = context.getFilesDir().getAbsolutePath() + File.separator;
                cacheFilePath = context.getCacheDir() + File.separator;
            }
        } else if (Build.VERSION.SDK_INT >= 24) {
            File[] externalFileDirs = context.getExternalFilesDirs(null);
            File[] exiernalCacheDirs = context.getExternalCacheDirs();
            if (externalFileDirs.length > 0) {
                SDCardRoot = externalFileDirs[0].getAbsolutePath() + File.separator;
            } else {
                SDCardRoot = context.getFilesDir().getAbsolutePath() + File.separator;
            }
            if (exiernalCacheDirs.length > 0) {
                cacheFilePath = exiernalCacheDirs[0].getAbsolutePath() + File.separator;
            } else {
                cacheFilePath = context.getCacheDir() + File.separator;
            }
        } else {
            SDCardRoot = System.getenv(ENV_SECONDARY_STORAGE);
            if (SDCardRoot != null) {
                SDCardRoot = SDCardRoot + File.separator + "Android" + File.separator + "data" + File.separator + context.getPackageName()
                        + File.separator + "files" + File.separator;
                cacheFilePath = SDCardRoot + File.separator + "Android" + File.separator + "data" + File.separator + context.getPackageName()
                        + File.separator + "cache" + File.separator;
            } else {
                SDCardRoot = context.getFilesDir().getAbsolutePath() + File.separator;
                cacheFilePath = context.getCacheDir() + File.separator;
            }


            File fileRoot = new File(SDCardRoot);
            File cacheRoot = new File(cacheFilePath);
            if (!fileRoot.exists()) {
                boolean createResult = fileRoot.mkdirs();
                if (!createResult) {
                    Log.e(TAG, "创建文件夹失败");
                }
            }

            if (!cacheRoot.exists()) {
                boolean createResult = cacheRoot.mkdirs();
                if (!createResult) {
                    Log.e(TAG, "创建缓存文件夹失败");
                }
            }
        }

        Log.i(TAG, "init: " + SDCardRoot);
        Log.i(TAG, "init: " + cacheFilePath);

        File file = new File(SDCardRoot + "iCity");
        File cacheFile = new File(cacheFilePath + "iCity");
        createDir(file, "创建iCity文件夹失败");
        createDir(cacheFile, "创建缓存文件夹失败");

        createDir(new File(getHttpCachePath()), "创建http缓存文件夹失败");

        createDir(new File(getLogPath()), "创建日志文件夹失败");

        createDir(new File(getAdImagePath()), "创建广告文件夹失败");

        createDir(new File(getTempPath()), "创建临时文件夹失败");


    }

    public static String getTempPath() {
        return SDCardRoot + "iCity" + File.separator + "temp" + File.separator;
    }

    public static String getSDCardRoot() {
        return SDCardRoot;
    }

    public static String getAdImagePath() {
        return SDCardRoot + "iCity" + File.separator + "ad" + File.separator;
    }


    private static void createDir(File fileImage, String logInfo) {
        if (!fileImage.exists()) {
            boolean result = fileImage.mkdir();
            if (!result)
                Log.e("fileUtil", logInfo);
        }
    }

    public static String getHttpCachePath() {
        return cacheFilePath + "iCity" + File.separator + "httpCache" + File.separator;
    }

    public static void deleteHttpCache() {
        File cache = new File(getHttpCachePath());
//
        for (File file : cache.listFiles()) {
//            if(file.isDirectory()){
//                file.delete();
//            }else{
//                file.delete();
//            }
            deleteDir(file);
        }
    }

    private static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
//            String[] children = dir.listFiles();
            for (File file : dir.listFiles()) {
                boolean success = deleteDir(file);
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }

    public static String getPrivatePath() {
        return privatePath + File.separator;
    }

    public static String getCrashPath() {
        return SDCardRoot + "iCity" + File.separator + "crash" + File.separator;
    }

    public static String guessMimeType(String path) {
        FileNameMap fileNameMap = URLConnection.getFileNameMap();
        String contentTypeFor = fileNameMap.getContentTypeFor(path);
        if (contentTypeFor == null) {
            contentTypeFor = "application/octet-stream";
        }
        return contentTypeFor;
    }

    public static boolean renameFile(String oldPath, String newPath) throws FileNotFoundException {
        File oldFile = new File(oldPath);
        if (!oldFile.exists()) {
            throw new FileNotFoundException(oldPath);
        }
        Log.d(TAG, "renameFile() called with: oldPath = [" + oldPath + "], newPath = [" + newPath + "]");
        File newFile = new File(newPath);
        return oldFile.renameTo(newFile);
    }

    public static boolean deleteFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public static boolean deleteFile(File file) {
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }


    public static File getCameraImg(String imgName) {
        return new File(getTempPath() + File.separator + imgName);
    }


    public static Intent getOpenFileIntent(Context context, String filePath) {
        File file = new File(filePath);
        if (!file.exists())
            return null;
        String mime = getMimeType(Uri.fromFile(file).toString());
        Log.d(TAG, "getOpenFileIntent: " + mime);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 24) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            Uri uri = FileProvider.getUriForFile(context, "com.inspur.icity.icityapp.fileProvider", file);
            Log.d(TAG, "getOpenFileIntent: " + uri.toString());
            intent.setDataAndType(uri, mime);
        } else {
            Uri uri = Uri.fromFile(file);
            intent.setDataAndType(uri, mime);
        }
        return intent;
    }

    private static String getMimeType(String url) {
        String type;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url).toLowerCase();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        type = mime.getMimeTypeFromExtension(extension);
        return type;
    }

    public static boolean checkFileExist(File file) {
        return file.exists();
    }

    /**
     * 根据Uri获取图片绝对路径，解决Android4.4以上版本Uri转换
     *
     * @param context
     * @param imageUri
     */
    @TargetApi(19)
    public static String getImageAbsolutePath(Context context, Uri imageUri) {
        if (context == null || imageUri == null)
            return null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, imageUri)) {
            if (isExternalStorageDocument(imageUri)) {
                String docId = DocumentsContract.getDocumentId(imageUri);
                String[] split = docId.split(":");
                String type = split[0];
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if (isDownloadsDocument(imageUri)) {
                String id = DocumentsContract.getDocumentId(imageUri);
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            } else if (isMediaDocument(imageUri)) {
                String docId = DocumentsContract.getDocumentId(imageUri);
                String[] split = docId.split(":");
                String type = split[0];
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                String selection = MediaStore.Images.Media._ID + "=?";
                String[] selectionArgs = new String[]{split[1]};
                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } // MediaStore (and general)
        else if ("content".equalsIgnoreCase(imageUri.getScheme())) {
            // Return the remote address
            if (isGooglePhotosUri(imageUri))
                return imageUri.getLastPathSegment();
            return getDataColumn(context, imageUri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(imageUri.getScheme())) {
            return imageUri.getPath();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        String column = MediaStore.Images.Media.DATA;
        String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    public static String getDataCachePath() {
        return "/data/data/" + BuildConfig.APPLICATION_ID + "/dateCache";
    }

    /**
     * 写入文件
     *
     * @param strcontent 写入文本内容
     * @param filePath   文件路径
     * @param fileName   文件名称
     */
    public static void writeToFile(String strcontent, String filePath, String fileName) {
        //生成文件夹之后，再生成文件，不然会出错
//        makeFilePath(filePath, fileName);

        String path = filePath + "/" + fileName;
        File file = new File(path);
        try {
            if (!file.exists()) {
                File dir = new File(file.getParent());
                dir.mkdirs();
                file.createNewFile();
            }
            FileOutputStream outStream = new FileOutputStream(file);
            outStream.write(strcontent.getBytes());
            outStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String readFile(String fileName) {
        StringBuffer sb = new StringBuffer("");
        File file = new File(fileName);

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] temp = new byte[1024];

            int len = 0;
            //读取文件内容:
            while ((len = fis.read(temp)) > 0) {
                sb.append(new String(temp, 0, len));
            }
            //关闭输入流
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


        return sb.toString();
    }


    // 生成文件
    private static File makeFilePath(String filePath, String fileName) {
        File file = null;
        makeRootDirectory(filePath);
        try {
            file = new File(filePath + fileName);
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    // 生成文件夹
    public static void makeRootDirectory(String filePath) {
        File file = null;
        try {
            file = new File(filePath);
            if (!file.exists()) {
                file.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
