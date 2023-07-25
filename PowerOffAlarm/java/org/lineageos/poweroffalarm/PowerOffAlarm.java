/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.poweroffalarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.qualcomm.qti.poweroffalarm.R;

public class PowerOffAlarm {
    private static final String TAG = "PowerOffAlarm";
    private static final String PREF_KEY_ALARM_TIME = "alarm_time";
    private static final boolean DEBUG = false;

    private final Context mContext;
    private final long mPreBootMillisec;
    private final Driver mDriver;
    private final SharedPreferences mPrefs;

    public PowerOffAlarm(Context context) {
        mContext = context;
        mPreBootMillisec = mContext.getResources().getInteger(R.integer.config_preBootMillis);
        final String driverConfig = mContext.getString(R.string.config_powerOffAlarmDrivers);
        Log.d(TAG, "trying driver config " + driverConfig);
        final String[] drivers = driverConfig != null ? driverConfig.split(",") : new String[0];
        Driver driver = null;
        for (String driverName : drivers) {
            Log.d(TAG, "trying driver " + driverName);
            switch (driverName) {
                case "alarm":
                    driver = new AlarmDriver();
                    break;
                case "rtc":
                    driver = new RtcDriver();
                    break;
                case "timerfd":
                    driver = new TimerFdDriver();
                    break;
                default:
                    Log.w(TAG, "driver " + driverName + " does not exist");
                    break;
            }
            boolean isSupported = false;
            if (driver != null) {
                try {
                    isSupported = driver.isSupported();
                } catch (Exception e) {
                    Log.e(TAG, Log.getStackTraceString(e));
                }
            }
            if (isSupported) {
                Log.d(TAG, "selected driver " + driverName);
                break;
            }
            Log.d(TAG, "driver " + driverName + " is not supported");
            driver = null;
        }
        mDriver = driver;
        if (mDriver == null) {
            Log.w(TAG, "no driver found, disabling PowerOffAlarm");
        }
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public void saveAlarmToPreference(long time) {
        mPrefs.edit().putLong(PREF_KEY_ALARM_TIME, time).apply();
    }

    public long getAlarmFromPreference() {
        return mPrefs.getLong(PREF_KEY_ALARM_TIME, 0L);
    }

    public void cancelAlarmInRtc() {
        if (mDriver != null) {
            try {
                mDriver.cancel();
            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    }

    public void setAlarmToRtc(long time) {
        if (mDriver != null) {
            long currentTime = System.currentTimeMillis();
            if (time <= currentTime + mPreBootMillisec) {
                Log.w(TAG, time + " is too early, current time is " + currentTime + "!");
                return;
            }
            long rtcTime = -1;
            try {
                rtcTime = mDriver.getRtcTimeOptional();
                if (DEBUG) Log.v(TAG, "read rtcTime "  + rtcTime + " current " + currentTime);
            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            if (rtcTime > 0) {
                // Account for differences in counting between RTC and AP clock, if needed
                long timeDiff = time - currentTime;
                time = (rtcTime + timeDiff) - mPreBootMillisec;
            } else {
                time -= mPreBootMillisec;
            }
            try {
                mDriver.set(time);
            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    }
}
