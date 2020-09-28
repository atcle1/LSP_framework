package com.android.server.notification;
import android.service.notification.StatusBarNotification;

class SmartNotificationItem {
	public static final int STATE_DELAYED = 1;
	public static final int STATE_MAY_PERCEIVED = 2;
	public static final int STATE_PERCEIVED = 3;

	// state is not object identifier, only sbn !
	public StatusBarNotification sbn;
	public int state;
	public boolean mustSendCancelIfNotNotify;

	public SmartNotificationItem(StatusBarNotification sbn, int state) {
		this.sbn = sbn;
		this.state = state;
		mustSendCancelIfNotNotify = false;
	}
	public String getKey() {
		return sbn.getKey();
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SmartNotificationItem other = (SmartNotificationItem) obj;
		return sbn.getKey().equals(other.sbn.getKey());
	}

	@Override
	public int hashCode() {
		return sbn.getKey().hashCode();
    }
}