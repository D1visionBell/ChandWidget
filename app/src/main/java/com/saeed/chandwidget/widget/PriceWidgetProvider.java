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
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.RemoteViews;
import com.saeed.chandwidget.R;
import com.saeed.chandwidget.config.WidgetConfigActivity;
import com.saeed.chandwidget.data.PriceItem;
import com.saeed.chandwidget.data.PriceRegistry;
import com.saeed.chandwidget.util.Formatter;
import com.saeed.chandwidget.util.Prefs;

/** 3×3 widget — 3 price rows, large text */
public class PriceWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_MANUAL_REFRESH = "com.saeed.chandwidget.ACTION_MANUAL_REFRESH";
    static final int UPDATE_INTERVAL_MS = 30 * 60 * 1000;
    private static final String TAG = "Widget3x3";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
        scheduleAlarm(ctx);
    }

    @Override
    public void onDeleted(Context ctx, int[] appWidgetIds) {
        for (int id : appWidgetIds) Prefs.removeWidget(ctx, id);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String action = intent.getAction();
        if (ACTION_MANUAL_REFRESH.equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            triggerFetch(ctx);
            // Boot wipes all previously scheduled alarms, and relying on the OS
            // calling onUpdate() again for existing widgets after boot is not
            // guaranteed on every launcher/OEM — schedule explicitly here too.
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                scheduleAlarm(ctx);
            }
        }
    }

    public static void update(Context ctx, AppWidgetManager mgr, int appWidgetId) {
        try {
            boolean persian = Prefs.isPersian(ctx);
            RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout);

            Intent cfgIntent = new Intent(ctx, WidgetConfigActivity.class);
            cfgIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            cfgIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            cfgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent cfgPi = PendingIntent.getActivity(ctx, appWidgetId, cfgIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, cfgPi);

            int[] nameIds  = {R.id.name0,  R.id.name1,  R.id.name2};
            int[] symIds   = {R.id.sym0,   R.id.sym1,   R.id.sym2};
            int[] emojiIds = {R.id.emoji0, R.id.emoji1, R.id.emoji2};
            int[] chgIds   = {R.id.chg0,   R.id.chg1,   R.id.chg2};
            int[] priceIds = {R.id.price0, R.id.price1, R.id.price2};


            // Must set sizes in Java — Samsung launcher overrides XML textSize on RemoteViews update
            for (int i = 0; i < 3; i++) {
                views.setTextViewTextSize(emojiIds[i], TypedValue.COMPLEX_UNIT_SP, 22);
                views.setTextViewTextSize(nameIds[i],  TypedValue.COMPLEX_UNIT_SP, 15);
                views.setTextViewTextSize(symIds[i],   TypedValue.COMPLEX_UNIT_SP, 13);
                views.setTextViewTextSize(chgIds[i],   TypedValue.COMPLEX_UNIT_SP, 13);
                views.setTextViewTextSize(priceIds[i], TypedValue.COMPLEX_UNIT_SP, 24);
            }
            views.setTextViewTextSize(R.id.update_time, TypedValue.COMPLEX_UNIT_SP, 9);

            for (int slot = 0; slot < 3; slot++) {
                String key = Prefs.getSlot(ctx, appWidgetId, slot);
                PriceItem item = PriceRegistry.get(key);
                if (item == null) {
                    views.setTextViewText(priceIds[slot], "—");
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

                // The name column is a fixed-width box inside an explicitly
                // LTR row (so emoji-left/price-right stays put regardless of
                // language). With gravity="start", real Persian text — which
                // is right-to-left — ended up drawn against the LEFT edge of
                // that box instead of hugging its right edge, leaving a gap.
                // Flipping to END fixes that, BUT it must only happen for
                // names that are actually Persian script. A few entries (like
                // EOS) have no Farsi translation and keep their Latin ticker
                // as nameFa, so blindly flipping gravity for every row
                // whenever the app is in Persian mode pushed that Latin text
                // to the right edge too, making it look like it "moved
                // right" compared to the other rows. Basing the decision on
                // the actual text being shown (not just the app language)
                // fixes EOS without touching the rows that are genuinely
                // Persian.
                boolean rtlName = persian && Formatter.containsRtl(nameStr);
                int nameGravity = (rtlName ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
                views.setInt(nameIds[slot], "setGravity", nameGravity);

                views.setTextViewText(emojiIds[slot], item.getEmoji());
                views.setTextViewText(nameIds[slot],  nameStr);
                views.setTextViewText(symIds[slot],   symStr);
                views.setTextViewText(priceIds[slot], priceStr);
                views.setTextViewText(chgIds[slot],   chgStr);

                int changeColor = change >= 0 ? Color.parseColor("#E53935") : Color.parseColor("#43A047");
                views.setTextColor(chgIds[slot], changeColor);
            }

            long cacheTime = Prefs.getCacheTime(ctx);
            if (cacheTime > 0) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
                String time = sdf.format(new java.util.Date(cacheTime));
                if (persian) time = Formatter.toPersianDigits(time);
                views.setTextViewText(R.id.update_time, time);
            }

            mgr.updateAppWidget(appWidgetId, views);
        } catch (Exception e) {
            Log.e(TAG, "update failed id=" + appWidgetId, e);
        }
    }

    public static void updateAllWidgets(Context ctx) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            // 3x3
            for (int id : mgr.getAppWidgetIds(new ComponentName(ctx, PriceWidgetProvider.class)))
                update(ctx, mgr, id);
            // 2x2 single
            for (int id : mgr.getAppWidgetIds(new ComponentName(ctx, PriceWidgetProviderSingle.class)))
                PriceWidgetProviderSingle.update(ctx, mgr, id);
            // 2x2 3-row
            for (int id : mgr.getAppWidgetIds(new ComponentName(ctx, PriceWidgetProvider2x2.class)))
                PriceWidgetProvider2x2.update(ctx, mgr, id);
        } catch (Exception e) {
            Log.e(TAG, "updateAllWidgets failed", e);
        }
    }

    /**
     * Schedules the NEXT single refresh, UPDATE_INTERVAL_MS from now.
     *
     * This used to be a single setInexactRepeating(ELAPSED_REALTIME, ...) call.
     * That was the reason updates were unreliable:
     *  1. ELAPSED_REALTIME (not _WAKEUP) does not wake the CPU. If the phone is
     *     asleep, the alarm just waits until something else wakes the device,
     *     which on an idle phone can be hours.
     *  2. Even the _WAKEUP variant, when repeating and inexact, gets pushed to
     *     the next Doze "maintenance window" by the OS once the screen has
     *     been off for a while — so a nominal 30-minute period can silently
     *     stretch to 1-2 hours or more on stock Android, and far worse on
     *     Xiaomi/Samsung/Huawei, which apply extra background restrictions
     *     on top of AOSP Doze.
     *
     * setExactAndAllowWhileIdle + ELAPSED_REALTIME_WAKEUP is the documented way
     * to get an alarm that reliably fires close to its target time even in
     * Doze. The OS still rate-limits this API to roughly once every ~9 minutes
     * per app while idle, which is well under our 30-minute cadence, so it's
     * not throttled in practice.
     *
     * Instead of registering one repeating alarm up front, each firing
     * re-schedules the next one itself (see PriceUpdateService, which calls
     * this again once a fetch completes). That way, if the process was killed
     * or the schedule was otherwise lost, the very next successful fetch
     * re-establishes the chain rather than the whole thing quietly stopping.
     */
    public static void scheduleAlarm(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(ctx, PriceWidgetProvider.class);
            i.setAction(ACTION_MANUAL_REFRESH);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long triggerAt = SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS;

            // On Android 12+ setExactAndAllowWhileIdle() requires the
            // SCHEDULE_EXACT_ALARM permission, and on Android 13+ that
            // permission has to be granted by the user in Settings (it's not
            // automatic anymore). If it isn't granted, the exact call throws
            // a SecurityException — previously that exception was caught
            // below and the whole method just gave up, silently breaking the
            // 30-minute chain forever. Checking canScheduleExactAlarms() up
            // front and falling back to the inexact-but-still-wakeup variant
            // means updates keep happening (the OS may shift them by a few
            // minutes under Doze) instead of stopping completely.
            boolean canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (canBeExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                } else {
                    Log.w(TAG, "SCHEDULE_EXACT_ALARM not granted — falling back to inexact alarm");
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                }
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            }
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
