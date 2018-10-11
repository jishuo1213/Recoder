package com.inspur.icity.recoder.model;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * Created by fan on 17-3-22.
 */

public class RecordInfoBean {

    public String recordFilePath;
    public int duration;
    public String fileName;


    public RecordInfoBean() {
    }


    public boolean isRecordFileExits() {
        File file = new File(recordFilePath);
        return file.exists();
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("duration", duration);
            json.put("path", recordFilePath);
            json.put("name", fileName);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }
}
