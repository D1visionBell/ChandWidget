package com.saeed.chandwidget.widget;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import com.saeed.chandwidget.data.PriceData;
import com.saeed.chandwidget.data.TgjuApi;
import com.saeed.chandwidget.util.Prefs;
import java.util.HashSet;
import java.util.Set;

public class PriceUpdateService extends Service {
    private static final String TAG = "PriceUpdateService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(() -> {
            try {
                fetchAndStore();
                updateWidgets();
            } catch (Exception e) {
                Log.e(TAG, "Error in update", e);
            } finally {
                stopSelf(startId);
            }
        }).start();
        return START_NOT_STICKY;
    }

    private void fetchAndStore() {
        // Collect unique keys from all widget instances
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        ComponentName cn = new ComponentName(this, PriceWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);

        Set<String> keysToFetch = new HashSet<>();
        for (int id : ids) {
            for (int slot = 0; slot < 3; slot++) {
                keysToFetch.add(Prefs.getSlot(this, slot));
            }
        }
        // Also fetch current default keys even if no widget instance yet
        keysToFetch.add(Prefs.getSlot(this, 0));
        keysToFetch.add(Prefs.getSlot(this, 1));
        keysToFetch.add(Prefs.getSlot(this, 2));

        for (String key : keysToFetch) {
            PriceData data = TgjuApi.fetch(key);
            if (data != null) {
                Prefs.cachePrice(this, key, data.price, data.change);
                Log.d(TAG, "Cached " + key + " = " + data.price);
            }
        }
        Prefs.setCacheTime(this, System.currentTimeMillis());
    }

    private void updateWidgets() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        ComponentName cn = new ComponentName(this, PriceWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        if (ids.length > 0) {
            Intent updateIntent = new Intent(this, PriceWidgetProvider.class);
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            sendBroadcast(updateIntent);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
