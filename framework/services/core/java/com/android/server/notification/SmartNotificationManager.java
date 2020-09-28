package com.android.server.notification;
import android.util.Slog;

import android.service.notification.StatusBarNotification;
import android.os.Handler;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import android.os.Looper;
import android.os.Message;
import android.os.Handler;
import android.os.HandlerThread;
import java.util.LinkedHashMap;
import android.content.Intent;
import android.os.UserHandle;
import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.io.File;

class SmartNotificationManager {
	// private final Object mLock = new Object();
	private NotificationManagerService nms = null;
	private Handler mHandler;
	private static int screen_timeout = 30;
	public boolean bEnabled = false;
	private boolean bScreenOn = true;
	private boolean pendingDirty = false;
	private long screenOnTimeMillis;
	public long userActivityTimeMillis;			// for debug
	public boolean bUserActivityEnabled = true;

	private static final int MESSAGE_SMARTNOTIFICATION = NotificationManagerService.MESSAGE_SMARTNOTIFICATION;
	private static final int ARG1_SEND_NOTIFICATION = 1;

	private static int PERCEIVED_THRESHOLD = 15 * 1000;
	private static int NOTIFICATION_DELAY_TIMEOUT = 15 * 1000;

	public SmartNotificationStatistics st = new SmartNotificationStatistics();


	public SmartNotificationManager(NotificationManagerService nms, Handler handler) {
		this.nms = nms;
		this.mHandler = handler;

/*
	    HandlerThread t = new HandlerThread("My Handler Thread");
	    t.start();
	    mHandler = new MyHandler(t.getLooper());
	    */
	}

	public static final String TAG = SmartNotificationManager.class.getSimpleName();
	LinkedList<SmartNotificationItem> postedDelayingNotification = new LinkedList<>();
	LinkedList<SmartNotificationItem> removedPendingNotification = new LinkedList<>();

	public boolean isExist(LinkedList<SmartNotificationItem> list, String key)
	{
		Iterator<SmartNotificationItem> itr = list.iterator();
		SmartNotificationItem item;
		while(itr.hasNext()) {
			item = itr.next();
			if (item.sbn.getKey().equals(key)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean deleteIfExist(LinkedList<SmartNotificationItem> list, String key)
	{
		ListIterator<SmartNotificationItem> itr = list.listIterator();
		SmartNotificationItem item;
		while(itr.hasNext()) {
			item = itr.next();
			if (item.sbn.getKey().equals(key)) {
				itr.remove();
				return true;
			}
		}
		return false;
	}
	
	public boolean isExistDelayedNotification(LinkedList<SmartNotificationItem> list)
	{
		ListIterator<SmartNotificationItem> itr = list.listIterator();
		SmartNotificationItem item;
		while(itr.hasNext()) {
			item = itr.next();
			if (item.state == SmartNotificationItem.STATE_DELAYED ||
					item.state == SmartNotificationItem.STATE_MAY_PERCEIVED) {
				return true;
			}
		}
		return false;
	}
	
	public int findIdx(List<SmartNotificationItem> list, String key) {
		if (list == null) return -1;
		for (int i = 0; i < list.size(); i++) {
			StatusBarNotification item = list.get(i).sbn;
			if (item.getKey().equals(key)) {
				return i;
			}
		}
		return -1;
	}
	
	public SmartNotificationItem findItem(List<SmartNotificationItem> list, String key) {
		if (list == null) return null;
		for (int i = 0; i < list.size(); i++) {
			StatusBarNotification item = list.get(i).sbn;
			if (item.getKey().equals(key)) {
				return list.get(i);
			}
		}
		return null;
	}

	public void onUserActivity(long time, int event) {
		// user perceived notification!
		//USER_ACTIVITY_EVENT_OTHER = 0,
		//USER_ACTIVITY_EVENT_BUTTON = 1,
		//USER_ACTIVITY_EVENT_TOUCH = 2,
		// Slog.i(TAG, "onUserActivity() " + time + " " + event);
		userActivityTimeMillis = System.currentTimeMillis();
		if (!pendingDirty) return;
		if (bUserActivityEnabled == false) return;

		Iterator<SmartNotificationItem> itr = postedDelayingNotification.iterator();
		while(itr.hasNext()) {
			SmartNotificationItem sbnItem = itr.next();
			if (sbnItem.state != SmartNotificationItem.STATE_PERCEIVED) {
				Slog.i(TAG, "onUserActivity() : STATE_PERCEIVED " + sbnItem.getKey());
				sbnItem.state = SmartNotificationItem.STATE_PERCEIVED;
				//mHandler.removeMessages(MESSAGE_SMARTNOTIFICATION, sbnItem);
				st.delayedCanceledNotificationCnt++;
				st.delayedCanceledByUserPerceived++;
				st.writeLog(sbnItem.getKey() + " UA PERCEIVED");	// user activity - perceived
			}
		}
		// remove all delaying notification event
		mHandler.removeMessages(MESSAGE_SMARTNOTIFICATION);
		pendingDirty = false;
	}

	public void onScreenOn() {
		Slog.i(TAG, "onScreenOn()");
		bScreenOn = true;
		screenOnTimeMillis = System.currentTimeMillis();
	}

	public void onScreenOff() {
		Slog.i(TAG, "onScreenOff()");
		bScreenOn = false;
		//userActivityTimeMillis = 0;
		if (bEnabled) {
			notifyAllpendingNotification("SCROFF");
		}
	}

	public void onPanelRevealed() 
	{
	// user perceived notification!
		Slog.i(TAG, "onPanelRevealed()");
		if (bScreenOn == false || !bEnabled || true) return;
		Iterator<SmartNotificationItem> itr = postedDelayingNotification.iterator();
		while(itr.hasNext()) {
			SmartNotificationItem sbnItem = itr.next();
			if (sbnItem.state == SmartNotificationItem.STATE_DELAYED || sbnItem.state == SmartNotificationItem.STATE_MAY_PERCEIVED) {
				Slog.i(TAG, "onPanelRevealed() : STATE_PERCEIVED " + sbnItem.getKey());
				sbnItem.state = SmartNotificationItem.STATE_PERCEIVED;
				mHandler.removeMessages(MESSAGE_SMARTNOTIFICATION, sbnItem);
				st.delayedCanceledNotificationCnt++;
				st.delayedCanceledByUserConfirm++;
				st.writeLog(sbnItem.getKey() + " PR C");
			}
		}
	}

	public void onPanelHidden() {
		Slog.i(TAG, "onPanelHidden()");
		if (bScreenOn == false || !bEnabled || true) return;
		Iterator<SmartNotificationItem> itr = postedDelayingNotification.iterator();
		while(itr.hasNext()) {
			SmartNotificationItem sbnItem = itr.next();
			if (sbnItem.state == SmartNotificationItem.STATE_DELAYED || sbnItem.state == SmartNotificationItem.STATE_MAY_PERCEIVED) {
				Slog.i(TAG, "onPanelHidden() : STATE_PERCEIVED " + sbnItem.getKey());
				sbnItem.state = SmartNotificationItem.STATE_PERCEIVED;
				mHandler.removeMessages(MESSAGE_SMARTNOTIFICATION, sbnItem);
				st.delayedCanceledNotificationCnt++;
				st.delayedCanceledByUserConfirm++;
			}
		}
	}

	public void onUserPresent() {
		Slog.i(TAG, "onUserPresent()");
	}

	public void setEnable() {
		Slog.i(TAG, "setEnable()");
		st.writeLog("ENABLE");
		bEnabled = true;
	}

	public void setDisable() {
		Slog.i(TAG, "setDisable()");
		st.writeLog("DISABLE");
		bEnabled = false;
		notifyAllpendingNotification("DISABLE");
		Slog.i(TAG, "reasult : " + st);
	}

	public void reset() {
		Slog.i(TAG, "reset()");
		bEnabled = false;
		notifyAllpendingNotification("RESET");
		st.init();
	}

	public static void setScreenTimeout(int timeout) {
		screen_timeout = timeout;
		PERCEIVED_THRESHOLD = timeout / 2;
		NOTIFICATION_DELAY_TIMEOUT = timeout / 2;
	}

	public boolean notifyPostedLockedToWear(StatusBarNotification sbn) {
		Slog.i(TAG, "notifyPostedLockedToWear " + sbn.getKey());
		boolean bDelayed = false;
		int state = 0;
		long postedTime = System.currentTimeMillis();
		SmartNotificationItem sni = null;

		if (!bEnabled) return false;

		st.postedNotificationCnt++;
		if (!isSendable(sbn)) {
			st.writeLog(sbn.getKey() + " NP S NOTSENDABLE");
			return false;
		}
		
		// deleteIfExist(removedPendingNotification, sbn.getKey());

		st.postedSendableNotificationCnt++;
		if (!bScreenOn) {
			st.writeLog(sbn.getKey() + " NP S SCROFF");
			return false;
		}

		String log = "";
		// now, screen on state
		
		if (postedTime < screenOnTimeMillis + (10 * 1000) && userActivityTimeMillis < screenOnTimeMillis) {
			// screen is recently turn on and recently screen is turned on
			// maybe notification app wakeup the display
			bDelayed = true;
			state = SmartNotificationItem.STATE_DELAYED;
			log = " NP D NOTIWAKEUP";	// notification delay(notification wakeup)
		}  if (pendingDirty) {
			bDelayed = true;
			state = SmartNotificationItem.STATE_MAY_PERCEIVED;
			log =  " NP D PENDINGD"; // notification delay(pending exist)
		} else if (postedTime < userActivityTimeMillis + PERCEIVED_THRESHOLD) {
			// recently, user used or pending notification exsist
			bDelayed = true;
			state = SmartNotificationItem.STATE_MAY_PERCEIVED;
			log = " NP D UACT"; // notification delay(user activity)
		} else {
			bDelayed = false;
			log = " NP S NUA";	// notification send (no user activity)
		}

		if (bDelayed) {
			pendingDirty = true;
			sni = new SmartNotificationItem(sbn, state);
			st.delayedNotificationCnt++;
			int idx = findIdx(postedDelayingNotification, sni.sbn.getKey());

			if (idx == -1) {
				// new delaying notification
				postedDelayingNotification.offer(sni);
				notifyDelayedToWear(sni);
			} else {
				// already delaying or perceived notification
				SmartNotificationItem item = postedDelayingNotification.get(idx);
				Slog.i(TAG, "notifyPostedLockedToWear : " + sbn.getKey() + " update");
				if (item.state == SmartNotificationItem.STATE_PERCEIVED) {
					// already perceived notification update, new NP delay
					// already counted to delayedCanceledNotification
					notifyDelayedToWear(sni);
					log += " APU";	// already perceived update
				} else {
					// not perceived delaying notification update
					// do nothing, just update.
					//mHandler.removeMessages(MESSAGE_SMARTNOTIFICATION, item);
					st.delayedUpdatedNotificationCnt++;	
					log += " DNU";		// delayed notification update
				}
				// update postedDelayingNotification list
				postedDelayingNotification.set(idx, sni);	// update queue
			}
			st.writeLog(sbn.getKey() + log);

			SmartNotificationItem removedItem = findItem(removedPendingNotification, sbn.getKey());
			if (removedItem != null) {
				// mustsendcancelifnotify 는 이미 전송된 알림에 대하여 cancel이 pending된것임, 한번은 가야함
				// 동일 알림이 발생해도 pendingCancel을 지우면 안됨
				if (removedItem.mustSendCancelIfNotNotify == false)
					deleteIfExist(removedPendingNotification, sbn.getKey());
			}
			st.writeLog(sbn.getKey() + log);
			return true;		// delaying
		}
		st.writeLog(sbn.getKey() + log);
		deleteIfExist(removedPendingNotification, sbn.getKey());
		return false;
	}

	public boolean notifyRemovedLockedToWear(StatusBarNotification sbn) {
		return notifyRemovedLockedToWear(sbn, -2);
	}

	public boolean notifyRemovedLockedToWear(StatusBarNotification sbn, int reason) {
		Slog.i(TAG, "notifyRemovedLockedToWear " + sbn.getKey() + " reason " + reason);
		SmartNotificationItem sbnItem;
		if (!bEnabled) return false;
		st.canceledNotificationCnt++;

		if (!isSendable(sbn) || reason == -2) {
			st.writeLog(sbn.getKey() + " NR S NOTSENDABLE");
			return false;
		}
		st.canceledSendableCancelCnt++;

		if (reason == 10 || reason == 11) {	// wear remove
			st.canceledByWear++;
			deleteIfExist(removedPendingNotification, sbn.getKey());
			return false;
		} else if (reason == 1) {
			st.canceledByUserClick++;
		} else if (reason == 2 || reason == 3) {
			st.canceledByUser++;
		} else if (reason == 8 || reason == 9) {
			st.canceledByApp++;
		} else {
			st.canceledByOther++; 
		}

		SmartNotificationItem prevItem = findItem(removedPendingNotification, sbn.getKey());

		Iterator<SmartNotificationItem> itr = postedDelayingNotification.iterator();
		while(itr.hasNext()) {
			sbnItem = itr.next();
			if (sbnItem.getKey().equals(sbn.getKey())) {

				Slog.i(TAG, "notifyRemovedLockedToWear : postedQueue removed " + sbnItem.getKey());
				if (sbnItem.state == SmartNotificationItem.STATE_DELAYED || sbnItem.state == SmartNotificationItem.STATE_MAY_PERCEIVED) {
					// delayed notification in smart notification manager
					st.delayedCanceledNotificationCnt++;

					if (reason == 1 || reason == 2 || reason == 3) {
						// naver occure..., (if user perceived a notification, it accounted in other perceiving routine.)
						st.delayedCanceledByUserConfirm++;
					} else if (reason == 8 || reason == 9) {
						st.delayedCanceledByApp++;
					} else {
						st.delayedCanceledByOther++;
					}
					st.writeLog(sbn.getKey() + " NR DC QRMV R=" + reason);
				} else if (sbnItem.state == SmartNotificationItem.STATE_PERCEIVED) {
					// perceived notificaiton
					// already canceled notification in smart notification manager by user perceived...
					/// st.delayedCanceldByUserPerceived...
					st.perceivedCanceled++;
					if (reason == 1 || reason == 2 || reason == 3) {
						st.perceivedCanceledByUserConfirm++;
					} else if (reason == 8 || reason == 9) {
						st.perceivedCanceledByApp++;
					} else if (reason == 10 || reason == 11 ) {
						st.perceivedCanceledByWear++;
					} else {
						st.delayedCanceledByOther++;
					}
					st.writeLog(sbn.getKey() + " NR PC ALREADY R=" + reason);
				}

				if (sbnItem.state != SmartNotificationItem.STATE_PERCEIVED) {
					mHandler.removeMessages(MESSAGE_SMARTNOTIFICATION, sbnItem);
				}

				itr.remove();

				if (!isExistDelayedNotification(postedDelayingNotification))
					pendingDirty = false;

				st.canceledRemoved++;
				return true;
			}
		}

		// While screen is off, do not delay
		if (!bScreenOn) {
			st.writeLog(sbn.getKey() + " NR S SCROFF R=" + reason);
			st.canceledNotDelayed++;
			return false;
		}

		// make pending NC...
		SmartNotificationItem item = new SmartNotificationItem(sbn, SmartNotificationItem.STATE_DELAYED);
		item.mustSendCancelIfNotNotify = true;

		if (prevItem != null) {
			int idx = findIdx(removedPendingNotification, prevItem.getKey());
			removedPendingNotification.set(idx, item);
		} else
			removedPendingNotification.offer(item);

		Slog.i(TAG, "notifyRemovedLockedToWear : removedQueue add " + sbn.getKey());
		st.writeLog(sbn.getKey() + " NR D SCRON R=" + reason);
		st.canceledDelayed++;

		return true;
	}

	private void notifyDelayedToWear(SmartNotificationItem sni) {
		Slog.i(TAG, "notifyDelayedToWear : " + sni.getKey());
		pendingDirty = true;
		Message msg = Message.obtain(mHandler, MESSAGE_SMARTNOTIFICATION);
		msg.arg1 = ARG1_SEND_NOTIFICATION;
		msg.obj = sni;
		mHandler.sendMessageDelayed(msg, NOTIFICATION_DELAY_TIMEOUT);
	}

	private void notifyAllpendingNotification(String reasonStr) {
		Iterator<SmartNotificationItem> itr;
		SmartNotificationItem sbnItem;
		Slog.i(TAG, "notifyAllpostedDelayingNotification() called");

		mHandler.removeMessages(MESSAGE_SMARTNOTIFICATION);	// cancel all delayed notificaiton message...

		itr = removedPendingNotification.iterator();
		while(itr.hasNext()) {
			SmartNotificationItem removeItem = itr.next();
			if (removeItem.mustSendCancelIfNotNotify) {
				SmartNotificationItem notifyItem = findItem(postedDelayingNotification, removeItem.getKey());
				if (notifyItem != null &&
						notifyItem.state != SmartNotificationItem.STATE_PERCEIVED) {
					// 보낼 노티피케이션 중 이전 cancel이 있으면 cancel은 안보내도 됨
					// ND NC : 이런 케이스 not exist
					itr.remove();
				}
			}
		}

		int postedDelayingNotificationSize = postedDelayingNotification.size();
		if (postedDelayingNotificationSize != 0) {
			st.writeLog("NAPN PSQS CNT=" + postedDelayingNotificationSize);
			itr = postedDelayingNotification.iterator();
			while(itr.hasNext()) {
				try {
					sbnItem = itr.next();
					if (sbnItem.state == SmartNotificationItem.STATE_DELAYED ||
						sbnItem.state == SmartNotificationItem.STATE_MAY_PERCEIVED) {
						Slog.i(TAG, "      notifyAllpendingNotification() : notify Post " + sbnItem.getKey());
						st.writeLog(sbnItem.getKey() + " NAPN PSQS R=" + reasonStr);
						nms.notifyPostedToWearLocked(sbnItem.sbn);
						st.delayedSendedNotificationCnt++;
						itr.remove();
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}

		int removedPendingNotificationSize = removedPendingNotification.size();
		if (removedPendingNotificationSize != 0) {
			st.canceledDelayedSendedAvgCnt = ((st.canceledDelayedSendedAvgCnt * st.canceledDelayedSended)
			 							+ removedPendingNotificationSize ) /
			 						 	(st.canceledDelayedSended + 1);
			st.canceledDelayedSended++;
			st.writeLog("NAPN PRQS CNT=" + removedPendingNotificationSize);
			itr = removedPendingNotification.iterator();
			while(itr.hasNext()) {
				try {
					sbnItem = itr.next();
					Slog.i(TAG, "      notifyAllpendingNotification() : notify Remove " + sbnItem.getKey());
					st.writeLog(sbnItem.getKey() + " NAPN PRQS R=" + reasonStr);
					nms.notifyRemovedToWearLocked(sbnItem.sbn);
					st.canceledDelayedSendedCnt++;
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
			removedPendingNotification.clear();
		}
		pendingDirty = false;
		if (reasonStr.equals("SCROFF")) {
			postedDelayingNotification.clear();
		}
	}
	/*
	private void sendNotification(SmartNotificationItem sni) {
		Slog.i(TAG, "      sendNotification : " + sni.getKey());
		nms.notifyPostedToWearLocked(sni.sbn);
		postedDelayingNotification.remove(sni);
		st.delayedSendedNotificationCnt++;
	}
	*/
	/*
	private void cancelNotification(SmartNotificationItem sni) {
		// not used
		Slog.i(TAG, "      cancelNotification : " + sni.getKey());
		nms.notifyRemovedToWearLocked(sni.sbn);
		removedPendingNotification.remove(sni);
	}
	*/

	public void handleMessage(Message msg) {
		switch (msg.arg1) {
			case ARG1_SEND_NOTIFICATION:
			/*
			// send only delayed timeout notification
			SmartNotificationItem sni = (SmartNotificationItem)msg.obj;
			Slog.i(TAG, "   ARG1_SEND_NOTIFICATION : " + sni.getKey());
			st.writeLog(sni.getKey() + " H PSQS R=TIMEOUT");
			sendNotification((SmartNotificationItem)msg.obj);
			*/
			// send all of delayed notification
			SmartNotificationItem sni = (SmartNotificationItem)msg.obj;
			Slog.i(TAG, "   ARG1_SEND_NOTIFICATION : " + sni.getKey());
			notifyAllpendingNotification("TIMOUT");

			break;
		}
		return;
	}

	private boolean isSendable(StatusBarNotification item) {
		String key = item.getKey();
		String pkg = item.getPackageName();
		boolean bOnGoing = item.isOngoing();
		boolean bClearable = item.isClearable();
 		//public static final int FLAG_LOCAL_ONLY         = 0x00000100;
		if ((item.getNotification().flags & 0x00000100) == 0x00000100)
			return false;
		
		if (bOnGoing|| bClearable == false ||
				key.contains("com.google.android.gms") ||
				key.contains("com.android.deskclock") ||
				key.contains("low_battery") ||
				pkg.equals("com.android.vending")||
				pkg.equals("com.android.providers.downloads") ||
				pkg.equals("android")||
				pkg.equals("com.android.dialer") ||
				pkg.equals("com.google.android.googlequicksearchbox")
				)
			return false;
	return true;
	}

	public SmartNotificationStatistics getStatistics() {
		return st;
	}

	public void sendStatisticsToApp() {
		Context context = nms.getContext();
		Intent intent = new Intent("kr.ac.snu.cares.smartnotification.app");
		intent.putExtra("type", "statistics");
		intent.putExtra("statistics", st.getBundle());

		context.sendBroadcastAsUser(intent, UserHandle.ALL);
		Slog.d(TAG, "send statistics");
	}
	public void sendStatusToApp() {
		Context context = nms.getContext();
		Intent intent = new Intent("kr.ac.snu.cares.smartnotification.app");
		intent.putExtra("type", "status");
		intent.putExtra("bEnabled", bEnabled);

		context.sendBroadcastAsUser(intent, UserHandle.ALL);
		Slog.d(TAG, "send status");
	}

	public void sendMsgToApp(String msg) {
		Context context = nms.getContext();
		Intent intent = new Intent("kr.ac.snu.cares.smartnotification.app");
		intent.putExtra("type", "msg");
		intent.putExtra("msg", msg);
		context.sendBroadcastAsUser(intent, UserHandle.ALL);
		Slog.d(TAG, "sendMsgToApp " + msg);
	}

		public void sendMsgToApp(String msg1, String msg2) {
		Context context = nms.getContext();
		Intent intent = new Intent("kr.ac.snu.cares.smartnotification.app");
		intent.putExtra("type", "msg2");
		intent.putExtra("msg1", msg2);
		intent.putExtra("msg2", msg2);
		context.sendBroadcastAsUser(intent, UserHandle.ALL);
		Slog.d(TAG, "sendMsgToApp " + msg1 + " / " + msg2);
	}


	public void writeLogToFile(String path) {
		Slog.i(TAG, "writeLogToFile() " + path);
		boolean result = st.writeLogToFile(path);
		if (result) {
			sendMsgToApp("writelog_success", path);
		} else {
			sendMsgToApp("writelog_failed");
		}
	}

	public void removeLogFile(String path) {
		Slog.i(TAG, "removeLogFile() " + path);
		try {
			File file = new File(path);
			file.delete();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void receiveBroadcast(Intent intent) {
		String type = intent.getStringExtra("type");
		Slog.i(TAG, "smartnoti broadcast " + type);
		if (type == null)
			return;

		if (type.equals("cmd")) {
			String cmd = intent.getStringExtra("cmd");
			Slog.i("LSP_SMN", "cmd : " + cmd);

			if (cmd.equals("snm_enable")) {
				setEnable();
			} else if (cmd.equals("snm_disable")) {
				setDisable();
			} else if (cmd.equals("snm_reset")) {
				reset();
			} else if (cmd.equals("snm_statistics")) {
				sendStatisticsToApp();
			} else if (cmd.equals("snm_status")) {
				sendStatusToApp();
			} else if (cmd.equals("snm_writelog")) {
				String path = intent.getStringExtra("path");
				writeLogToFile(path);
			} else if (cmd.equals("snm_removelogfile")) {
				String path = intent.getStringExtra("path");
				removeLogFile(path);
			} else if (cmd.equals("snm_enable_userActivity")) {
				bUserActivityEnabled = true;
			} else if (cmd.equals("snm_disable_userActivity")) {
				bUserActivityEnabled = false;
			}else {
				Slog.i("LSP", "unknown cmd " + cmd);
			}
		}
	}
}