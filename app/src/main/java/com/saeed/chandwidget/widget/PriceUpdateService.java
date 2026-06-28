package com.saeed.chandwidget.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.saeed.chandwidget.R;
import com.saeed.chandwidget.data.PriceData;
import com.saeed.chandwidget.data.TgjuApi;
import com.saeed.chandwidget.util.Prefs;
import java.util.HashSet;
import java.util.Set;

public class PriceUpdateService extends Service {
    private static final String TAG = "PriceUpdateService";
    private static final String CHANNEL_ID = "chand_update";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // FIX: must call startForeground immediately on Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(1, buildNotification());
        }

        new Thread(() -> {
            try {
                fetchAndStore();
                PriceWidgetProvider.updateAllWidgets(this);
            } catch (Exception e) {
                Log.e(TAG, "Error in update", e);
            } finally {
                stopSelf(startId);
            }
        }).start();
        return START_NOT_STICKY;
    }

    private void fetchAndStore() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        ComponentName cn = new ComponentName(this, PriceWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);

        Set<String> keysToFetch = new HashSet<>();
        for (int id : ids) {
            for (int slot = 0; slot < 3; slot++) {
                // FIX: use per-instance slot keys
                keysToFetch.add(Prefs.getSlot(this, id, slot));
            }
        }
        // Fallback defaults if no widgets yet
        if (keysToFetch.isEmpty()) {
            keysToFetch.add(Prefs.DEFAULT_SLOT0);
            keysToFetch.add(Prefs.DEFAULT_SLOT1);
            keysToFetch.add(Prefs.DEFAULT_SLOT2);
        }

        for (String key : keysToFetch) {
            PriceData data = TgjuApi.fetch(key);
            if (data != null) {
                Prefs.cachePrice(this, key, data.price, data.change);
                Log.d(TAG, "Cached " + key + " = " + data.price + " chg=" + data.change);
            } else {
                Log.w(TAG, "Failed to fetch: " + key);
            }
        }
        Prefs.setCacheTime(this, System.currentTimeMillis());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Widget Update", NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Fetching price data");
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Chand Widget")
                .setContentText("Updating prices...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
