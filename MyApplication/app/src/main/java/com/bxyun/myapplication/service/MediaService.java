/*
 * Copyright (C) 2012 Andrew Neal
 * Copyright (C) 2014 The CyanogenMod Project
 * Copyright (C) 2015 Naman Dwivedi
 *
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.bxyun.myapplication.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;
import android.provider.MediaStore.Audio.AudioColumns;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.bxyun.myapplication.MainApplication;
import com.bxyun.myapplication.MediaAidlInterface;
import com.bxyun.myapplication.MusicFileDownInfo;
import com.bxyun.myapplication.R;
import com.bxyun.myapplication.downmusic.Down;
import com.bxyun.myapplication.info.MusicInfo;
import com.bxyun.myapplication.net.BMA;
import com.bxyun.myapplication.net.HttpUtil;
import com.bxyun.myapplication.permissions.Nammu;
import com.bxyun.myapplication.provider.MusicPlaybackState;
import com.bxyun.myapplication.provider.RecentStore;
import com.bxyun.myapplication.proxy.utils.MediaPlayerProxy;
import com.bxyun.myapplication.receiver.MediaButtonIntentReceiver;
import com.bxyun.myapplication.recent.SongPlayCount;
import com.bxyun.myapplication.utils.CommonUtils;
import com.bxyun.myapplication.utils.ImageUtils;
import com.bxyun.myapplication.utils.PreferencesUtility;
import com.facebook.common.executors.CallerThreadExecutor;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;


@SuppressLint("NewApi")
public class MediaService extends Service {
    // 发送广播的 活动标记
    public static final String PLAYSTATE_CHANGED = "com.wm.remusic.playstatechanged";
    public static final String POSITION_CHANGED = "com.wm.remusic.positionchanged";
    public static final String META_CHANGED = "com.wm.remusic.metachanged";
    public static final String PLAYLIST_ITEM_MOVED = "com.wm.remusic.mmoved";
    public static final String QUEUE_CHANGED = "com.wm.remusic.queuechanged";
    public static final String PLAYLIST_CHANGED = "com.wm.remusic.playlistchanged";
    public static final String REPEATMODE_CHANGED = "com.wm.remusic.repeatmodechanged";
    public static final String SHUFFLEMODE_CHANGED = "com.wm.remusic.shufflemodechanged";
    public static final String TRACK_ERROR = "com.wm.remusic.trackerror";
    public static final String TIMBER_PACKAGE_NAME = "com.wm.remusic";
    public static final String MUSIC_PACKAGE_NAME = "com.android.music";
    public static final String SERVICECMD = "com.wm.remusic.musicservicecommand";
    public static final String TOGGLEPAUSE_ACTION = "com.wm.remusic.togglepause";
    public static final String PAUSE_ACTION = "com.wm.remusic.pause";
    public static final String STOP_ACTION = "com.wm.remusic.stop";
    public static final String PREVIOUS_ACTION = "com.wm.remusic.previous";
    public static final String PREVIOUS_FORCE_ACTION = "com.wm.remusic.previous.force";
    public static final String NEXT_ACTION = "com.wm.remusic.next";
    public static final String MUSIC_CHANGED = "com.wm.remusi.change_music";
    public static final String REPEAT_ACTION = "com.wm.remusic.repeat";
    public static final String SHUFFLE_ACTION = "com.wm.remusic.shuffle";
    public static final String FROM_MEDIA_BUTTON = "frommediabutton";
    public static final String REFRESH = "com.wm.remusic.refresh";
    public static final String LRC_UPDATED = "com.wm.remusic.updatelrc";
    public static final String UPDATE_LOCKSCREEN = "com.wm.remusic.updatelockscreen";
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDSTOP = "stop";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPLAY = "play";
    public static final String CMDPREVIOUS = "previous";
    public static final String CMDNEXT = "next";
    public static final String CMDNOTIF = "buttonId";
    //  曲目准备中
    public static final String TRACK_PREPARED = "com.wm.remusic.prepared";
    //  获取曲目信息
    public static final String TRY_GET_TRACKINFO = "com.wm.remusic.gettrackinfo";
    //  缓冲
    public static final String BUFFER_UP = "com.wm.remusic.bufferup";
    // 锁屏
    public static final String LOCK_SCREEN = "com.wm.remusic.lock";
    //  更新进度条
    public static final String SEND_PROGRESS = "com.wm.remusic.progress";
    // 音乐加载中
    public static final String MUSIC_LODING = "com.wm.remusic.loading";
    //  关闭 音乐播放器
    private static final String SHUTDOWN = "com.wm.remusic.shutdown";
    public static final String SETQUEUE = "com.wm.remusic.setqueue";
    //  下一首
    public static final int NEXT = 2;
    // 上一首
    public static final int LAST = 3;
    //   关闭循环模式，进行单曲播放模式
    public static final int SHUFFLE_NONE = 0;
    //    随机播放
    public static final int SHUFFLE_NORMAL = 1;
    //    SHUFFLE_AUTO则自动根据播放列表长度,从设备中自动选取曲目,自动补充到尾部
    public static final int SHUFFLE_AUTO = 2;
    //重复播放已关闭<
    public static final int REPEAT_NONE = 2;
    //重复播放当前歌曲（单曲播放）
    public static final int REPEAT_CURRENT = 1;
    // 重复播放所有歌曲  (列表播放）
    public static final int REPEAT_ALL = 2;
    // 设置存储的最大历史记录的条数
    public static final int MAX_HISTORY_SIZE = 1000;
    // 打印日志标记
    private static final String TAG = "MusicPlaybackService";
    //  是否是调试模式
    private static final boolean D = true;
    //  下载歌词
    private static final int LRC_DOWNLOADED = -10;
    private static final int IDCOLIDX = 0;
    private static final int TRACK_ENDED = 1;
    private static final int TRACK_WENT_TO_NEXT = 2;

    // 释放cpu，不让cpu一直处于唤醒的状态
    private static final int RELEASE_WAKELOCK = 3;
    //
    private static final int SERVER_DIED = 4;
    // 焦点发生变化
    private static final int FOCUSCHANGE = 5;

    // 音量 一点一点的减小
    private static final int FADEDOWN = 6;

    // 音量 一点一点的增加
    private static final int FADEUP = 7;

    //   设置多长时间后关闭播放器
    private static final int IDLE_DELAY = 5 * 60 * 1000;
    private static final long REWIND_INSTEAD_PREVIOUS_THRESHOLD = 3000;
    //查询数据库中的列中的信息
    private static final String[] PROJECTION = new String[]{
            "audio._id AS _id", MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID
    };
    private static final String[] ALBUM_PROJECTION = new String[]{
            MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.LAST_YEAR
    };
    private static final Shuffler mShuffler = new Shuffler();
    private static final int NOTIFY_MODE_NONE = 0;
    private static final int NOTIFY_MODE_FOREGROUND = 1;
    private static final int NOTIFY_MODE_BACKGROUND = 2;
    private static final String[] PROJECTION_MATRIX = new String[]{
            "_id", MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID
    };
    // 存储的是播放在列表中的位置
    private static LinkedList<Integer> mHistory = new LinkedList<>();
    private final IBinder mBinder = new ServiceStub(this);
    private MultiPlayer mPlayer;
    private String mFileToPlay;
    //  保持 cpu一直处于运行的状态
    public WakeLock mWakeLock;
    private AlarmManager mAlarmManager;
    private PendingIntent mShutdownIntent;
    private boolean mShutdownScheduled;
    private NotificationManager mNotificationManager;
    private Cursor mCursor;
    private Cursor mAlbumCursor;
    private AudioManager mAudioManager;
    private SharedPreferences mPreferences;
    private boolean mServiceInUse = false;
    //  是否正在播放，true正在播放，false   停止播放
    private boolean mIsSupposedToBePlaying = false;
    private long mLastPlayedTime;
    private int mNotifyMode = NOTIFY_MODE_NONE;
    private long mNotificationPostTime = 0;
    private boolean mQueueIsSaveable = true;
    //这说明你临时失去了音频焦点，但是在不久就会再返回来。此时，
    // 你必须终止所有的音频播放，但是保留你的播放资源，因为可能不久就会返回来。
    private boolean mPausedByTransientLossOfFocus = false;
//    专门用来解决媒体播放时界面和Service通讯的问题，
//    意在规范上述这些功能的流程。使用这个框架我们可以减少一些流程复杂的开发工作，
//    例如使用各种广播来控制播放器，而且其代码可读性、结构耦合度方面都控制得非常好
    private MediaSession mSession;
    // 组件名称
    private ComponentName mMediaButtonReceiverComponent;

    private int mCardId;

    private int mPlayPos = -1;

    private int mNextPlayPos = -1;
    // 打开失败的计数器
    private int mOpenFailedCounter = 0;

    private int mMediaMountedCount = 0;

    private int mShuffleMode = SHUFFLE_NONE;

    public int mRepeatMode = REPEAT_ALL;

    private int mServiceStartId = -1;

    private ArrayList<MusicTrack> mPlaylist = new ArrayList<MusicTrack>(100);

    private HashMap<Long, MusicInfo> mPlaylistInfo = new HashMap<>();

    private long[] mAutoShuffleList = null;

    private MusicPlayerHandler mPlayerHandler;

    private HandlerThread mHandlerThread;
    private BroadcastReceiver mUnmountReceiver = null;
    private MusicPlaybackState mPlaybackStateStore;
    private boolean mShowAlbumArtOnLockscreen;
    private SongPlayCount mSongPlayCount;
    private RecentStore mRecentStore;
    private int mNotificationId = 1000;

    private ContentObserver mMediaStoreObserver;
    private static Handler mUrlHandler;
    private static Handler mLrcHandler;
    private MediaPlayerProxy mProxy;
    public static final String LRC_PATH = "/remusic/lrc/";
    public long mLastSeekPos = 0;
    private RequestPlayUrl mRequestUrl;
    private boolean mIsSending = false;
    private boolean mIsLocked;
    private Bitmap mNoBit;
    private Notification mNotification;


    private Thread mGetUrlThread = new Thread(new Runnable() {
        @Override
        public void run() {
            Looper.prepare();
            mUrlHandler = new Handler();
            Looper.loop();
        }
    });
    // 音频焦点发生变化
    private final OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {

        @Override
        public void onAudioFocusChange(final int focusChange) {
            mPlayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();
        }
    };

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String command = intent.getStringExtra(CMDNAME);

            Log.d(TAG, "onreceive" + intent.toURI());
            handleCommandIntent(intent);

        }
    };

    @Override
    public IBinder onBind(final Intent intent) {
        if (D) Log.d(TAG, "Service bound, intent = " + intent);
        cancelShutdown();
        mServiceInUse = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        if (D) Log.d(TAG, "Service unbound");
        mServiceInUse = false;
        saveQueue(true);
        if (mIsSupposedToBePlaying || mPausedByTransientLossOfFocus) {

            return true;

        } else if (mPlaylist.size() > 0 || mPlayerHandler.hasMessages(TRACK_ENDED)) {
            scheduleDelayedShutdown();
            return true;
        }
        stopSelf(mServiceStartId);

        return true;
    }

    @Override
    public void onRebind(final Intent intent) {
        cancelShutdown();
        mServiceInUse = true;
    }

    @Override
    public void onCreate() {
        if (D) Log.d(TAG, "Creating service");
        super.onCreate();
        mGetUrlThread.start();
        // 播放器代理,就是一个网络访问代理
        mProxy = new MediaPlayerProxy(this);
        mProxy.init();
        mProxy.start();
        //  通知栏管理工具类
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // gets a pointer to the playback state store
        // 处理保存状态的工具类
        mPlaybackStateStore = MusicPlaybackState.getInstance(this);
        mSongPlayCount = SongPlayCount.getInstance(this);
        mRecentStore = RecentStore.getInstance(this);

        mHandlerThread = new HandlerThread("MusicPlayerHandler",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        // 播放状态处理，点击不同操作按钮进行不同的操作
        mPlayerHandler = new MusicPlayerHandler(this, mHandlerThread.getLooper());
        //  音频播放
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMediaButtonReceiverComponent = new ComponentName(getPackageName(),
                MediaButtonIntentReceiver.class.getName());
        mAudioManager.registerMediaButtonEventReceiver(mMediaButtonReceiverComponent);
        // 使用 MediaSession 框架控制播放和停止
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setUpMediaSession();
        }

        mPreferences = getSharedPreferences("Service", 0);
        //  获取内存卡的ID 每个内存卡的ID都是独一无二的
        mCardId = getCardId();
        // 注册内存卡的安装和卸载的监听
        registerExternalStorageListener();
        //  播放器
        mPlayer = new MultiPlayer(this);
        mPlayer.setHandler(mPlayerHandler);

        // Initialize the intent filter and each action
        final IntentFilter filter = new IntentFilter();
        filter.addAction(SERVICECMD);
        filter.addAction(TOGGLEPAUSE_ACTION);
        filter.addAction(PAUSE_ACTION);
        filter.addAction(STOP_ACTION);
        filter.addAction(NEXT_ACTION);
        filter.addAction(PREVIOUS_ACTION);
        filter.addAction(PREVIOUS_FORCE_ACTION);
        filter.addAction(REPEAT_ACTION);
        filter.addAction(SHUFFLE_ACTION);
        filter.addAction(TRY_GET_TRACKINFO);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(LOCK_SCREEN);
        filter.addAction(SEND_PROGRESS);
        filter.addAction(SETQUEUE);
        // Attach the broadcast listener
        registerReceiver(mIntentReceiver, filter);

        mMediaStoreObserver = new MediaStoreObserver(mPlayerHandler);
        getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI, true, mMediaStoreObserver);
        getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mMediaStoreObserver);

        // Initialize the wake lock
        //电源管理
        final PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        mWakeLock.setReferenceCounted(false);

        //
        final Intent shutdownIntent = new Intent(this, MediaService.class);
        shutdownIntent.setAction(SHUTDOWN);
        // 定时器管理
        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mShutdownIntent = PendingIntent.getService(this, 0, shutdownIntent, 0);
        // 演示关闭
        scheduleDelayedShutdown();

        reloadQueueAfterPermissionCheck();
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);
    }
    //  Android  5.0 使用 MediaSession
    //  实现线控和按键处理
    private void setUpMediaSession() {
        mSession = new MediaSession(this, "remusic");
        mSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPause() {
                pause();
                mPausedByTransientLossOfFocus = false;
            }

            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onSeekTo(long pos) {
                seek(pos);
            }

            @Override
            public void onSkipToNext() {
                gotoNext(true);
            }

            @Override
            public void onSkipToPrevious() {
                prev(false);
            }

            @Override
            public void onStop() {
                pause();
                mPausedByTransientLossOfFocus = false;
                seek(0);
                releaseServiceUiAndStop();
            }
        });
        //FLAG_HANDLES_TRANSPORT_CONTROLS  ：控制传输命令
        mSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    public void exit() {
    }

    // 服务销毁的时候出现需要处理的步骤
    @Override
    public void onDestroy() {
        if (D) Log.d(TAG, "Destroying service");
        super.onDestroy();
        // Remove any sound effects
        //  音频效果     关闭所有的音频效果

        /**
         * 当播放内容的类型是游戏音频时EXTRA_CONTENT_TYPE的值。

         String	ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION	关闭音频效果
         String	ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL	启动一个音频效果控制面板UI。
         String	ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION	打开音频效果。
         int	ALREADY_EXISTS	内部操作状态。

         当播放内容的类型是游戏音频时EXTRA_CONTENT_TYPE的值。
         int	CONTENT_TYPE_MOVIE	当播放内容的类型是电影时EXTRA_CONTENT_TYPE的值。
         int	CONTENT_TYPE_MUSIC	当播放内容的类型是音乐时EXTRA_CONTENT_TYPE的值。
         int	CONTENT_TYPE_VOICE	当播放内容的类型是话音时EXTRA_CONTENT_TYPE的值

         String	EXTRA_AUDIO_SESSION	包含使用效果的音频会话ID。
         String	EXTRA_CONTENT_TYPE	指示应用程序播放内容的类型。
         String	EXTRA_PACKAGE_NAME	包含调用应用程序的包名。
         int	SUCCESS	操作成功。

         */
        final Intent audioEffectsIntent = new Intent(
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(audioEffectsIntent);

        cancelNotification();

        mAlarmManager.cancel(mShutdownIntent);

        mPlayerHandler.removeCallbacksAndMessages(null);

        if (CommonUtils.isJellyBeanMR2())
            mHandlerThread.quitSafely();
        else mHandlerThread.quit();

        mPlayer.release();
        mPlayer = null;

        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mSession.release();

        getContentResolver().unregisterContentObserver(mMediaStoreObserver);

        closeCursor();

        unregisterReceiver(mIntentReceiver);
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
            mUnmountReceiver = null;
        }

        mWakeLock.release();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (D) Log.d(TAG, "Got new intent " + intent + ", startId = " + startId);
        mServiceStartId = startId;
        if (intent != null) {
            final String action = intent.getAction();

            if (SHUTDOWN.equals(action)) {
                mShutdownScheduled = false;
                releaseServiceUiAndStop();
                return START_NOT_STICKY;
            }
            handleCommandIntent(intent);
        }

        scheduleDelayedShutdown();

        if (intent != null && intent.getBooleanExtra(FROM_MEDIA_BUTTON, false)) {
            MediaButtonIntentReceiver.completeWakefulIntent(intent);
        }
        return START_STICKY;
    }

    //  不再进行视频播放 进行的操作
    private void releaseServiceUiAndStop() {
        if (isPlaying()
                || mPausedByTransientLossOfFocus
                || mPlayerHandler.hasMessages(TRACK_ENDED)) {
            return;
        }

        if (D) Log.d(TAG, "Nothing is playing anymore, releasing notification");
        //  释放通知
        cancelNotification();
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mSession.setActive(false);

        if (!mServiceInUse) {
            saveQueue(true);
            stopSelf(mServiceStartId);
        }
    }

    //  接收到广播发送过来的信息，主要控制 暂停  播放  下一首  上一首  等  UI  布局中的命令
    private void handleCommandIntent(Intent intent) {
        final String action = intent.getAction();
        final String command = SERVICECMD.equals(action) ? intent.getStringExtra(CMDNAME) : null;

        if (D) Log.d(TAG, "handleCommandIntent: action = " + action + ", command = " + command);

        if (CMDNEXT.equals(command) || NEXT_ACTION.equals(action)) {
            gotoNext(true);
        } else if (CMDPREVIOUS.equals(command) || PREVIOUS_ACTION.equals(action)
                || PREVIOUS_FORCE_ACTION.equals(action)) {
            prev(PREVIOUS_FORCE_ACTION.equals(action));
        } else if (CMDTOGGLEPAUSE.equals(command) || TOGGLEPAUSE_ACTION.equals(action)) {
            if (isPlaying()) {
                pause();
                mPausedByTransientLossOfFocus = false;
            } else {
                play();
            }
        } else if (CMDPAUSE.equals(command) || PAUSE_ACTION.equals(action)) {
            pause();
            mPausedByTransientLossOfFocus = false;
        } else if (CMDPLAY.equals(command)) {
            play();
        } else if (CMDSTOP.equals(command) || STOP_ACTION.equals(action)) {
            pause();
            mPausedByTransientLossOfFocus = false;
            seek(0);
            releaseServiceUiAndStop();
        } else if (REPEAT_ACTION.equals(action)) {
            cycleRepeat();
        } else if (SHUFFLE_ACTION.equals(action)) {
            cycleShuffle();
        } else if (TRY_GET_TRACKINFO.equals(action)) {
//            getLrc(mPlaylist.get(mPlayPos).mId);
        } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            // TODO: 2020/8/21
            if (isPlaying() && !mIsLocked) {
//                Intent lockscreen = new Intent(this, LockActivity.class);
//                lockscreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                startActivity(lockscreen);
            }
        } else if (LOCK_SCREEN.equals(action)) {
            mIsLocked = intent.getBooleanExtra("islock", true);
//            L.D(D,TAG,"isloced = " + mIsLocked);
        } else if (SEND_PROGRESS.equals(action)) {
            if (isPlaying() && !mIsSending) {
                mPlayerHandler.post(sendDuration);
                mIsSending = true;
            } else if (!isPlaying()) {
                mPlayerHandler.removeCallbacks(sendDuration);
                mIsSending = false;
            }

        } else if (SETQUEUE.equals(action)) {
            Log.e("playab", "action");
            setQueuePosition(intent.getIntExtra("position", 0));
        }
    }

    //更新时间进度，控制进度条的变化
    private Runnable sendDuration = new Runnable() {
        @Override
        public void run() {
            notifyChange(SEND_PROGRESS);
            mPlayerHandler.postDelayed(sendDuration, 1000);
        }
    };

    //  更新通知栏
    private void updateNotification() {
        final int newNotifyMode;
        if (isPlaying()) { //  正在播放
            newNotifyMode = NOTIFY_MODE_FOREGROUND;
        } else if (recentlyPlayed()) { //
            newNotifyMode = NOTIFY_MODE_BACKGROUND;
        } else {
            newNotifyMode = NOTIFY_MODE_NONE;
        }

        // int mNotificationId = hashCode();
        //  更新通知，先关闭之前的通知再打开一个通知
        if (mNotifyMode != newNotifyMode) {
            if (mNotifyMode == NOTIFY_MODE_FOREGROUND) {
                if (CommonUtils.isLollipop())
                    stopForeground(newNotifyMode == NOTIFY_MODE_NONE);
                else
                    stopForeground(newNotifyMode == NOTIFY_MODE_NONE || newNotifyMode == NOTIFY_MODE_BACKGROUND);
            } else if (newNotifyMode == NOTIFY_MODE_NONE) {
                mNotificationManager.cancel(mNotificationId);
                mNotificationPostTime = 0;
            }
        }

        if (newNotifyMode == NOTIFY_MODE_FOREGROUND) {
            //开启前台服务这样系统就不会随意杀死这个服务，通知也不会消失
            startForeground(mNotificationId, getNotification());

        } else if (newNotifyMode == NOTIFY_MODE_BACKGROUND) {
            //  开启通知
            mNotificationManager.notify(mNotificationId, getNotification());
        }

        mNotifyMode = newNotifyMode;
    }

    // 关闭服务并且 取消通知  在onDestory()和关闭服务的时候调用
    private void cancelNotification() {
        // 结束前台服务，并且也移除其中的通知
        stopForeground(true);
        //mNotificationManager.cancel(hashCode());
        //  取消指定的通知
        mNotificationManager.cancel(mNotificationId);

        mNotificationPostTime = 0;
        mNotifyMode = NOTIFY_MODE_NONE;
    }

    //  获取SD标识 ，意思就是获取卡的唯一标识，这样可以从对应的内存卡中获取相应的内容
    private int getCardId() {
        // 判断版本号
        if (CommonUtils.isMarshmallow()) {
            // 检查是否有读取内存卡的权限
            if (Nammu.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                return getmCardId();
            } else return 0;
        } else {
            return getmCardId();
        }
    }

    //  content://media/external/fs_id 是标示外部存储设备的ID，当我们更换SD卡时，通过这个获取到的ID会改变。
    private int getmCardId() {
        final ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(Uri.parse("content://media/external/fs_id"), null, null,
                null, null);
        int mCardId = -1;
        if (cursor != null && cursor.moveToFirst()) {
            mCardId = cursor.getInt(0);
            cursor.close();
        }
        return mCardId;
    }

    //  内存卡卸载之后，需要的操作
    public void closeExternalStorageFiles(final String storagePath) {
        // 停止播放
        stop(true);
        // 通知队列发生变化
        notifyChange(QUEUE_CHANGED);
        //   通知消息发生变化
        notifyChange(META_CHANGED);
    }

    //  内存卡的安装和卸载监听（广播接收者）
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(final Context context, final Intent intent) {
                    final String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        saveQueue(true);
                        mQueueIsSaveable = false;
                        closeExternalStorageFiles(intent.getData().getPath());
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        mMediaMountedCount++;
                        mCardId = getCardId();
                        reloadQueueAfterPermissionCheck();
                        mQueueIsSaveable = true;
                        notifyChange(QUEUE_CHANGED);
                        notifyChange(META_CHANGED);
                    }
                }
            };
            final IntentFilter filter = new IntentFilter();
            //  已拔掉外部大容量储存设备发出的广播（比如SD卡，或移动硬盘）,
            //  不管有没有正确卸载都会发出此广播
            filter.addAction(Intent.ACTION_MEDIA_EJECT);
            // 插入SD卡并且已正确安装（识别）时发出的广播
            filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            filter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, filter);
        }
    }

    // 设置 IDLE_DELAY 后 关闭  音乐
    private void scheduleDelayedShutdown() {
        if (D) Log.v(TAG, "Scheduling shutdown in " + IDLE_DELAY + " ms");
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + IDLE_DELAY, mShutdownIntent);
        mShutdownScheduled = true;
    }

    //  取消 定时关闭
    private void cancelShutdown() {
        if (D) Log.d(TAG, "Cancelling delayed shutdown, scheduled = " + mShutdownScheduled);
        if (mShutdownScheduled) {
            mAlarmManager.cancel(mShutdownIntent);
            mShutdownScheduled = false;
        }
    }

    //  停止播放 ，让播放器处于空闲状态
    private void stop(final boolean goToIdle) {
        if (D) Log.d(TAG, "Stopping playback, goToIdle = " + goToIdle);
        if (mPlayer.isInitialized()) {
            mPlayer.stop();
        }
        mFileToPlay = null;
        closeCursor();
        if (goToIdle) {
            setIsSupposedToBePlaying(false, false);
        }
// else {
//            if (CommonUtils.isLollipop())
//                stopForeground(false);
//            else stopForeground(true);
//        }
    }

    private int removeTracksInternal(int first, int last) {
        synchronized (this) {
            if (last < first) {
                return 0;
            } else if (first < 0) {
                first = 0;
            } else if (last >= mPlaylist.size()) {
                last = mPlaylist.size() - 1;
            }

            boolean gotonext = false;
            if (first <= mPlayPos && mPlayPos <= last) {
                mPlayPos = first;
                gotonext = true;
            } else if (mPlayPos > last) {
                mPlayPos -= last - first + 1;
            }
            final int numToRemove = last - first + 1;

            if (first == 0 && last == mPlaylist.size() - 1) {
                mPlayPos = -1;
                mNextPlayPos = -1;
                mPlaylist.clear();
                mHistory.clear();
            } else {
                for (int i = 0; i < numToRemove; i++) {
                    mPlaylistInfo.remove(mPlaylist.get(first).mId);
                    mPlaylist.remove(first);

                }

                ListIterator<Integer> positionIterator = mHistory.listIterator();
                while (positionIterator.hasNext()) {
                    int pos = positionIterator.next();
                    if (pos >= first && pos <= last) {
                        positionIterator.remove();
                    } else if (pos > last) {
                        positionIterator.set(pos - numToRemove);
                    }
                }
            }
            if (gotonext) {
                if (mPlaylist.size() == 0) {
                    stop(true);
                    mPlayPos = -1;
                    closeCursor();
                } else {
                    if (mShuffleMode != SHUFFLE_NONE) {
                        mPlayPos = getNextPosition(true);
                    } else if (mPlayPos >= mPlaylist.size()) {
                        mPlayPos = 0;
                    }
                    final boolean wasPlaying = isPlaying();
                    stop(false);
                    openCurrentAndNext();
                    if (wasPlaying) {
                        play();
                    }
                }
                notifyChange(META_CHANGED);
            }
            return last - first + 1;
        }
    }

    private void addToPlayList(final long[] list, int position) {
        final int addlen = list.length;
        if (position < 0) {
            mPlaylist.clear();
            position = 0;
        }
        //   ensureCapacity   扩容使用的
        mPlaylist.ensureCapacity(mPlaylist.size() + addlen);
        if (position > mPlaylist.size()) {
            position = mPlaylist.size();
        }

        final ArrayList<MusicTrack> arrayList = new ArrayList<MusicTrack>(addlen);
        for (int i = 0; i < list.length; i++) {
            arrayList.add(new MusicTrack(list[i], i));
        }

        mPlaylist.addAll(position, arrayList);

        if (mPlaylist.size() == 0) {
            closeCursor();
            notifyChange(META_CHANGED);
        }
    }

    /**
     * 通过 trackId 模拟一个cursor
     * 创建 音乐 曲目的模拟cursor
     *
     * @param trackId
     */
    private void updateCursor(final long trackId) {
        MusicInfo info = mPlaylistInfo.get(trackId);
        if (mPlaylistInfo.get(trackId) != null) {
            MatrixCursor cursor = new MatrixCursor(PROJECTION);
            cursor.addRow(new Object[]{info.songId, info.artist, info.albumName, info.musicName
                    , info.data, info.albumData, info.albumId, info.artistId});
            cursor.moveToFirst();
            mCursor = cursor;
            cursor.close();
        }
    }

    private void updateCursor(final String selection, final String[] selectionArgs) {
        synchronized (this) {
            closeCursor();
            mCursor = openCursorAndGoToFirst(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    PROJECTION, selection, selectionArgs);
        }
    }

    private void updateCursor(final Uri uri) {
        synchronized (this) {
            closeCursor();
            Intent intent = new Intent(Intent.ACTION_CAMERA_BUTTON);
            mCursor = openCursorAndGoToFirst(uri, PROJECTION, null, null);
        }
    }

    private Cursor openCursorAndGoToFirst(Uri uri, String[] projection,
                                          String selection, String[] selectionArgs) {
        Cursor c = getContentResolver().query(uri, projection,
                selection, selectionArgs, null);
        if (c == null) {
            return null;
        }
        if (!c.moveToFirst()) {
            c.close();
            return null;
        }
        return c;
    }

    //  关闭数据库
    private synchronized void closeCursor() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
        if (mAlbumCursor != null) {
            mAlbumCursor.close();
            mAlbumCursor = null;
        }
    }

    //  获取播放地址
    class RequestPlayUrl implements Runnable {
        private long id;
        private boolean play;
        private boolean stop;

        public RequestPlayUrl(long id, boolean play) {
            this.id = id;
            this.play = play;
        }

        public void stop() {
            stop = true;
        }

        @Override
        public void run() {
            try {
                //  获取播放地址
                String url = PreferencesUtility.getInstance(MediaService.this).getPlayLink(id);
                if (url == null) {
                    MusicFileDownInfo song = Down.getUrl(MediaService.this, id + "");
                    if (song != null && song.getShow_link() != null) {
                        url = song.getShow_link();
                        // 存储在本地
                        PreferencesUtility.getInstance(MediaService.this).setPlayLink(id, url);
                    }
                }
                if (url != null) {
//                    L.E(D, TAG, "current url = " + url);
                } else {
                    gotoNext(true);
                }

                if (!stop) {
                    startProxy();
                    // String urlEn = HttpUtil.urlEncode(url);
                    String urlEn = url;
                    urlEn = mProxy.getProxyURL(urlEn);
                    mPlayer.setDataSource(urlEn);
                }

                if (play && !stop) {
                    play();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

//    class RequestLrc implements Runnable {
//
//        private MusicInfo musicInfo;
//        private boolean stop;
//
//        RequestLrc(MusicInfo info) {
//            this.musicInfo = info;
//        }
//
//        public void stop() {
//            stop = true;
//        }
//
//        @Override
//        public void run() {
////            L.E(D, TAG, "start to getlrc");
//            String url = null;
//            if (musicInfo != null && musicInfo.lrc != null) {
//                url = musicInfo.lrc;
//            }
//            try {
//                JsonObject jsonObject = HttpUtil.getResposeJsonObject(BMA.Search.searchLrcPic(musicInfo.musicName, musicInfo.artist));
//                JsonArray array = jsonObject.get("songinfo").getAsJsonArray();
//                int len = array.size();
//                url = null;
//                for (int i = 0; i < len; i++) {
//                    url = array.get(i).getAsJsonObject().get("lrclink").getAsString();
//                    if (url != null) {
////                        L.D(D,TAG,"lrclink = " + url);
//                        break;
//                    }
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            if (!stop) {
//                File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + LRC_PATH + musicInfo.songId);
//                String lrc = null;
//                try {
//                    lrc = HttpUtil.getResposeString(url);
//                    if (lrc != null && !lrc.isEmpty()) {
//                        if (!file.exists())
//                            file.createNewFile();
//                        writeToFile(file, lrc);
//                        mPlayerHandler.sendEmptyMessage(LRC_DOWNLOADED);
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//
//
//        }
//    }


    //    private void getLrc(long id) {
//        MusicInfo info = mPlaylistInfo.get(id);
//
//        if(info == null){
////            L.D(D,TAG,"get lrc err ,musicinfo is null");
//        }
//        String lrc = Environment.getExternalStorageDirectory().getAbsolutePath() + LRC_PATH;
//        File file = new File(lrc);
////        L.D(D,TAG, "file exists = " + file.exists());
//        if (!file.exists()) {
//            //不存在就建立此目录
//            boolean r = file.mkdirs();
////            L.D(D,TAG, "file created = " + r);
//
//        }
//        file = new File(lrc + id);
//        if (!file.exists()) {
//            if (mRequestLrc != null) {
//                mRequestLrc.stop();
//                mLrcHandler.removeCallbacks(mRequestLrc);
//            }
////            mRequestLrc = new RequestLrc(mPlaylistInfo.get(id));
////            mLrcHandler.postDelayed(mRequestLrc, 70);
//        }
//    }
    //  写入文件中
    private synchronized void writeToFile(File file, String lrc) {
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(lrc.getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开启请求代理
     */
    private void startProxy() {
        if (mProxy == null) {
            mProxy = new MediaPlayerProxy(this);
            mProxy.init();
            mProxy.start();
        }
    }

    /**
     *
     * @param play  是否播放
     */
    private void openCurrentAndNextPlay(boolean play) {
        openCurrentAndMaybeNext(play, true);
    }


    private void openCurrentAndNext() {
        openCurrentAndMaybeNext(false, true);
    }

    /**
     *
     * @param play
     * @param openNext
     */
    private void openCurrentAndMaybeNext(final boolean play, final boolean openNext) {
        synchronized (this) {
            if (D) Log.d(TAG, "open current");
            closeCursor();
            stop(false);
            boolean shutdown = false;

            if (mPlaylist.size() == 0 || mPlaylistInfo.size() == 0 && mPlayPos >= mPlaylist.size()) {
                clearPlayInfos();
                return;
            }
            // 在数据库中的唯一标识
            final long id = mPlaylist.get(mPlayPos).mId;
            updateCursor(id);
            if (mPlaylistInfo.get(id) == null) {
                return;
            }
            if (!mPlaylistInfo.get(id).islocal) {
                // 处理网络歌曲
                if (mRequestUrl != null) {
                    mRequestUrl.stop();
                    mUrlHandler.removeCallbacks(mRequestUrl);
                }
                mRequestUrl = new RequestPlayUrl(id, play);
                mUrlHandler.postDelayed(mRequestUrl, 70);

            } else {
                //  处理本地音乐的处理方式
                while (true) {
                    if (mCursor != null
                            && openFile(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/"
                            + mCursor.getLong(IDCOLIDX))) {
                        break;
                    }
                    closeCursor();
                    if (mOpenFailedCounter++ < 10 && mPlaylist.size() > 1) {
                        final int pos = getNextPosition(false);
                        if (pos < 0) {
                            shutdown = true;
                            break;
                        }
                        mPlayPos = pos;
                        stop(false);
                        mPlayPos = pos;
                        updateCursor(mPlaylist.get(mPlayPos).mId);
                    } else {
                        mOpenFailedCounter = 0;
                        Log.w(TAG, "Failed to open file for playback");
                        shutdown = true;
                        break;
                    }
                }
            }

            if (shutdown) {
                scheduleDelayedShutdown();
                if (mIsSupposedToBePlaying) {
                    mIsSupposedToBePlaying = false;
                    notifyChange(PLAYSTATE_CHANGED);
                }
            } else if (openNext) {
                setNextTrack();
            }
        }
    }

    //  发送错误的信息
    private void sendErrorMessage(final String trackName) {
        final Intent i = new Intent(TRACK_ERROR);
        i.putExtra(TrackErrorExtra.TRACK_NAME, trackName);
        sendBroadcast(i);
    }
    /**
     * @param force 是否正常播放完成，执行下一首，true  标识的不是正常播放完成，手动操作进入下一首
     *              <p>
     *              false  标识正常完成
     * @return -1  表示没有得到想要的下一首的位置
     */
    private int getNextPosition(final boolean force) {

        if (mPlaylist == null || mPlaylist.isEmpty()) {
            return -1;
        }
        // 表示正常完成 播放完成 并且循环模式是只循环当前的歌曲
        //
        // 只循环当前这一首
        if (!force && mRepeatMode == REPEAT_CURRENT) { //  判断循环模式
            if (mPlayPos < 0) {
                return 0;
            }
            return mPlayPos;
        } else if (mShuffleMode == SHUFFLE_NORMAL) { // 判断随机模式
            //
            //  获取播放列表的长度
            final int numTracks = mPlaylist.size();
            //  创建播放列表数组,曲目编号播放
            final int[] trackNumPlays = new int[numTracks];
            for (int i = 0; i < numTracks; i++) {
                trackNumPlays[i] = 0;
            }
            // 获取播放历史的长度
            final int numHistory = mHistory.size();
            // 历史记录 曲目编号播放 次数
            for (int i = 0; i < numHistory; i++) {
                final int idx = mHistory.get(i).intValue();
                if (idx >= 0 && idx < numTracks) {
                    trackNumPlays[idx]++;
                }
            }
            // 当前播放位置的曲目 播放此说 加1
            if (mPlayPos >= 0 && mPlayPos < numTracks) {
                trackNumPlays[mPlayPos]++;
            }
            //  65535
            int minNumPlays = Integer.MAX_VALUE;
            //最少播放次数  曲目的个数    ，假设有5首歌曲播放的都是一次， numTracksWithMinNumPlays 表示的就是这五首歌曲
            int numTracksWithMinNumPlays = 0;
            for (int i = 0; i < trackNumPlays.length; i++) {
                if (trackNumPlays[i] < minNumPlays) {
                    minNumPlays = trackNumPlays[i];
                    numTracksWithMinNumPlays = 1;
                } else if (trackNumPlays[i] == minNumPlays) {
                    numTracksWithMinNumPlays++;
                }
            }

            // 1、minNumPlays 最少播放 次数
            // 2、numTracksWithMinNumPlays == numTracks    最少播放次数曲目的个数 等于 总播放的个数
            // 3、 mRepeatMode != REPEAT_ALL   不是列表播放，而是随机播放或者单曲循环
            // 4、force  false   正常播放完成
            //  上面已经对 正常播放完成 （force 是  false ）循环模式是 REPEAT_CURRENT的 处理过了

            if (minNumPlays > 0 && numTracksWithMinNumPlays == numTracks
                    && mRepeatMode != REPEAT_ALL && !force) {
                return -1;
            }

            //随机生成一个数字
            int skip = mShuffler.nextInt(numTracksWithMinNumPlays);
            // 返回skip 等于 0 的 歌曲的位置，取出最后一个播放次数少的歌曲的位置，就是下次要播放的的位置
            //  取出最后一个播放此说少的 歌曲 所在的位置
            for (int i = 0; i < trackNumPlays.length; i++) {
                //  取出播放次数最少的歌曲
                if (trackNumPlays[i] == minNumPlays) {
                    if (skip == 0) {
                        return i;
                    } else {
                        skip--;
                    }
                }
            }

            if (D)
                //取得下一个位置结果并没有得到应有的结果
                Log.e(TAG, "Getting the next position resulted did not get a result when it should have");

            return -1;

        } else if (mShuffleMode == SHUFFLE_AUTO) {
            // 顺序循环 并自动更新 列表
            doAutoShuffleUpdate();
            return mPlayPos + 1;
        } else {
            if (mPlayPos >= mPlaylist.size() - 1) {
                if (mRepeatMode == REPEAT_NONE && !force) {
                    return -1;
                } else if (mRepeatMode == REPEAT_ALL || force) {
                    return 0;
                }
                return -1;
            } else {
                return mPlayPos + 1;
            }
        }
    }

    private void setNextTrack() {
        setNextTrack(getNextPosition(false));
    }


    private void setNextTrack(int position) {
        mNextPlayPos = position;
        if (D) Log.d(TAG, "setNextTrack: next play position = " + mNextPlayPos);
        if (mNextPlayPos >= 0 && mPlaylist != null && mNextPlayPos < mPlaylist.size()) {
            final long id = mPlaylist.get(mNextPlayPos).mId;
            if (mPlaylistInfo.get(id) != null) {
                if (mPlaylistInfo.get(id).islocal) {
                    mPlayer.setNextDataSource(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + id);
                } else {
                    mPlayer.setNextDataSource(null);
                }

            }
        } else {
            mPlayer.setNextDataSource(null);
        }
    }

    private boolean makeAutoShuffleList() {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{
                            MediaStore.Audio.Media._ID
                    }, MediaStore.Audio.Media.IS_MUSIC + "=1", null, null);
            if (cursor == null || cursor.getCount() == 0) {
                return false;
            }
            final int len = cursor.getCount();
            final long[] list = new long[len];
            for (int i = 0; i < len; i++) {
                cursor.moveToNext();
                list[i] = cursor.getLong(0);
            }
            mAutoShuffleList = list;
            return true;
        } catch (final RuntimeException e) {
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        return false;
    }

    //  自动更新随机的列表
    private void doAutoShuffleUpdate() {
        boolean notify = false;
        if (mPlayPos > 10) {
            removeTracks(0, mPlayPos - 9);
            notify = true;
        }
        final int toAdd = 7 - (mPlaylist.size() - (mPlayPos < 0 ? -1 : mPlayPos));
        for (int i = 0; i < toAdd; i++) {
            int lookback = mHistory.size();
            int idx = -1;
            while (true) {
                idx = mShuffler.nextInt(mAutoShuffleList.length);
                if (!wasRecentlyUsed(idx, lookback)) {
                    break;
                }
                lookback /= 2;
            }
            mHistory.add(idx);
            if (mHistory.size() > MAX_HISTORY_SIZE) {
                mHistory.remove(0);
            }
            mPlaylist.add(new MusicTrack(mAutoShuffleList[idx], -1));
            notify = true;
        }
        if (notify) {
            notifyChange(QUEUE_CHANGED);
        }
    }

    private boolean wasRecentlyUsed(final int idx, int lookbacksize) {
        if (lookbacksize == 0) {
            return false;
        }
        final int histsize = mHistory.size();
        if (histsize < lookbacksize) {
            lookbacksize = histsize;
        }
        final int maxidx = histsize - 1;
        for (int i = 0; i < lookbacksize; i++) {
            final long entry = mHistory.get(maxidx - i);
            if (entry == idx) {
                return true;
            }
        }
        return false;
    }

    // 更新缓存进度
    public void sendUpdateBuffer(int progress) {
        Intent intent = new Intent(BUFFER_UP);
        intent.putExtra("progress", progress);
        sendBroadcast(intent);
    }

    public void notifyChange(final String what) {
        if (D) Log.d(TAG, "notifyChange: what = " + what);
        if (SEND_PROGRESS.equals(what)) {
            final Intent intent = new Intent(what);
            intent.putExtra("position", position());
            intent.putExtra("duration", duration());
            sendStickyBroadcast(intent);
            return;
        }

        // Update the lockscreen controls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            updateMediaSession(what);

        if (what.equals(POSITION_CHANGED)) {
            return;
        }

        final Intent intent = new Intent(what);
        intent.putExtra("id", getAudioId());
        intent.putExtra("artist", getArtistName());
        intent.putExtra("album", getAlbumName());
        intent.putExtra("track", getTrackName());
        intent.putExtra("playing", isPlaying());
        intent.putExtra("albumuri", getAlbumPath());
        intent.putExtra("islocal", isTrackLocal());

        sendStickyBroadcast(intent);
        final Intent musicIntent = new Intent(intent);
        musicIntent.setAction(what.replace(TIMBER_PACKAGE_NAME, MUSIC_PACKAGE_NAME));
        sendStickyBroadcast(musicIntent);
//        if (what.equals(TRACK_PREPARED)) {
//            return;
//        }

        if (what.equals(META_CHANGED)) {

            mRecentStore.addSongId(getAudioId());
            mSongPlayCount.bumpSongCount(getAudioId());

        } else if (what.equals(QUEUE_CHANGED)) {
            Intent intent1 = new Intent("com.wm.remusic.emptyplaylist");
            intent.putExtra("showorhide", "show");
            sendBroadcast(intent1);
            saveQueue(true);
            if (isPlaying()) {

                if (mNextPlayPos >= 0 && mNextPlayPos < mPlaylist.size()
                        && getShuffleMode() != SHUFFLE_NONE) {
                    setNextTrack(mNextPlayPos);
                } else {
                    setNextTrack();
                }
            }
        } else {
            saveQueue(false);
        }

        if (what.equals(PLAYSTATE_CHANGED)) {
            updateNotification();
        }

    }

    private void updateMediaSession(final String what) {
        int playState = mIsSupposedToBePlaying
                ? PlaybackState.STATE_PLAYING
                : PlaybackState.STATE_PAUSED;

        if (what.equals(PLAYSTATE_CHANGED) || what.equals(POSITION_CHANGED)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mSession.setPlaybackState(new PlaybackState.Builder()
                        .setState(playState, position(), 1.0f)
                        .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE |
                                PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                        .build());
            }
        } else if (what.equals(META_CHANGED) || what.equals(QUEUE_CHANGED)) {
            //Bitmap albumArt = ImageLoader.getInstance().loadImageSync(CommonUtils.getAlbumArtUri(getAlbumId()).toString());
            Bitmap albumArt = null;
            if (albumArt != null) {
                Bitmap.Config config = albumArt.getConfig();
                if (config == null) {
                    config = Bitmap.Config.ARGB_8888;
                }
                albumArt = albumArt.copy(config, false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mSession.setMetadata(new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, getArtistName())
                        .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, getAlbumArtistName())
                        .putString(MediaMetadata.METADATA_KEY_ALBUM, getAlbumName())
                        .putString(MediaMetadata.METADATA_KEY_TITLE, getTrackName())
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, duration())
                        .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, getQueuePosition() + 1)
                        .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, getQueue().length)
                        .putString(MediaMetadata.METADATA_KEY_GENRE, getGenreName())
                        .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,
                                mShowAlbumArtOnLockscreen ? albumArt : null)
                        .build());

                mSession.setPlaybackState(new PlaybackState.Builder()
                        .setState(playState, position(), 1.0f)
                        .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE |
                                PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                        .build());
            }
        }
    }


    private Notification getNotification() {
        RemoteViews remoteViews;
        final int PAUSE_FLAG = 0x1;
        final int NEXT_FLAG = 0x2;
        final int STOP_FLAG = 0x3;
        final String albumName = getAlbumName();
        final String artistName = getArtistName();
        final boolean isPlaying = isPlaying();

        remoteViews = new RemoteViews(this.getPackageName(), R.layout.notification);
        String text = TextUtils.isEmpty(albumName) ? artistName : artistName + " - " + albumName;
        remoteViews.setTextViewText(R.id.title, getTrackName());
        remoteViews.setTextViewText(R.id.text, text);

        //此处action不能是一样的 如果一样的 接受的flag参数只是第一个设置的值
        Intent pauseIntent = new Intent(TOGGLEPAUSE_ACTION);
        pauseIntent.putExtra("FLAG", PAUSE_FLAG);
        PendingIntent pausePIntent = PendingIntent.getBroadcast(this, 0, pauseIntent, 0);
        remoteViews.setImageViewResource(R.id.iv_pause, isPlaying ? R.drawable.note_btn_pause : R.drawable.note_btn_play);
        remoteViews.setOnClickPendingIntent(R.id.iv_pause, pausePIntent);

        Intent nextIntent = new Intent(NEXT_ACTION);
        nextIntent.putExtra("FLAG", NEXT_FLAG);
        PendingIntent nextPIntent = PendingIntent.getBroadcast(this, 0, nextIntent, 0);
        remoteViews.setOnClickPendingIntent(R.id.iv_next, nextPIntent);

        Intent preIntent = new Intent(STOP_ACTION);
        preIntent.putExtra("FLAG", STOP_FLAG);
        PendingIntent prePIntent = PendingIntent.getBroadcast(this, 0, preIntent, 0);
        remoteViews.setOnClickPendingIntent(R.id.iv_stop, prePIntent);

//        PendingIntent pendingIntent = PendingIntent.getActivity(this.getApplicationContext(), 0,
//                new Intent(this.getApplicationContext(), PlayingActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        final Intent nowPlayingIntent = new Intent();
        //nowPlayingIntent.setAction("com.wm.remusic.LAUNCH_NOW_PLAYING_ACTION");
        nowPlayingIntent.setComponent(new ComponentName("com.wm.remusic", "com.wm.remusic.activity.PlayingActivity"));
        nowPlayingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent clickIntent = PendingIntent.getBroadcast(this, 0, nowPlayingIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent click = PendingIntent.getActivity(this, 0, nowPlayingIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        final Bitmap bitmap = ImageUtils.getArtworkQuick(this, getAlbumId(), 160, 160);
        if (bitmap != null) {
            remoteViews.setImageViewBitmap(R.id.image, bitmap);
            // remoteViews.setImageViewUri(R.id.image, MusicUtils.getAlbumUri(this, getAudioId()));
            mNoBit = null;

        } else if (!isTrackLocal()) {
            if (mNoBit != null) {
                remoteViews.setImageViewBitmap(R.id.image, mNoBit);
                mNoBit = null;

            } else {
                Uri uri = null;
                if (getAlbumPath() != null) {
                    try {
                        uri = Uri.parse(getAlbumPath());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (getAlbumPath() == null || uri == null) {
                    mNoBit = BitmapFactory.decodeResource(getResources(), R.drawable.note_btn_play);
                    updateNotification();
                } else {
                    ImageRequest imageRequest = ImageRequestBuilder
                            .newBuilderWithSource(uri)
                            .setProgressiveRenderingEnabled(true)
                            .build();
                    ImagePipeline imagePipeline = Fresco.getImagePipeline();
                    DataSource<CloseableReference<CloseableImage>>
                            dataSource = imagePipeline.fetchDecodedImage(imageRequest, MediaService.this);

                    dataSource.subscribe(new BaseBitmapDataSubscriber() {

                                             @Override
                                             public void onNewResultImpl(@Nullable Bitmap bitmap) {
                                                 // You can use the bitmap in only limited ways
                                                 // No need to do any cleanup.
                                                 if (bitmap != null) {
                                                     mNoBit = bitmap;
                                                 }
                                                 updateNotification();
                                             }

                                             @Override
                                             public void onFailureImpl(DataSource dataSource) {
                                                 // No cleanup required here.
                                                 mNoBit = BitmapFactory.decodeResource(getResources(), R.drawable.note_btn_play);
                                                 updateNotification();
                                             }
                                         },
                            CallerThreadExecutor.getInstance());
                }
            }

        } else {
            remoteViews.setImageViewResource(R.id.image, R.drawable.note_btn_play);
        }


        if (mNotificationPostTime == 0) {
            mNotificationPostTime = System.currentTimeMillis();
        }
        if (mNotification == null) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this).setContent(remoteViews)
                    .setSmallIcon(R.drawable.note_btn_play)
                    .setContentIntent(click)
                    .setWhen(mNotificationPostTime);
            if (CommonUtils.isJellyBeanMR1()) {
                builder.setShowWhen(false);
            }
            mNotification = builder.build();
        } else {
            mNotification.contentView = remoteViews;
        }

        return mNotification;
    }


    private final PendingIntent retrievePlaybackAction(final String action) {
        final ComponentName serviceName = new ComponentName(this, MediaService.class);
        Intent intent = new Intent(action);
        intent.setComponent(serviceName);

        return PendingIntent.getService(this, 0, intent, 0);
    }

    /**
     * 保存队列信息，当前内存卡的ID，
     *
     * @param full 是否保存当前所有的列表
     */
    private void saveQueue(final boolean full) {
        if (!mQueueIsSaveable) {
            return;
        }

        final SharedPreferences.Editor editor = mPreferences.edit();
        if (full) {
            // 随机播放  保存到数据库
            mPlaybackStateStore.saveState(mPlaylist, mShuffleMode != SHUFFLE_NONE ? mHistory : null);
            //   列表的详细信息保存到 内存卡
            if (mPlaylistInfo.size() > 0) {
                String temp = MainApplication.gsonInstance().toJson(mPlaylistInfo);
                try {
                    File file = new File(getCacheDir().getAbsolutePath() + "playlist");
                    RandomAccessFile ra = new RandomAccessFile(file, "rws");
                    ra.write(temp.getBytes());
                    ra.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            editor.putInt("cardid", mCardId);

        }
        editor.putInt("curpos", mPlayPos);
        if (mPlayer.isInitialized()) {
            editor.putLong("seekpos", mPlayer.position());
        }
        editor.putInt("repeatmode", mRepeatMode);
        editor.putInt("shufflemode", mShuffleMode);
        editor.apply();
    }

    //  先检查权限 然后加载队列
    private void reloadQueueAfterPermissionCheck() {
        if (CommonUtils.isMarshmallow()) {
            if (Nammu.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                reloadQueue();
            }
        } else {
            reloadQueue();
        }
    }

    //  读取文本信息
    private String readTextFromSDcard(InputStream is) throws Exception {
        InputStreamReader reader = new InputStreamReader(is);
        BufferedReader bufferedReader = new BufferedReader(reader);
        StringBuffer buffer = new StringBuffer();
        String str;
        while ((str = bufferedReader.readLine()) != null) {
            buffer.append(str);
            buffer.append("\n");
        }
        return buffer.toString();
    }

    // 清楚播放信息
    private void clearPlayInfos() {
        File file = new File(getCacheDir().getAbsolutePath() + "playlist");
        if (file.exists()) {
            file.delete();
        }
        MusicPlaybackState.getInstance(this).clearQueue();
    }

    //  重新加载列表
    private void reloadQueue() {
        int id = mCardId;
        if (mPreferences.contains("cardid")) {
            id = mPreferences.getInt("cardid", ~mCardId);
        }
        if (id == mCardId) {
            //  从数据库查询列表
            mPlaylist = mPlaybackStateStore.getQueue();
            try {
                FileInputStream in = new FileInputStream(new File(getCacheDir().getAbsolutePath() + "playlist"));
                String c = readTextFromSDcard(in);
                HashMap<Long, MusicInfo> play = MainApplication.gsonInstance().fromJson(c, new TypeToken<HashMap<Long, MusicInfo>>() {
                }.getType());
                if (play != null && play.size() > 0) {
                    mPlaylistInfo = play;
//                    L.D(D,TAG, mPlaylistInfo.keySet().toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //  判断 列表和 存储的列表信息要一一对应
        if ((mPlaylist.size() == mPlaylistInfo.size()) && mPlaylist.size() > 0) {
            final int pos = mPreferences.getInt("curpos", 0);
            if (pos < 0 || pos >= mPlaylist.size()) {
                mPlaylist.clear();
                return;
            }
            mPlayPos = pos;
            //  获取条目的ID ，模拟出信息的cursor
            updateCursor(mPlaylist.get(mPlayPos).mId);
            if (mCursor == null) {
                SystemClock.sleep(3000);
                updateCursor(mPlaylist.get(mPlayPos).mId);
            }
            synchronized (this) {
                closeCursor();
                mOpenFailedCounter = 20;
                openCurrentAndNext();
            }

//            if (!mPlayer.isInitialized() && isTrackLocal()) {
//                mPlaylist.clear();
//                return;
//            }
            final long seekpos = mPreferences.getLong("seekpos", 0);
            mLastSeekPos = seekpos;
            seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);

            if (D) {
                Log.d(TAG, "restored queue, currently at position "
                        + position() + "/" + duration()
                        + " (requested " + seekpos + ")");
            }

            int repmode = mPreferences.getInt("repeatmode", REPEAT_ALL);
            if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
                repmode = REPEAT_NONE;
            }
            mRepeatMode = repmode;

            int shufmode = mPreferences.getInt("shufflemode", SHUFFLE_NONE);
            if (shufmode != SHUFFLE_AUTO && shufmode != SHUFFLE_NORMAL) {
                shufmode = SHUFFLE_NONE;
            }
            if (shufmode != SHUFFLE_NONE) {
                mHistory = mPlaybackStateStore.getHistory(mPlaylist.size());
            }
            if (shufmode == SHUFFLE_AUTO) {
                if (!makeAutoShuffleList()) {
                    shufmode = SHUFFLE_NONE;
                }
            }
            mShuffleMode = shufmode;
        } else {
            clearPlayInfos();
        }
        notifyChange(MUSIC_CHANGED);
    }
    //
    public boolean openFile(final String path) {
        if (D) Log.d(TAG, "openFile: path = " + path);
        synchronized (this) {
            if (path == null) {
                return false;
            }

            if (mCursor == null) {
                Uri uri = Uri.parse(path);
                boolean shouldAddToPlaylist = true;
                long id = -1;
                try {
                    id = Long.valueOf(uri.getLastPathSegment());
                } catch (NumberFormatException ex) {
                    // Ignore
                }

                if (id != -1 && path.startsWith(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString())) {
                    updateCursor(uri);

                } else if (id != -1 && path.startsWith(
                        MediaStore.Files.getContentUri("external").toString())) {
                    updateCursor(id);

                } else if (path.startsWith("content://downloads/")) {

                    String mpUri = getValueForDownloadedFile(this, uri, "mediaprovider_uri");
                    if (D) Log.i(TAG, "Downloaded file's MP uri : " + mpUri);
                    if (!TextUtils.isEmpty(mpUri)) {
                        if (openFile(mpUri)) {
                            notifyChange(META_CHANGED);
                            return true;
                        } else {
                            return false;
                        }
                    } else {
                        updateCursorForDownloadedFile(this, uri);
                        shouldAddToPlaylist = false;
                    }

                } else {
                    String where = MediaStore.Audio.Media.DATA + "=?";
                    String[] selectionArgs = new String[]{path};
                    updateCursor(where, selectionArgs);
                }
                try {
                    if (mCursor != null && shouldAddToPlaylist) {
                        mPlaylist.clear();
                        mPlaylist.add(new MusicTrack(
                                mCursor.getLong(IDCOLIDX), -1));
                        notifyChange(QUEUE_CHANGED);
                        mPlayPos = 0;
                        mHistory.clear();
                    }
                } catch (final UnsupportedOperationException ex) {
                    // Ignore
                }
            }

            mFileToPlay = path;
            mPlayer.setDataSource(mFileToPlay);
            if (mPlayer.isInitialized()) {
                mOpenFailedCounter = 0;
                return true;
            }

            String trackName = getTrackName();
            if (TextUtils.isEmpty(trackName)) {
                trackName = path;
            }
            sendErrorMessage(trackName);

            stop(true);
            return false;
        }
    }

    //  为下载文件构造一个cursor
    private void updateCursorForDownloadedFile(Context context, Uri uri) {
        synchronized (this) {
            closeCursor();
            //虚拟构造的cursor ，一般都是从查询数据库获取
            //
            MatrixCursor cursor = new MatrixCursor(PROJECTION_MATRIX);
            String title = getValueForDownloadedFile(this, uri, "title");
            cursor.addRow(new Object[]{
                    null,
                    null,
                    null,
                    title,
                    null,
                    null,
                    null,
                    null
            });
            mCursor = cursor;
            mCursor.moveToFirst();
        }
    }

    //  获取下载文件对应的一些值
    private String getValueForDownloadedFile(Context context, Uri uri, String column) {

        Cursor cursor = null;
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public int getAudioSessionId() {
        synchronized (this) {
            return mPlayer.getAudioSessionId();
        }
    }

    public int getMediaMountedCount() {
        return mMediaMountedCount;
    }

    public int getShuffleMode() {
        return mShuffleMode;
    }

    //
    public void setShuffleMode(final int shufflemode) {
        synchronized (this) {
            if (mShuffleMode == shufflemode && mPlaylist.size() > 0) {
                return;
            }
            mShuffleMode = shufflemode;
            if (mShuffleMode == SHUFFLE_AUTO) {
                if (makeAutoShuffleList()) {
                    mPlaylist.clear();
                    doAutoShuffleUpdate();
                    mPlayPos = 0;
                    openCurrentAndNext();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                } else {
                    mShuffleMode = SHUFFLE_NONE;
                }
            } else {
                setNextTrack();
            }
            saveQueue(false);
            notifyChange(SHUFFLEMODE_CHANGED);
        }
    }

    // 获取循环 模式
    public int getRepeatMode() {
        return mRepeatMode;
    }

    //  设置循环模式
    public void setRepeatMode(final int repeatmode) {
        synchronized (this) {
            mRepeatMode = repeatmode;
            setNextTrack();
            saveQueue(false);
            notifyChange(REPEATMODE_CHANGED);
        }
    }

    // 移除曲目
    public int removeTrack(final long id) {
        int numremoved = 0;
        synchronized (this) {
            for (int i = 0; i < mPlaylist.size(); i++) {
                if (mPlaylist.get(i).mId == id) {
                    numremoved += removeTracksInternal(i, i);
                    i--;
                }
            }

            mPlaylistInfo.remove(id);
        }

        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }

    // 移除指定位置的曲目
    public boolean removeTrackAtPosition(final long id, final int position) {
        synchronized (this) {
            if (position >= 0 &&
                    position < mPlaylist.size() &&
                    mPlaylist.get(position).mId == id) {
                mPlaylistInfo.remove(id);
                return removeTracks(position, position) > 0;
            }

        }
        return false;
    }

    public int removeTracks(final int first, final int last) {
        final int numremoved = removeTracksInternal(first, last);
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }

    public int getQueuePosition() {
        synchronized (this) {
            return mPlayPos;
        }
    }
    //
    //  设置播放的位置，一方面播放位置指定错误的时候设置播放位置
    public void setQueuePosition(final int index) {
        synchronized (this) {
            stop(false);
            mPlayPos = index;
            openCurrentAndNext();
            play();
            notifyChange(META_CHANGED);
            if (mShuffleMode == SHUFFLE_AUTO) {
                doAutoShuffleUpdate();
            }
        }
    }

    public int getQueueHistorySize() {
        synchronized (this) {
            return mHistory.size();
        }
    }

    public int getQueueHistoryPosition(int position) {
        synchronized (this) {
            if (position >= 0 && position < mHistory.size()) {
                return mHistory.get(position);
            }
        }

        return -1;
    }

    public int[] getQueueHistoryList() {
        synchronized (this) {
            int[] history = new int[mHistory.size()];
            for (int i = 0; i < mHistory.size(); i++) {
                history[i] = mHistory.get(i);
            }

            return history;
        }
    }

    public String getPath() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.DATA));
        }
    }

    //  获取音乐专辑的名称
    public String getAlbumName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.ALBUM));
        }
    }

    //  获取音乐专辑的路径
    public String getAlbumPath() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.MIME_TYPE));
        }
    }

    public String[] getAlbumPathAll() {
        synchronized (this) {
            try {
                int len = mPlaylistInfo.size();
                String[] albums = new String[len];
                long[] queue = getQueue();
                for (int i = 0; i < len; i++) {
                    albums[i] = mPlaylistInfo.get(queue[i]).albumData;
                }
                return albums;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return new String[]{};
        }
    }

    // 获取曲目的名称
    public String getTrackName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.TITLE));
        }
    }

    public boolean isTrackLocal() {
        synchronized (this) {
            MusicInfo info = mPlaylistInfo.get(getAudioId());
            if (info == null) {
                return true;
            }
            return info.islocal;
        }
    }

    public String getAlbumPath(long id) {
        synchronized (this) {
            try {
                String str = mPlaylistInfo.get(id).albumData;
                return str;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public String getGenreName() {
        synchronized (this) {
            if (mCursor == null || mPlayPos < 0 || mPlayPos >= mPlaylist.size()) {
                return null;
            }
            String[] genreProjection = {MediaStore.Audio.Genres.NAME};
            Uri genreUri = MediaStore.Audio.Genres.getContentUriForAudioId("external",
                    (int) mPlaylist.get(mPlayPos).mId);
            Cursor genreCursor = getContentResolver().query(genreUri, genreProjection,
                    null, null, null);
            if (genreCursor != null) {
                try {
                    if (genreCursor.moveToFirst()) {
                        return genreCursor.getString(
                                genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME));
                    }
                } finally {
                    genreCursor.close();
                }
            }
            return null;
        }
    }

    public String getArtistName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(AudioColumns.ARTIST));
        }
    }

    public String getAlbumArtistName() {
        synchronized (this) {
            if (mAlbumCursor == null) {
                return null;
            }
            return mAlbumCursor.getString(mAlbumCursor.getColumnIndexOrThrow(AlbumColumns.ARTIST));
        }
    }

    public long getAlbumId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(AudioColumns.ALBUM_ID));
        }
    }

    public long getArtistId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(AudioColumns.ARTIST_ID));
        }
    }


    public long getNextAudioId() {
        synchronized (this) {
            if (mNextPlayPos >= 0 && mNextPlayPos < mPlaylist.size() && mPlayer.isInitialized()) {
                return mPlaylist.get(mNextPlayPos).mId;
            }
        }
        return -1;
    }

    public long getPreviousAudioId() {
        synchronized (this) {
            if (mPlayer.isInitialized()) {
                int pos = getPreviousPlayPosition(false);
                if (pos >= 0 && pos < mPlaylist.size()) {
                    return mPlaylist.get(pos).mId;
                }
            }
        }
        return -1;
    }

    public long seek(long position) {
        if (mPlayer.isInitialized()) {
            if (position < 0) {
                position = 0;
            } else if (position > mPlayer.duration()) {
                position = mPlayer.duration();
            }
            long result = mPlayer.seek(position);
            notifyChange(POSITION_CHANGED);
            return result;
        }
        return -1;
    }

    public void seekRelative(long deltaInMs) {
        synchronized (this) {
            if (mPlayer.isInitialized()) {
                final long newPos = position() + deltaInMs;
                final long duration = duration();
                if (newPos < 0) {
                    prev(true);
                    // seek to the new duration + the leftover position
                    seek(duration() + newPos);
                } else if (newPos >= duration) {
                    gotoNext(true);
                    // seek to the leftover duration
                    seek(newPos - duration);
                } else {
                    seek(newPos);
                }
            }
        }
    }

    public long position() {
        if (mPlayer.isInitialized() && mPlayer.isTrackPrepared()) {
            try {
                return mPlayer.position();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    //  获取缓存的进度
    public int getSecondPosition() {
        if (mPlayer.isInitialized()) {
            return mPlayer.sencondaryPosition;
        }
        return -1;
    }

    //   获取时间
    public long duration() {
        if (mPlayer.isInitialized() && mPlayer.isTrackPrepared()) {
            return mPlayer.duration();
        }
        return -1;
    }

    // 获取播放信息
    public HashMap<Long, MusicInfo> getPlayinfos() {
        synchronized (this) {
            return mPlaylistInfo;
        }
    }

    // 对外开放的 获取播放列表，对外开放的
    public long[] getQueue() {
        synchronized (this) {
            final int len = mPlaylist.size();
            final long[] list = new long[len];
            for (int i = 0; i < len; i++) {
                list[i] = mPlaylist.get(i).mId;
            }
            return list;
        }
    }

    //  获取在列表中的位置
    public long getQueueItemAtPosition(int position) {
        synchronized (this) {
            if (position >= 0 && position < mPlaylist.size()) {
                return mPlaylist.get(position).mId;
            }
        }

        return -1;
    }

    // 获取列表的大小
    public int getQueueSize() {
        synchronized (this) {
            return mPlaylist.size();
        }
    }

    //   是否正在播放
    public boolean isPlaying() {
        return mIsSupposedToBePlaying;
    }

    //  设置是否继续播放，如果为false 就会停止继续播放
    private void setIsSupposedToBePlaying(boolean value, boolean notify) {
        if (mIsSupposedToBePlaying != value) {
            mIsSupposedToBePlaying = value;


            if (!mIsSupposedToBePlaying) {
                scheduleDelayedShutdown();
                mLastPlayedTime = System.currentTimeMillis();
            }

            if (notify) {
                notifyChange(PLAYSTATE_CHANGED);
            }
        }
    }

    // 最近是否已经播放
    private boolean recentlyPlayed() {
        return isPlaying() || System.currentTimeMillis() - mLastPlayedTime < IDLE_DELAY;
    }
    //  初始化   流程开始

    /**
     *
     * @param infos  存放歌曲信息的集合
     * @param list   存放歌曲信息的key
     * @param position  播放的位置，如果小于0 随机播放
     */
    public void open( final HashMap<Long, MusicInfo> infos, final long[] list, final int position) {
        synchronized (this) {
            mPlaylistInfo = infos;
//            L.D(D,TAG,mPlaylistInfo.toString());
            if (mShuffleMode == SHUFFLE_AUTO) {
                mShuffleMode = SHUFFLE_NORMAL;
            }
            // 获取上次保存的条目ID
            final long oldId = getAudioId();
            final int listlength = list.length;
            boolean newlist = true;
            // 已经
            if (mPlaylist.size() == listlength) {
                newlist = false;
                for (int i = 0; i < listlength; i++) {
                    if (list[i] != mPlaylist.get(i).mId) {
                        newlist = true;
                        break;
                    }
                }
            }
            //  如果是新的列表，就把新的列表添加到总的列表中
            if (newlist) {
                addToPlayList(list, -1);
                notifyChange(QUEUE_CHANGED);
            }
            if (position >= 0) {
                mPlayPos = position;
            } else {
                // 随机获取指定范围内的一个播放位置
                mPlayPos = mShuffler.nextInt(mPlaylist.size());
            }

            mHistory.clear();
            openCurrentAndNextPlay(true);
            if (oldId != getAudioId()) {
                notifyChange(META_CHANGED);
            }
        }
    }

    //  获取存储当前音乐信息的key，也就是歌曲在数据中的key
    public long getAudioId() {
        MusicTrack track = getCurrentTrack();
        if (track != null) {
            return track.mId;
        }
        return -1;
    }

    public MusicTrack getCurrentTrack() {
        return getTrack(mPlayPos);
    }

    //  或者指定位置的曲目
    public synchronized MusicTrack getTrack(int index) {
        if (index >= 0 && index < mPlaylist.size()) {
            return mPlaylist.get(index);
        }

        return null;
    }

    public void stop() {
        stop(true);
    }

    public void play() {
        play(true);
    }

    public void play(boolean createNewNextTrack) {
        int status = mAudioManager.requestAudioFocus(mAudioFocusListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        if (D) Log.d(TAG, "Starting playback: audio focus request status = " + status);

        if (status != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return;
        }

        final Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(intent);

        mAudioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(),
                MediaButtonIntentReceiver.class.getName()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mSession.setActive(true);
        if (createNewNextTrack) {
            setNextTrack();
        } else {
            setNextTrack(mNextPlayPos);
        }
        if (mPlayer.isTrackPrepared()) {
            final long duration = mPlayer.duration();
            if (mRepeatMode != REPEAT_CURRENT && duration > 2000
                    && mPlayer.position() >= duration - 2000) {
                gotoNext(true);
            }
        }
        mPlayer.start();
        mPlayerHandler.removeMessages(FADEDOWN);
        mPlayerHandler.sendEmptyMessage(FADEUP);
        setIsSupposedToBePlaying(true, true);
        cancelShutdown();
        updateNotification();
        notifyChange(META_CHANGED);
    }

    public void pause() {
        if (D) Log.d(TAG, "Pausing playback");
        synchronized (this) {
            mPlayerHandler.removeMessages(FADEUP);
            if (mIsSupposedToBePlaying) {
                final Intent intent = new Intent(
                        AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
                intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
                sendBroadcast(intent);

                mPlayer.pause();

                setIsSupposedToBePlaying(false, true);
                notifyChange(META_CHANGED);
            }
        }
    }

    public void gotoNext(final boolean force) {
        if (D) Log.d(TAG, "Going to next track");
        synchronized (this) {
            // 没有歌曲直接停止
            if (mPlaylist.size() <= 0) {
                if (D) Log.d(TAG, "No play queue");
                scheduleDelayedShutdown();
                return;
            }

            int pos = mNextPlayPos;
            if (pos < 0) {
                pos = getNextPosition(force);
            }

            if (pos < 0) {
                setIsSupposedToBePlaying(false, true);
                return;
            }

            stop(false);
            setAndRecordPlayPos(pos);
            openCurrentAndNext();
            play();
            notifyChange(META_CHANGED);
            notifyChange(MUSIC_CHANGED);
        }
    }

    //  设置和录制播放位置
    public void setAndRecordPlayPos(int nextPos) {
        synchronized (this) {

            if (mShuffleMode != SHUFFLE_NONE) {
                mHistory.add(mPlayPos);
                if (mHistory.size() > MAX_HISTORY_SIZE) {
                    mHistory.remove(0);
                }
            }

            mPlayPos = nextPos;
        }
    }

    //  强制上一首
    public void prev(boolean forcePrevious) {
        synchronized (this) {

            boolean goPrevious = getRepeatMode() != REPEAT_CURRENT &&
                    (position() < REWIND_INSTEAD_PREVIOUS_THRESHOLD || forcePrevious);

            if (goPrevious) {
                if (D) Log.d(TAG, "Going to previous track");
                int pos = getPreviousPlayPosition(true);

                if (pos < 0) {
                    return;
                }
                mNextPlayPos = mPlayPos;
                mPlayPos = pos;
                stop(false);
                openCurrent();
                play(false);
                notifyChange(META_CHANGED);
                notifyChange(MUSIC_CHANGED);
            } else {
                if (D) Log.d(TAG, "Going to beginning of track");
                seek(0);
                play(false);
            }
        }
    }

    public int getPreviousPlayPosition(boolean removeFromHistory) {
        synchronized (this) {
            if (mShuffleMode == SHUFFLE_NORMAL) {

                final int histsize = mHistory.size();
                if (histsize == 0) {
                    return -1;
                }
                final Integer pos = mHistory.get(histsize - 1);
                if (removeFromHistory) {
                    mHistory.remove(histsize - 1);
                }
                return pos.intValue();
            } else {
                if (mPlayPos > 0) {
                    return mPlayPos - 1;
                } else {
                    return mPlaylist.size() - 1;
                }
            }
        }
    }

    private void openCurrent() {
        openCurrentAndMaybeNext(false, false);
    }

    public void moveQueueItem(int index1, int index2) {
        synchronized (this) {
            if (index1 >= mPlaylist.size()) {
                index1 = mPlaylist.size() - 1;
            }
            if (index2 >= mPlaylist.size()) {
                index2 = mPlaylist.size() - 1;
            }

            if (index1 == index2) {
                return;
            }
            mPlaylistInfo.remove(mPlaylist.get(index1).mId);
            final MusicTrack track = mPlaylist.remove(index1);
            if (index1 < index2) {
                mPlaylist.add(index2, track);
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index1 && mPlayPos <= index2) {
                    mPlayPos--;
                }
            } else if (index2 < index1) {
                mPlaylist.add(index2, track);
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index2 && mPlayPos <= index1) {
                    mPlayPos++;
                }
            }
            notifyChange(QUEUE_CHANGED);
        }
    }

    public void enqueue(final long[] list, final HashMap<Long, MusicInfo> map, final int action) {
        synchronized (this) {
            mPlaylistInfo.putAll(map);
            if (action == NEXT && mPlayPos + 1 < mPlaylist.size()) {
                addToPlayList(list, mPlayPos + 1);
                mNextPlayPos = mPlayPos + 1;
                notifyChange(QUEUE_CHANGED);
            } else {
                addToPlayList(list, Integer.MAX_VALUE);
                notifyChange(QUEUE_CHANGED);
            }

            if (mPlayPos < 0) {
                mPlayPos = 0;
                openCurrentAndNext();
                play();
                notifyChange(META_CHANGED);
            }
        }
    }

    //cycle repeat
    //单曲 循环
    private void cycleRepeat() {
        if (mRepeatMode == REPEAT_NONE) {
            setRepeatMode(REPEAT_CURRENT);
            if (mShuffleMode != SHUFFLE_NONE) {
                //  关闭随机播放
                setShuffleMode(SHUFFLE_NONE);
            }
        } else {
            setRepeatMode(REPEAT_NONE);
        }
    }

    // 随机循环
    private void cycleShuffle() {
        if (mShuffleMode == SHUFFLE_NONE) {
            setShuffleMode(SHUFFLE_NORMAL);
            if (mRepeatMode == REPEAT_CURRENT) {
                setRepeatMode(REPEAT_ALL);
            }
        } else if (mShuffleMode == SHUFFLE_NORMAL || mShuffleMode == SHUFFLE_AUTO) {
            setShuffleMode(SHUFFLE_NONE);
        }
    }

    //  刷新
    public void refresh() {
        notifyChange(REFRESH);
    }

    //  播放列表发生变化
    public void playlistChanged() {
        notifyChange(PLAYLIST_CHANGED);
    }

    public void loading(boolean l) {
        Intent intent = new Intent(MUSIC_LODING);
        intent.putExtra("isloading", l);
        sendBroadcast(intent);
    }

    // 设置在锁屏显示 艺术家信息
    public void setLockscreenAlbumArt(boolean enabled) {
        mShowAlbumArtOnLockscreen = enabled;
        notifyChange(META_CHANGED);
    }

    //  定时器
    public void timing(int time) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(PAUSE_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.set(AlarmManager.RTC, System.currentTimeMillis() + time, pendingIntent);

    }

    public interface TrackErrorExtra {

        String TRACK_NAME = "trackname";
    }

    /**
     * 处理 音量增加  音量减少 ，释放  处理上一首  焦点变化  等
     */
    private static final class MusicPlayerHandler extends Handler {
        private final WeakReference<MediaService> mService;
        private float mCurrentVolume = 1.0f;


        public MusicPlayerHandler(final MediaService service, final Looper looper) {
            super(looper);
            mService = new WeakReference<MediaService>(service);
        }


        @Override
        public void handleMessage(final Message msg) {
            final MediaService service = mService.get();
            if (service == null) {
                return;
            }

            synchronized (service) {
                switch (msg.what) {
                    case FADEDOWN:
                        mCurrentVolume -= .05f;
                        if (mCurrentVolume > .2f) {
                            sendEmptyMessageDelayed(FADEDOWN, 10);
                        } else {
                            mCurrentVolume = .2f;
                        }
                        service.mPlayer.setVolume(mCurrentVolume);
                        break;
                    case FADEUP:
                        mCurrentVolume += .01f;
                        if (mCurrentVolume < 1.0f) {
                            sendEmptyMessageDelayed(FADEUP, 10);
                        } else {
                            mCurrentVolume = 1.0f;
                        }
                        service.mPlayer.setVolume(mCurrentVolume);
                        break;
                    case SERVER_DIED:
                        if (service.isPlaying()) {
                            final TrackErrorInfo info = (TrackErrorInfo) msg.obj;
                            service.sendErrorMessage(info.mTrackName);


                            service.removeTrack(info.mId);
                        } else {
                            service.openCurrentAndNext();
                        }
                        break;
                    case TRACK_WENT_TO_NEXT:
                        service.setAndRecordPlayPos(service.mNextPlayPos);
                        service.setNextTrack();
                        if (service.mCursor != null) {
                            service.mCursor.close();
                            service.mCursor = null;
                        }

                        service.updateCursor(service.mPlaylist.get(service.mPlayPos).mId);
                        service.notifyChange(META_CHANGED);
                        service.notifyChange(MUSIC_CHANGED);
                        service.updateNotification();
                        break;
                    case TRACK_ENDED:
                        if (service.mRepeatMode == REPEAT_CURRENT) {
                            service.seek(0);
                            service.play();
                        } else {
                            if (D) Log.d(TAG, "Going to  of track");
                            service.gotoNext(false);
                        }
                        break;
                    case RELEASE_WAKELOCK:
                        service.mWakeLock.release();
                        break;
                    case FOCUSCHANGE:
                        if (D) Log.d(TAG, "Received audio focus change event " + msg.arg1);
                        switch (msg.arg1) {
                            case AudioManager.AUDIOFOCUS_LOSS:
                            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                                if (service.isPlaying()) {
                                    service.mPausedByTransientLossOfFocus =
                                            msg.arg1 == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                                }
                                service.pause();
                                break;
                            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                                removeMessages(FADEUP);
                                sendEmptyMessage(FADEDOWN);
                                break;
                            case AudioManager.AUDIOFOCUS_GAIN:
                                if (!service.isPlaying()
                                        && service.mPausedByTransientLossOfFocus) {
                                    service.mPausedByTransientLossOfFocus = false;
                                    mCurrentVolume = 0f;
                                    service.mPlayer.setVolume(mCurrentVolume);
                                    service.play();
                                } else {
                                    removeMessages(FADEDOWN);
                                    sendEmptyMessage(FADEUP);
                                }
                                break;
                            default:
                        }
                        break;
                    case LRC_DOWNLOADED:
                        service.notifyChange(LRC_UPDATED);
                    default:
                        break;
                }
            }
        }
    }

    //  随机播放的类
    private static final class Shuffler {

        private final LinkedList<Integer> mHistoryOfNumbers = new LinkedList<Integer>();

        private final TreeSet<Integer> mPreviousNumbers = new TreeSet<Integer>();

        private final Random mRandom = new Random();

        private int mPrevious;


        public Shuffler() {
            super();
        }

        //  随机获取下一个曲目
        public int nextInt(final int interval) {
            int next;
            do {
                next = mRandom.nextInt(interval);
            } while (next == mPrevious && interval > 1
                    && !mPreviousNumbers.contains(Integer.valueOf(next)));
            mPrevious = next;
            mHistoryOfNumbers.add(mPrevious);
            mPreviousNumbers.add(mPrevious);
            cleanUpHistory();
            return next;
        }

        //  历史记录有一定的限制
        private void cleanUpHistory() {
            if (!mHistoryOfNumbers.isEmpty() && mHistoryOfNumbers.size() >= MAX_HISTORY_SIZE) {
                //  超过最大的值，删除前一半的历史记录
                for (int i = 0; i < Math.max(1, MAX_HISTORY_SIZE / 2); i++) {
                    mPreviousNumbers.remove(mHistoryOfNumbers.removeFirst());
                }
            }
        }
    }

    public static final class TrackErrorInfo {
        public long mId;
        public String mTrackName;

        public TrackErrorInfo(long id, String trackName) {
            mId = id;
            mTrackName = trackName;
        }
    }


    private static final class ServiceStub extends MediaAidlInterface.Stub {

        private final WeakReference<MediaService> mService;

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            try {
                super.onTransact(code, data, reply, flags);
            } catch (final RuntimeException e) {
//               L.E(D,TAG,"onTransact error");
                e.printStackTrace();
                File file = new File(mService.get().getCacheDir().getAbsolutePath() + "/err/");
                if (!file.exists()) {
                    file.mkdirs();
                }
                try {
                    PrintWriter writer = new PrintWriter(mService.get().getCacheDir().getAbsolutePath() + "/err/" + System.currentTimeMillis() + "_aidl.log");
                    e.printStackTrace(writer);
                    writer.close();
                } catch (Exception e1) {
                    e1.printStackTrace();
                }

                throw e;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return true;
        }

        private ServiceStub(final MediaService service) {
            mService = new WeakReference<MediaService>(service);
        }

        @Override
        public void openFile(final String path) throws RemoteException {
            mService.get().openFile(path);
        }

        @Override
        public void open(final Map infos, final long[] list, final int position)
                throws RemoteException {
            mService.get().open((HashMap<Long, MusicInfo>) infos, list, position);
        }

        @Override
        public void stop() throws RemoteException {
            mService.get().stop();
        }

        @Override
        public void pause() throws RemoteException {
            mService.get().pause();
        }


        @Override
        public void play() throws RemoteException {
            mService.get().play();
        }

        @Override
        public void prev(boolean forcePrevious) throws RemoteException {
            mService.get().prev(forcePrevious);
        }

        @Override
        public void next() throws RemoteException {
            mService.get().gotoNext(true);
        }

        @Override
        public void enqueue(final long[] list, final Map infos, final int action)
                throws RemoteException {
            mService.get().enqueue(list, (HashMap<Long, MusicInfo>) infos, action);
        }

        @Override
        public Map getPlayinfos() throws RemoteException {
            return mService.get().getPlayinfos();
        }

        @Override
        public void moveQueueItem(final int from, final int to) throws RemoteException {
            mService.get().moveQueueItem(from, to);
        }

        @Override
        public void refresh() throws RemoteException {
            mService.get().refresh();
        }

        @Override
        public void playlistChanged() throws RemoteException {
            mService.get().playlistChanged();
        }

        @Override
        public boolean isPlaying() throws RemoteException {
            return mService.get().isPlaying();
        }

        @Override
        public long[] getQueue() throws RemoteException {
            return mService.get().getQueue();
        }

        @Override
        public long getQueueItemAtPosition(int position) throws RemoteException {
            return mService.get().getQueueItemAtPosition(position);
        }

        @Override
        public int getQueueSize() throws RemoteException {
            return mService.get().getQueueSize();
        }

        @Override
        public int getQueueHistoryPosition(int position) throws RemoteException {
            return mService.get().getQueueHistoryPosition(position);
        }

        @Override
        public int getQueueHistorySize() throws RemoteException {
            return mService.get().getQueueHistorySize();
        }

        @Override
        public int[] getQueueHistoryList() throws RemoteException {
            return mService.get().getQueueHistoryList();
        }

        @Override
        public long duration() throws RemoteException {
            return mService.get().duration();
        }

        @Override
        public long position() throws RemoteException {
            return mService.get().position();
        }

        @Override
        public int secondPosition() throws RemoteException {
            return mService.get().getSecondPosition();
        }

        @Override
        public long seek(final long position) throws RemoteException {
            return mService.get().seek(position);
        }

        @Override
        public void seekRelative(final long deltaInMs) throws RemoteException {
            mService.get().seekRelative(deltaInMs);
        }

        @Override
        public long getAudioId() throws RemoteException {
            return mService.get().getAudioId();
        }

        @Override
        public MusicTrack getCurrentTrack() throws RemoteException {
            return mService.get().getCurrentTrack();
        }

        @Override
        public MusicTrack getTrack(int index) throws RemoteException {
            return mService.get().getTrack(index);
        }

        @Override
        public long getNextAudioId() throws RemoteException {
            return mService.get().getNextAudioId();
        }

        @Override
        public long getPreviousAudioId() throws RemoteException {
            return mService.get().getPreviousAudioId();
        }

        @Override
        public long getArtistId() throws RemoteException {
            return mService.get().getArtistId();
        }

        @Override
        public long getAlbumId() throws RemoteException {
            return mService.get().getAlbumId();
        }

        @Override
        public String getArtistName() throws RemoteException {
            return mService.get().getArtistName();
        }

        @Override
        public String getTrackName() throws RemoteException {
            return mService.get().getTrackName();
        }

        @Override
        public boolean isTrackLocal() throws RemoteException {
            return mService.get().isTrackLocal();
        }

        @Override
        public String getAlbumName() throws RemoteException {
            return mService.get().getAlbumName();
        }

        @Override
        public String getAlbumPath() throws RemoteException {
            return mService.get().getAlbumPath();
        }

        @Override
        public String[] getAlbumPathtAll() throws RemoteException {
            return mService.get().getAlbumPathAll();
        }

        @Override
        public String getPath() throws RemoteException {
            return mService.get().getPath();
        }

        @Override
        public int getQueuePosition() throws RemoteException {
            return mService.get().getQueuePosition();
        }

        @Override
        public void setQueuePosition(final int index) throws RemoteException {
            mService.get().setQueuePosition(index);
        }

        @Override
        public int getShuffleMode() throws RemoteException {
            return mService.get().getShuffleMode();
        }

        @Override
        public void setShuffleMode(final int shufflemode) throws RemoteException {
            mService.get().setShuffleMode(shufflemode);
        }

        @Override
        public int getRepeatMode() throws RemoteException {
            return mService.get().getRepeatMode();
        }

        @Override
        public void setRepeatMode(final int repeatmode) throws RemoteException {
            mService.get().setRepeatMode(repeatmode);
        }

        @Override
        public int removeTracks(final int first, final int last) throws RemoteException {
            return mService.get().removeTracks(first, last);
        }


        @Override
        public int removeTrack(final long id) throws RemoteException {
            return mService.get().removeTrack(id);
        }


        @Override
        public boolean removeTrackAtPosition(final long id, final int position)
                throws RemoteException {
            return mService.get().removeTrackAtPosition(id, position);
        }


        @Override
        public int getMediaMountedCount() throws RemoteException {
            return mService.get().getMediaMountedCount();
        }


        @Override
        public int getAudioSessionId() throws RemoteException {
            return mService.get().getAudioSessionId();
        }


        @Override
        public void setLockscreenAlbumArt(boolean enabled) {
            mService.get().setLockscreenAlbumArt(enabled);
        }

        @Override
        public void exit() throws RemoteException {
            mService.get().exit();
        }

        @Override
        public void timing(int time) throws RemoteException {
            mService.get().timing(time);
        }

    }

    //  内容观察者   内容发生变化的时候   查看是否有新的内容添加
    private class MediaStoreObserver extends ContentObserver implements Runnable {

        private static final long REFRESH_DELAY = 500;
        private Handler mHandler;

        public MediaStoreObserver(Handler handler) {
            super(handler);
            mHandler = handler;
        }

        @Override
        public void onChange(boolean selfChange) {


            mHandler.removeCallbacks(this);
            //  延迟REFRESH_DELAY 秒 之后 执行 run 的方法，
            mHandler.postDelayed(this, REFRESH_DELAY);
        }

        @Override
        public void run() {
            //  进行任务刷新
            Log.e("ELEVEN", "calling refresh!");
            refresh();
        }
    }


}