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
import com.saeed.chandwidget.data.PriceRegistry;
import com.saeed.chandwidget.data.TgjuApi;
import com.saeed.chandwidget.util.Prefs;
import java.util.HashSet;
import java.util.Set;

public class PriceUpdateService extends Service {
    private static final String TAG = "PriceUpdateService";
    private static final String CHANNEL_ID = "chand_update";
    private static final int NOTIF_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());

        final int id = startId;
        new Thread(() -> {
            try {
                fetchAndStore();
                PriceWidgetProvider.updateAllWidgets(this);
            } catch (Exception e) {
                Log.e(TAG, "Error in update", e);
            } finally {
                stopSelf(id);
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void fetchAndStore() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);

        // Collect keys needed by all widget instances
        Set<String> keysToFetch = new HashSet<>();

        // Large widgets
        ComponentName cn = new ComponentName(this, PriceWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        for (int widgetId : ids) {
            for (int slot = 0; slot < 3; slot++) {
                String key = Prefs.getSlot(this, widgetId, slot);
                if (key != null && !key.isEmpty()) keysToFetch.add(key);
            }
        }

        // Small widgets
        ComponentName cnSmall = new ComponentName(this, PriceWidgetProviderSingle.class);
        int[] smallIds = mgr.getAppWidgetIds(cnSmall);
        for (int widgetId : smallIds) {
            for (int slot = 0; slot < 3; slot++) {
                String key = Prefs.getSlot(this, widgetId, slot);
                if (key != null && !key.isEmpty()) keysToFetch.add(key);
            }
        }

        // FIX: always fetch ALL registry keys so the price list in the app shows everything
        for (String key : PriceRegistry.ALL.keySet()) {
            keysToFetch.add(key);
        }

        for (String key : keysToFetch) {
            try {
                PriceData data = TgjuApi.fetch(key);
                if (data != null) {
                    Prefs.cachePrice(this, key, data.price, data.change);
                    Log.d(TAG, "Cached " + key + " = " + data.price + " chg=" + data.change);
                } else {
                    Log.w(TAG, "Failed to fetch: " + key);
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception fetching " + key, e);
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
            ch.enableVibration(false);
            ch.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Chand Widget")
                .setContentText("در حال بروزرسانی قیمت‌ها...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setOngoing(false)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
