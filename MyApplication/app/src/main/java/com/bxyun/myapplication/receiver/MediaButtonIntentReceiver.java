/*
 * Copyright (C) 2007 The Android Open Source Project Licensed under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.bxyun.myapplication.receiver;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.KeyEvent;

import androidx.legacy.content.WakefulBroadcastReceiver;

import com.bxyun.myapplication.MainActivity;
import com.bxyun.myapplication.service.MediaService;


/**
 * Used to control headset playback.
 * Single press: pause/resume     单机
 * Double press: next track      双击
 * Triple press: previous track   三击（连续点击三次）
 * Long press: voice search
 */
public class MediaButtonIntentReceiver extends WakefulBroadcastReceiver {
    private static final boolean DEBUG = false;
    private static final String TAG = "ButtonIntentReceiver";

    private static final int MSG_LONGPRESS_TIMEOUT = 1;
    private static final int MSG_HEADSET_DOUBLE_CLICK_TIMEOUT = 2;

    private static final int LONG_PRESS_DELAY = 1000;
    private static final int DOUBLE_CLICK = 800;

    private static WakeLock mWakeLock = null;
    private static int mClickCounter = 0;
    private static long mLastClickTime = 0;
    private static boolean mDown = false;
    private static boolean mLaunched = false;

    private static Handler mHandler = new Handler() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case MSG_LONGPRESS_TIMEOUT:
                    if (DEBUG) Log.v(TAG, "Handling longpress timeout, launched " + mLaunched);
                    if (!mLaunched) {
                        final Context context = (Context) msg.obj;
                        final Intent i = new Intent();
                        i.setClass(context, MainActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(i);
                        mLaunched = true;
                    }
                    break;

                case MSG_HEADSET_DOUBLE_CLICK_TIMEOUT:
                    final int clickCount = msg.arg1;
                    final String command;

                    if (DEBUG) Log.v(TAG, "Handling headset click, count = " + clickCount);
                    switch (clickCount) {
                        case 1:
                            command = MediaService.CMDTOGGLEPAUSE;
                            break;
                        case 2:
                            command = MediaService.CMDNEXT;
                            break;
                        case 3:
                            command = MediaService.CMDPREVIOUS;
                            break;
                        default:
                            command = null;
                            break;
                    }

                    if (command != null) {
                        final Context context = (Context) msg.obj;
                        startService(context, command);
                    }
                    break;
            }
            releaseWakeLockIfHandlerIdle();
        }
    };

    private static void startService(Context context, String command) {
        final Intent i = new Intent(context, MediaService.class);
        i.setAction(MediaService.SERVICECMD);
        i.putExtra(MediaService.CMDNAME, command);
        i.putExtra(MediaService.FROM_MEDIA_BUTTON, true);
        startWakefulService(context, i);
    }

    @SuppressLint("InvalidWakeLockTag")
    private static void acquireWakeLockAndSendMessage(Context context, Message msg, long delay) {
        if (mWakeLock == null) {
            Context appContext = context.getApplicationContext();
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Timber headset button");
            mWakeLock.setReferenceCounted(false);
        }
        if (DEBUG) Log.v(TAG, "Acquiring wake lock and sending " + msg.what);
        // Make sure we don't indefinitely hold the wake lock under any circumstances
        mWakeLock.acquire(10000);

        mHandler.sendMessageDelayed(msg, delay);
    }

    private static void releaseWakeLockIfHandlerIdle() {
        if (mHandler.hasMessages(MSG_LONGPRESS_TIMEOUT)
                || mHandler.hasMessages(MSG_HEADSET_DOUBLE_CLICK_TIMEOUT)) {
            if (DEBUG) Log.v(TAG, "Handler still has messages pending, not releasing wake lock");
            return;
        }

        if (mWakeLock != null) {
            if (DEBUG) Log.v(TAG, "Releasing wake lock");
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String intentAction = intent.getAction();
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intentAction)) {
            if (true)
                startService(context, MediaService.CMDPAUSE);
        } else if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
            final KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event == null) {
                return;
            }

            final int keycode = event.getKeyCode();
            final int action = event.getAction();
            final long eventtime = event.getEventTime();

            String command = null;
            /**
             *
             对于不同的设备   有手机的（手机没有物理下一首的按键）、车载的
             KEYCODE_MEDIA_PLAY	多媒体键 播放
             KEYCODE_MEDIA_STOP	多媒体键 停止
             KEYCODE_MEDIA_PAUSE	多媒体键 暂停
             KEYCODE_MEDIA_PLAY_PAUSE	多媒体键 播放/暂停
             KEYCODE_MEDIA_FAST_FORWARD	多媒体键 快进
             KEYCODE_MEDIA_REWIND	多媒体键 快退
             KEYCODE_MEDIA_NEXT	多媒体键 下一首
             KEYCODE_MEDIA_PREVIOUS	多媒体键 上一首
             KEYCODE_MEDIA_CLOSE	多媒体键 关闭
             KEYCODE_MEDIA_EJECT	多媒体键 弹出
             KEYCODE_MEDIA_RECORD	多媒体键 录音

             */
            switch (keycode) {
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    command = MediaService.CMDSTOP;
                    break;
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    command = MediaService.CMDTOGGLEPAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    command = MediaService.CMDNEXT;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    command = MediaService.CMDPREVIOUS;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    command = MediaService.CMDPAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    command = MediaService.CMDPLAY;
                    break;
            }
            if (command != null) {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mDown) {
                        //  暂停和播放的处理
                        if (MediaService.CMDTOGGLEPAUSE.equals(command)
                                || MediaService.CMDPLAY.equals(command)) {
                            if (mLastClickTime != 0
                                    && eventtime - mLastClickTime > LONG_PRESS_DELAY) {
                                acquireWakeLockAndSendMessage(context,
                                        mHandler.obtainMessage(MSG_LONGPRESS_TIMEOUT, context), 0);
                            }
                        }
                    } else if (event.getRepeatCount() == 0) {// 物理按键点击的次数

                        if (keycode == KeyEvent.KEYCODE_HEADSETHOOK) {//按下了耳机键
                            if (eventtime - mLastClickTime >= DOUBLE_CLICK) {
                                // 记录耳机键按下的次数
                                mClickCounter = 0;
                            }

                            mClickCounter++;
                            if (DEBUG) Log.v(TAG, "Got headset click, count = " + mClickCounter);
                            mHandler.removeMessages(MSG_HEADSET_DOUBLE_CLICK_TIMEOUT);

                            Message msg = mHandler.obtainMessage(
                                    MSG_HEADSET_DOUBLE_CLICK_TIMEOUT, mClickCounter, 0, context);

                            long delay = mClickCounter < 3 ? DOUBLE_CLICK : 0;
                            if (mClickCounter >= 3) {
                                mClickCounter = 0;
                            }
                            mLastClickTime = eventtime;
                            acquireWakeLockAndSendMessage(context, msg, delay);
                        } else {
                            startService(context, command);
                        }
                        mLaunched = false;
                        mDown = true;
                    }
                } else {
                    mHandler.removeMessages(MSG_LONGPRESS_TIMEOUT);
                    mDown = false;
                }

                //  判断当前广播是否是有序广播
                if (isOrderedBroadcast()) {
                    // 终止所有广播
                    abortBroadcast();
                }
                releaseWakeLockIfHandlerIdle();
            }
        }
    }
}
