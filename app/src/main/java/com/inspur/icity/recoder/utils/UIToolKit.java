package com.inspur.icity.recoder.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.widget.Toast;


public class UIToolKit {

    public static void showToastShort(Context context, String info) {
        if (context == null || info == null || "".equals(info))
            return;

        Toast mToast = Toast.makeText(context, info, Toast.LENGTH_SHORT);
        mToast.show();
    }

    public static void showToastShort(Context context, String info, int durtion) {
        if (context == null || info == null || "".equals(info))
            return;

        Toast mToast = Toast.makeText(context, info, durtion);
        mToast.show();
    }

    public static void showToast(final Activity activity, final String word, final long time) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                final Toast toast = Toast.makeText(activity, word, Toast.LENGTH_LONG);
                toast.show();
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        toast.cancel();
                    }
                }, time);
            }
        });
    }


    /**
     * 之前显示的内容
     */
    private static String oldMsg;
    /**
     * Toast对象
     */
    private static Toast toast = null;
    /**
     * 第一次时间
     */
    private static long oneTime = 0;
    /**
     * 第二次时间
     */
    private static long twoTime = 0;

    /**
     * 显示Toast
     *
     * @param context
     * @param message
     */
    public static void showToast(Context context, String message) {
        if (toast == null) {
            toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
            toast.show();
            oneTime = System.currentTimeMillis();
        } else {
            twoTime = System.currentTimeMillis();
            if (message.equals(oldMsg)) {
                if (twoTime - oneTime > Toast.LENGTH_SHORT) {
                    toast.show();
                }
            } else {
                oldMsg = message;
                toast.setText(message);
                toast.show();
            }
        }
        oneTime = twoTime;
    }
}
