package com.bxyun.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.bxyun.myapplication.info.MusicInfo;
import com.bxyun.myapplication.service.MediaService;
import com.bxyun.myapplication.service.MusicPlayer;
import com.bxyun.myapplication.utils.IConstants;
import com.bxyun.myapplication.utils.MusicUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

import static com.bxyun.myapplication.service.MusicPlayer.*;

public class MainActivity extends AppCompatActivity implements ServiceConnection {
    private   String  url = "http://win.web.ra01.sycdn.kuwo.cn/f05a603ca612771d9c48b9cd8fb4f3ef/5cfdb556/resource/n2/2009/07/24/2707183870.mp3";

    private ServiceToken mToken;
    private PlaybackStatus mPlaybackStatus; //receiver 接受播放状态变化等
//    private QuickControlsFragment fragment; //底部播放控制栏
    private String TAG = "BaseActivity";
    private ArrayList<MusicStateListener> mMusicListener = new ArrayList<>();

    /**
     * 更新播放队列
     */
    public void updateQueue() {

    }

    /**
     * 更新歌曲状态信息
     */
    public void updateTrackInfo() {
        for (final MusicStateListener listener : mMusicListener) {
            if (listener != null) {
                listener.reloadAdapter();
                listener.updateTrackInfo();
            }
        }
    }

    /**
     *  fragment界面刷新
     */
    public void refreshUI() {
        for (final MusicStateListener listener : mMusicListener) {
            if (listener != null) {
                listener.reloadAdapter();
            }
        }
    }

    public void updateTime() {
        for (final MusicStateListener listener : mMusicListener) {
            if (listener != null) {
                listener.updateTime();
            }
        }
    }

    /**
     *  歌曲切换
     */
    public void updateTrack() {

    }



    public void updateLrc() {

    }

    /**
     * @param p 更新歌曲缓冲进度值，p取值从0~100
     */
    public void updateBuffer(int p) {

    }

    public void changeTheme() {
        for (final MusicStateListener listener : mMusicListener) {
            if (listener != null) {
                listener.changeTheme();
            }
        }
    }

    /**
     * @param l 歌曲是否加载中
     */
    public void loading(boolean l){

    }


    /**
     * @param outState 取消保存状态
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //super.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    /**
     * @param savedInstanceState 取消保存状态
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        //super.onRestoreInstanceState(savedInstanceState);
    }


    /**
//     * @param  显示或关闭底部播放控制栏
//     */
//    protected void showQuickControl(boolean show) {
//        Log.d(TAG, MusicPlayer.getQueue().length + "");
//        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
//        if (show) {
//            if (fragment == null) {
//                fragment = QuickControlsFragment.newInstance();
//                ft.add(R.id.bottom_container, fragment).commitAllowingStateLoss();
//            } else {
//                ft.show(fragment).commitAllowingStateLoss();
//            }
//        } else {
//            if (fragment != null)
//                ft.hide(fragment).commitAllowingStateLoss();
//        }
//    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mToken = bindToService(this, this);
        mPlaybackStatus = new PlaybackStatus(this);
        setContentView(R.layout.activity_main);
        IntentFilter f = new IntentFilter();
        f.addAction(MediaService.PLAYSTATE_CHANGED);
        f.addAction(MediaService.META_CHANGED);
        f.addAction(MediaService.QUEUE_CHANGED);
        f.addAction(IConstants.MUSIC_COUNT_CHANGED);
        f.addAction(MediaService.TRACK_PREPARED);
        f.addAction(MediaService.BUFFER_UP);
        f.addAction(IConstants.EMPTY_LIST);
        f.addAction(MediaService.MUSIC_CHANGED);
        f.addAction(MediaService.LRC_UPDATED);
        f.addAction(IConstants.PLAYLIST_COUNT_CHANGED);
        f.addAction(MediaService.MUSIC_LODING);
        registerReceiver(mPlaybackStatus, new IntentFilter(f));
//        showQuickControl(true);
    }


    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        mService = MediaAidlInterface.Stub.asInterface(service);
    }

    @Override
    public void onServiceDisconnected(final ComponentName name) {
        mService = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        unbindService();
        try {
            unregisterReceiver(mPlaybackStatus);
        } catch (final Throwable e) {
        }
        mMusicListener.clear();

    }

    public void unbindService() {
        if (mToken != null) {
            unbindFromService(mToken);
            mToken = null;
        }
    }

    public void setMusicStateListenerListener(final MusicStateListener status) {
        if (status == this) {
            throw new UnsupportedOperationException("Override the method, don't add a listener");
        }

        if (status != null) {
            mMusicListener.add(status);
        }
    }

    public void removeMusicStateListenerListener(final MusicStateListener status) {
        if (status != null) {
            mMusicListener.remove(status);
        }
    }


    private final static class PlaybackStatus extends BroadcastReceiver {

        private final WeakReference<MainActivity> mReference;


        public PlaybackStatus(final MainActivity activity) {
            mReference = new WeakReference<>(activity);
        }


        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            MainActivity baseActivity = mReference.get();
            if (baseActivity != null) {
                if (action.equals(MediaService.META_CHANGED)) {
                    baseActivity.updateTrackInfo();

                } else if (action.equals(MediaService.PLAYSTATE_CHANGED)) {

                } else if (action.equals(MediaService.TRACK_PREPARED)) {
                    baseActivity.updateTime();
                } else if (action.equals(MediaService.BUFFER_UP)) {
                    baseActivity.updateBuffer(intent.getIntExtra("progress", 0));
                } else if (action.equals(MediaService.MUSIC_LODING)) {
                    baseActivity.loading(intent.getBooleanExtra("isloading",false));
                } else if (action.equals(MediaService.REFRESH)) {

                } else if (action.equals(IConstants.MUSIC_COUNT_CHANGED)) {
                    baseActivity.refreshUI();
                } else if (action.equals(IConstants.PLAYLIST_COUNT_CHANGED)) {
                    baseActivity.refreshUI();
                } else if (action.equals(MediaService.QUEUE_CHANGED)) {
                    baseActivity.updateQueue();
                } else if (action.equals(MediaService.TRACK_ERROR)) {
                    @SuppressLint({"StringFormatInvalid", "LocalSuppress"}) final String errorMsg = context.getString(R.string.exit,
                            intent.getStringExtra(MediaService.TrackErrorExtra.TRACK_NAME));
                    Toast.makeText(baseActivity, errorMsg, Toast.LENGTH_SHORT).show();
                } else if (action.equals(MediaService.MUSIC_CHANGED)) {
                    baseActivity.updateTrack();
                } else if (action.equals(MediaService.LRC_UPDATED)) {
                    baseActivity.updateLrc();
                }

            }
        }
    }


    private void   play(){
        ArrayList<MusicInfo>  mList= new ArrayList<>();
        for (int i = 0; i <5; i++) {

            MusicInfo   musicInfo = new MusicInfo();


        }


//        HandlerUtil.getInstance(RecentActivity.this).postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                long[] list = new long[mList.size()];
//                HashMap<Long, MusicInfo> infos = new HashMap();
//                for (int i = 0; i < mList.size(); i++) {
//          //获取本地的音乐
//                    MusicInfo info = MusicUtils.getMusicInfo(MainActivity.this, mList.get(i).id);
//                    list[i] = info.songId;
//                    info.islocal = true;
//                    info.albumData = MusicUtils.getAlbumArtUri(info.albumId) + "";
//                    infos.put(list[i], info);
//                }
//                MusicPlayer.playAll(infos, list, 0, false);
//            }
//        },70);
//        playAll(infos, list, 0, false);
    }


}