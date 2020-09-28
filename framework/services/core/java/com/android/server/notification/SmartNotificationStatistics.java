package com.android.server.notification;

import android.util.Slog;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.Iterator;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import android.os.Environment;
import android.os.FileUtils;
public class SmartNotificationStatistics {
    public static final String TAG = SmartNotificationStatistics.class.getSimpleName();
    public ArrayList<String> mLog;
    // notify
    int postedNotificationCnt;
    int postedSendableNotificationCnt;

    // notify delay
    int delayedNotificationCnt; // sended + updated + cancled

    int delayedUpdatedNotificationCnt;
    int delayedSendedNotificationCnt;
    int delayedCanceledNotificationCnt; // delayedCanceledByUser + App + other

    int delayedCanceledByUserPerceived;
    int delayedCanceledByUserConfirm;
    int delayedCanceledByApp;
    int delayedCanceledByOther;
    
    int perceivedCanceled;
    int perceivedCanceledByUserConfirm;
    int perceivedCanceledByApp;
    int perceivedCanceledByWear;
    int perceivedCanceledByOther;

    // cancel
    int canceledNotificationCnt;    // total
    int canceledSendableCancelCnt;  // sendable total

    // cancel reason
    int canceledByUser;
    int canceledByUserClick;
    int canceledByApp;
    int canceledByWear;
    int canceledByOther;

    // cancel delay info
    int canceledNotDelayed;
    int canceledDelayed;
    int canceledRemoved;
    
    int canceledDelayedSended;
    int canceledDelayedSendedCnt;
    double canceledDelayedSendedAvgCnt;

    public SmartNotificationStatistics() {
        mLog = new ArrayList<String>();
    }

    public SmartNotificationStatistics(Bundle bundle) {
        fromBundle(bundle);
    }

    public void init() {
        postedNotificationCnt = 0;
        postedSendableNotificationCnt = 0;

        delayedNotificationCnt = 0;

        delayedUpdatedNotificationCnt = 0;
        delayedSendedNotificationCnt = 0;
        delayedCanceledNotificationCnt = 0;

        delayedCanceledByUserPerceived = 0;

        delayedCanceledByUserConfirm = 0;   //naver occured...
        delayedCanceledByApp = 0;
        delayedCanceledByOther = 0;
        
        perceivedCanceled = 0;
        perceivedCanceledByUserConfirm = 0;
        perceivedCanceledByApp = 0;
        perceivedCanceledByWear = 0;
        perceivedCanceledByOther = 0;

        canceledNotificationCnt = 0;
        canceledSendableCancelCnt = 0;
        canceledByUser = 0;
        canceledByUserClick = 0;
        canceledByApp = 0;
        canceledByWear = 0;
        canceledByOther = 0;

        canceledNotDelayed = 0;
        canceledDelayed = 0;
        canceledRemoved = 0;
        canceledDelayedSended = 0;
        canceledDelayedSendedCnt = 0;
        canceledDelayedSendedAvgCnt = 0;

        mLog.clear();
    }

    @Override
    public String toString() {
        return "\n" +
                "postedNotificationCnt : " + postedNotificationCnt +
                "\npostedSendableNotificationCnt : " + postedSendableNotificationCnt +
                "\ndelayedNotificationCnt : " + delayedNotificationCnt +
                "\ndelayedSendedNotificationCnt : " + delayedSendedNotificationCnt +
                "\ndelayedUpdatedNotificationCnt : " + delayedUpdatedNotificationCnt +
                "\ndelayedCanceledNotificationCnt : " + delayedCanceledNotificationCnt +
                "\n   delayedCanceledByUserPerceived : " + delayedCanceledByUserPerceived +
                "\n   delayedCanceledByUserConfirm : " + delayedCanceledByUserConfirm +
                "\n   delayedCanceledByApp : " + delayedCanceledByApp +
                "\n   delayedCanceledByOther : " + delayedCanceledByOther +
                
                "\n\nperceivedCanceled : " + perceivedCanceled +
                "\n   perceivedCanceledByUserConfirm : "+ perceivedCanceledByUserConfirm +
                "\n   perceivedCanceledByApp : " + perceivedCanceledByApp+ 
                "\n   perceivedCanceledByWear : "+ perceivedCanceledByWear +
                "\n   perceivedCanceledByOther : "+ perceivedCanceledByOther+

                "\n\ncanceledNotificationCnt : " + canceledNotificationCnt +
                "\ncanceledSendableCancelCnt : " + canceledSendableCancelCnt +
                "\n   canceledByUser : " + canceledByUser +
                "\n   canceledByUserClick : " + canceledByUserClick +
                "\n   canceledByApp : " + canceledByApp +
                "\n   canceledByWear : "+ canceledByWear +
                "\n   canceledByOther : "+ canceledByOther +

                "\n\ncanceledNotDelayed : " + canceledNotDelayed +
                "\ncanceledDelayed : " + canceledDelayed +
                "\ncanceledDelayedRemoved : " + canceledRemoved +
                "\ncanceledDelayedSended : " + canceledDelayedSended +
                "\ncanceledDelayedSendedCnt : " + canceledDelayedSendedCnt +
                "\ncanceledDelayedSendedAvgCnt : " + canceledDelayedSendedAvgCnt;
    }

    public Bundle getBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt("postedNotificationCnt", postedNotificationCnt);
        bundle.putInt("postedSendableNotificationCnt", postedSendableNotificationCnt);

        bundle.putInt("delayedNotificationCnt", delayedNotificationCnt);

        bundle.putInt("delayedUpdatedNotificationCnt", delayedUpdatedNotificationCnt);
        bundle.putInt("delayedSendedNotificationCnt", delayedSendedNotificationCnt);
        bundle.putInt("delayedCanceledNotificationCnt", delayedCanceledNotificationCnt);

        bundle.putInt("delayedCanceledByUserPerceived", delayedCanceledByUserPerceived);
        bundle.putInt("delayedCanceledByUserConfirm", delayedCanceledByUserConfirm);
        bundle.putInt("delayedCanceledByApp", delayedCanceledByApp);
        bundle.putInt("delayedCanceledByOther", delayedCanceledByOther);

        bundle.putInt("perceivedCanceled", perceivedCanceled);
        bundle.putInt("perceivedCanceledByUserConfirm", perceivedCanceledByUserConfirm);
        bundle.putInt("perceivedCanceledByApp", perceivedCanceledByApp);
        bundle.putInt("perceivedCanceledByWear", perceivedCanceledByWear);
        bundle.putInt("perceivedCanceledByOther", perceivedCanceledByOther);

        bundle.putInt("canceledNotificationCnt", canceledNotificationCnt);
        bundle.putInt("canceledSendableCancelCnt", canceledSendableCancelCnt);

        bundle.putInt("canceledByUser", canceledByUser);
        bundle.putInt("canceledByUserClick", canceledByUserClick);
        bundle.putInt("canceledByApp", canceledByApp);
        bundle.putInt("canceledByWear", canceledByWear);
        bundle.putInt("canceledByOther", canceledByOther);

        bundle.putInt("canceledNotDelayed", canceledNotDelayed);
        bundle.putInt("canceledDelayed", canceledDelayed);
        bundle.putInt("canceledRemoved", canceledRemoved);

        bundle.putInt("canceledDelayedSended", canceledDelayedSended);
        bundle.putInt("canceledDelayedSendedCnt", canceledDelayedSendedCnt);
        bundle.putDouble("canceledDelayedSendedAvgCnt", canceledDelayedSendedAvgCnt);


        return bundle;
    }

    public void fromBundle(Bundle bundle) {
        postedNotificationCnt = bundle.getInt("postedNotificationCnt", postedNotificationCnt);
        postedSendableNotificationCnt = bundle.getInt("postedSendableNotificationCnt", postedSendableNotificationCnt);

        delayedNotificationCnt = bundle.getInt("delayedNotificationCnt", delayedNotificationCnt);

        delayedUpdatedNotificationCnt = bundle.getInt("delayedUpdatedNotificationCnt", delayedUpdatedNotificationCnt);
        delayedSendedNotificationCnt = bundle.getInt("delayedSendedNotificationCnt", delayedSendedNotificationCnt);
        delayedCanceledNotificationCnt = bundle.getInt("delayedCanceledNotificationCnt", delayedCanceledNotificationCnt);

        delayedCanceledByUserPerceived = bundle.getInt("delayedCanceledByUserPerceived", delayedCanceledByUserPerceived);
        delayedCanceledByUserConfirm = bundle.getInt("delayedCanceledByUserConfirm", delayedCanceledByUserConfirm);
        delayedCanceledByApp = bundle.getInt("delayedCanceledByApp", delayedCanceledByApp);
        delayedCanceledByOther = bundle.getInt("delayedCanceledByOther", delayedCanceledByOther);

        perceivedCanceled = bundle.getInt("perceivedCanceled", perceivedCanceled);
        perceivedCanceledByUserConfirm = bundle.getInt("perceivedCanceledByUserConfirm", perceivedCanceledByUserConfirm);
        perceivedCanceledByApp = bundle.getInt("perceivedCanceledByApp", perceivedCanceledByApp);
        perceivedCanceledByWear = bundle.getInt("perceivedCanceledByWear", perceivedCanceledByWear);
        perceivedCanceledByOther = bundle.getInt("perceivedCanceledByOther", perceivedCanceledByOther);

        canceledNotificationCnt = bundle.getInt("canceledNotificationCnt", canceledNotificationCnt);
        canceledSendableCancelCnt = bundle.getInt("canceledSendableCancelCnt", canceledSendableCancelCnt);

        canceledByUser = bundle.getInt("canceledByUser", canceledByUser);
        canceledByUserClick = bundle.getInt("canceledByUserClick", canceledByUserClick);
        canceledByApp = bundle.getInt("canceledByApp", canceledByApp);
        canceledByWear = bundle.getInt("canceledByWear", canceledByWear);
        canceledByOther = bundle.getInt("canceledByOther", canceledByOther);

        canceledNotDelayed = bundle.getInt("canceledDelayed", canceledNotDelayed);
        canceledDelayed = bundle.getInt("canceledDelayed", canceledDelayed);
        canceledRemoved = bundle.getInt("canceledRemoved", canceledRemoved);

        canceledDelayedSended = bundle.getInt("canceledDelayedSended", canceledDelayedSended);
        canceledDelayedSended = bundle.getInt("canceledDelayedSendedCnt", canceledDelayedSendedCnt);
        canceledDelayedSendedAvgCnt = bundle.getDouble("canceledDelayedSendedAvgCnt", canceledDelayedSendedAvgCnt);
    }

    public void writeLog(String msg) {
        synchronized(mLog) {
            String t = getTimeStringFromSystemMillis() + " " + msg;
            mLog.add(t);
            Slog.i(TAG, t);
        }
    }

    static SimpleDateFormat timeSDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    public static String getTimeStringFromSystemMillis(long timeMillis) {
        return timeSDF.format(new Date(timeMillis));
    }
    public static String getTimeStringFromSystemMillis() {
        return timeSDF.format(new Date());
    }

    public boolean writeLogToFile(String path) {
        File logFile;
        FileWriter fw;
        BufferedWriter bufferWritter;
        try {
            logFile = new File(path);
            File directory = new File(logFile.getParentFile().getAbsolutePath());
            directory.mkdirs();
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            fw = new FileWriter(logFile, true);
            bufferWritter = new BufferedWriter(fw);
            Slog.i(TAG, "log size : " + mLog.size());
            for (int i = 0; i < mLog.size(); i++) {
                bufferWritter.write(mLog.get(i) + "\n");
            }
            bufferWritter.write("statistics start");
            bufferWritter.write(toString() + "\n");
            bufferWritter.write("statistics end\n");
            bufferWritter.close();
            FileUtils.setPermissions(logFile.getPath(), 0777, -1, -1); // drwxrwxr-x
            FileUtils.setPermissions(directory.getPath(), 0777, -1, -1); // drwxrwxr-x
            
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
        Slog.i(TAG, "writeLogToFile " + path + " success");
        return true;
    }
}