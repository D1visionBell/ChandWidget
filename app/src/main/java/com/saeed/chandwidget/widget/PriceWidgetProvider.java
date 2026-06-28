package com.saeed.chandwidget.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;
import com.saeed.chandwidget.R;
import com.saeed.chandwidget.config.WidgetConfigActivity;
import com.saeed.chandwidget.data.PriceItem;
import com.saeed.chandwidget.data.PriceRegistry;
import com.saeed.chandwidget.util.Formatter;
import com.saeed.chandwidget.util.Prefs;

public class PriceWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_MANUAL_REFRESH = "com.saeed.chandwidget.ACTION_MANUAL_REFRESH";
    private static final String TAG = "PriceWidgetProvider";
    private static final int UPDATE_INTERVAL_MS = 30 * 60 * 1000;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            updateWidget(ctx, mgr, id);
        }
        scheduleAlarm(ctx);
        // FIX: Do NOT call triggerFetch() here.
        // onUpdate is called by the system (not user interaction), so Android 12+
        // blocks startForegroundService() with ForegroundServiceStartNotAllowedException.
        // Fetching is handled by the alarm (scheduleAlarm) and by BOOT_COMPLETED.
    }

    @Override
    public void onDeleted(Context ctx, int[] appWidgetIds) {
        for (int id : appWidgetIds) Prefs.removeWidget(ctx, id);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String action = intent.getAction();
        // ACTION_MANUAL_REFRESH and BOOT_COMPLETED are user/system events where
        // startForegroundService IS allowed.
        if (ACTION_MANUAL_REFRESH.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            triggerFetch(ctx);
        }
    }

    public static void updateWidget(Context ctx, AppWidgetManager mgr, int appWidgetId) {
        try {
            boolean persian = Prefs.isPersian(ctx);
            RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout);

            Intent cfgIntent = new Intent(ctx, WidgetConfigActivity.class);
            cfgIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            cfgIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            cfgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent cfgPi = PendingIntent.getActivity(
                    ctx, appWidgetId, cfgIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, cfgPi);

            int[] nameIds  = {R.id.name0,  R.id.name1,  R.id.name2};
            int[] symIds   = {R.id.sym0,   R.id.sym1,   R.id.sym2};
            int[] emojiIds = {R.id.emoji0, R.id.emoji1, R.id.emoji2};
            int[] chgIds   = {R.id.chg0,   R.id.chg1,   R.id.chg2};
            int[] priceIds = {R.id.price0, R.id.price1, R.id.price2};

            for (int slot = 0; slot < 3; slot++) {
                String key = Prefs.getSlot(ctx, appWidgetId, slot);
                PriceItem item = PriceRegistry.get(key);

                if (item == null) {
                    views.setTextViewText(priceIds[slot], "—");
                    views.setTextViewText(nameIds[slot],  "");
                    views.setTextViewText(symIds[slot],   "");
                    views.setTextViewText(emojiIds[slot], "");
                    views.setTextViewText(chgIds[slot],   "");
                    continue;
                }

                double price  = Prefs.getCachedPrice(ctx, key);
                double change = Prefs.getCachedChange(ctx, key);

                String nameStr  = persian ? item.getNameFa()   : item.getNameEn();
                String symStr   = persian ? item.getSymbolFa() : item.getSymbolEn();
                String priceStr = Formatter.formatPrice(price, item.getType(), persian);
                String chgStr   = Formatter.formatChange(change, item.getType(), persian);

                if (persian) {
                    priceStr = Formatter.toPersianDigits(priceStr);
                    chgStr   = Formatter.toPersianDigits(chgStr);
                }

                views.setTextViewText(emojiIds[slot], item.getEmoji());
                views.setTextViewText(nameIds[slot],  nameStr);
                views.setTextViewText(symIds[slot],   symStr);
                views.setTextViewText(priceIds[slot], priceStr);
                views.setTextViewText(chgIds[slot],   chgStr);

                int changeColor = change >= 0
                        ? Color.parseColor("#E53935")
                        : Color.parseColor("#43A047");
                views.setTextColor(chgIds[slot], changeColor);
            }

            long cacheTime = Prefs.getCacheTime(ctx);
            if (cacheTime > 0) {
                java.text.SimpleDateFormat sdf =
                        new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
                views.setTextViewText(R.id.update_time,
                        "آخرین: " + sdf.format(new java.util.Date(cacheTime)));
            } else {
                views.setTextViewText(R.id.update_time, "در حال بارگذاری...");
            }

            mgr.updateAppWidget(appWidgetId, views);

        } catch (Exception e) {
            Log.e(TAG, "updateWidget failed for id=" + appWidgetId, e);
        }
    }

    public static void updateAllWidgets(Context ctx) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            ComponentName cn = new ComponentName(ctx, PriceWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            for (int id : ids) updateWidget(ctx, mgr, id);
        } catch (Exception e) {
            Log.e(TAG, "updateAllWidgets failed", e);
        }
    }

    private static void scheduleAlarm(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(ctx, PriceWidgetProvider.class);
            i.setAction(ACTION_MANUAL_REFRESH);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS,
                    UPDATE_INTERVAL_MS, pi);
        } catch (Exception e) {
            Log.e(TAG, "scheduleAlarm failed", e);
        }
    }

    public static void triggerFetch(Context ctx) {
        try {
            Intent svc = new Intent(ctx, PriceUpdateService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(svc);
            } else {
                ctx.startService(svc);
            }
        } catch (Exception e) {
            Log.e(TAG, "triggerFetch failed", e);
        }
    }
}
