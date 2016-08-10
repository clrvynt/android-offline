package com.kk.noteservice.util;

import android.content.Context;

import com.raizlabs.android.dbflow.annotation.Database;
import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;

/**
 * Created by kal on 8/9/16.
 */
@Database(name = "NoteDB", version = 1)
public class NoteDB {
    public static void init(Context context) {
        FlowManager.init(new FlowConfig.Builder(context).build());
    }
}
