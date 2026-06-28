package com.saeed.chandwidget.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.graphics.Color;
import android.os.SystemClock;
import com.saeed.chandwidget.R;
import com.saeed.chandwidget.data.PriceItem;
import com.saeed.chandwidget.data.PriceRegistry;
import com.saeed.chandwidget.util.Formatter;
import com.saeed.chandwidget.util.Prefs;

public class PriceWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_MANUAL_REFRESH = "com.saeed.chandwidget.ACTION_MANUAL_REFRESH";
    private static final int UPDATE_INTERVAL_MS = 30 * 60 * 1000; // 30 min

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            updateWidget(ctx, mgr, id);
        }
        scheduleAlarm(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String action = intent.getAction();
        if (ACTION_MANUAL_REFRESH.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            triggerFetch(ctx);
        }
    }

    public static void updateWidget(Context ctx, AppWidgetManager mgr, int appWidgetId) {
        boolean persian = Prefs.isPersian(ctx);

        RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout);

        // Setup manual refresh on widget click
        Intent refreshIntent = new Intent(ctx, PriceWidgetProvider.class);
        refreshIntent.setAction(ACTION_MANUAL_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pi);

        // Fill 3 slots
        int[] cardIds = {R.id.card0, R.id.card1, R.id.card2};
        int[] emojiIds = {R.id.emoji0, R.id.emoji1, R.id.emoji2};
        int[] nameIds  = {R.id.name0,  R.id.name1,  R.id.name2};
        int[] symIds   = {R.id.sym0,   R.id.sym1,   R.id.sym2};
        int[] chgIds   = {R.id.chg0,   R.id.chg1,   R.id.chg2};
        int[] priceIds = {R.id.price0, R.id.price1, R.id.price2};

        for (int slot = 0; slot < 3; slot++) {
            String key = Prefs.getSlot(ctx, slot);
            PriceItem item = PriceRegistry.get(key);
            if (item == null) continue;

            double price  = Prefs.getCachedPrice(ctx, key);
            double change = Prefs.getCachedChange(ctx, key);

            String nameStr  = persian ? item.getNameFa()   : item.getNameEn();
            String symStr   = persian ? item.getSymbolFa()  : item.getSymbolEn();
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

            // Color for change
            int changeColor = change >= 0
                    ? Color.parseColor("#E53935")   // red = rise (Iranian market: red = up)
                    : Color.parseColor("#43A047");   // green = drop
            // Actually: let's match Chand app which uses red for positive
            // But you can flip with "Price Change Color" setting later
            // For now: positive = red (↑), negative = green (↓)
            views.setTextColor(chgIds[slot], changeColor);
        }

        // Last update time
        long cacheTime = Prefs.getCacheTime(ctx);
        if (cacheTime > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
            String timeStr = sdf.format(new java.util.Date(cacheTime));
            views.setTextViewText(R.id.update_time, timeStr);
        }

        mgr.updateAppWidget(appWidgetId, views);
    }

    private static void scheduleAlarm(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, PriceWidgetProvider.class);
        i.setAction(ACTION_MANUAL_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS,
                UPDATE_INTERVAL_MS, pi);
    }

    private static void triggerFetch(Context ctx) {
        Intent svc = new Intent(ctx, PriceUpdateService.class);
        ctx.startService(svc);
    }
}
