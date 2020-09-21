package com.bxyun.myapplication;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import androidx.appcompat.widget.ThemeUtils;

import com.bxyun.myapplication.permissions.Nammu;
import com.bxyun.myapplication.provider.PlaylistInfo;
import com.bxyun.myapplication.utils.IConstants;
import com.bxyun.myapplication.utils.PreferencesUtility;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.google.gson.Gson;

/**
 * author ：贾卫星
 * date: 2020/08/21 15:09
 * description:
 */

public class MainApplication extends Application {

    public static   Context context;
    private long favPlaylist = IConstants.FAV_PLAYLIST;
    @Override
    public void onCreate() {
        super.onCreate();

        frescoInit();
        super.onCreate();
//        if (LeakCanary.isInAnalyzerProcess(this)) {
//            // This process is dedicated to LeakCanary for heap analysis.
//            // You should not init your app in this process.
//            return;
//        }


        context = this;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Nammu.init(this);
        }


        if (!PreferencesUtility.getInstance(this).getFavriateMusicPlaylist()) {
            PlaylistInfo.getInstance(this).addPlaylist(favPlaylist, getResources().getString(R.string.my_fav_playlist),
                    0, "res:/" + R.mipmap.ic_launcher, "local");
            PreferencesUtility.getInstance(this).setFavriateMusicPlaylist(true);
        }
    }
    private void frescoInit() {
        Fresco.initialize(this);
    }
    private static Gson gson;

    public static Gson gsonInstance() {
        if (gson == null) {
            gson = new Gson();
        }
        return gson;
    }
}
