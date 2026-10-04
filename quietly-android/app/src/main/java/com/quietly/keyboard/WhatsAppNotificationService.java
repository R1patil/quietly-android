package com.quietly.keyboard;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class WhatsAppNotificationService extends NotificationListenerService {

    public static final String PREFS_NAME = "quietly_context";
    public static final String KEY_SENDER = "last_whatsapp_sender";
    public static final String KEY_TEXT = "last_whatsapp_text";
    public static final String KEY_TIME = "last_whatsapp_time";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName() == null) return;

        String pkg = sbn.getPackageName();
        // Support WhatsApp, WhatsApp Business, and Telegram
        if (pkg.equals("com.whatsapp") || pkg.equals("com.whatsapp.w4b") || pkg.equals("org.telegram.messenger")) {
            Notification n = sbn.getNotification();
            if (n == null || n.extras == null) return;

            Bundle extras = n.extras;
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);

            if (text != null && text.length() > 0) {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit()
                        .putString(KEY_SENDER, title != null ? title.toString() : "")
                        .putString(KEY_TEXT, text.toString())
                        .putLong(KEY_TIME, System.currentTimeMillis())
                        .apply();
            }
        }
    }

    public static String getRecentIncomingMessage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long time = prefs.getLong(KEY_TIME, 0);
        // If message arrived within the last 15 minutes, consider it relevant
        if (System.currentTimeMillis() - time < 15 * 60 * 1000) {
            String sender = prefs.getString(KEY_SENDER, "");
            String text = prefs.getString(KEY_TEXT, "");
            if (!text.isEmpty()) {
                return (sender.isEmpty() ? "" : sender + ": ") + text;
            }
        }
        return "";
    }
}
