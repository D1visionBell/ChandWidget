package com.saeed.chandwidget.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;
import com.saeed.chandwidget.R;
import com.saeed.chandwidget.config.WidgetConfigActivity;
import com.saeed.chandwidget.data.PriceItem;
import com.saeed.chandwidget.data.PriceRegistry;
import com.saeed.chandwidget.util.Formatter;
import com.saeed.chandwidget.util.Prefs;

/** 2×2 widget — shows 3 prices compact */
public class PriceWidgetProvider2x2 extends AppWidgetProvider {
    private static final String TAG = "Widget2x2";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
        PriceWidgetProvider.scheduleAlarm(ctx);
    }

    @Override
    public void onDeleted(Context ctx, int[] appWidgetIds) {
        for (int id : appWidgetIds) Prefs.removeWidget(ctx, id);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (PriceWidgetProvider.ACTION_MANUAL_REFRESH.equals(intent.getAction())
                || Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            PriceWidgetProvider.triggerFetch(ctx);
        }
    }

    public static void update(Context ctx, AppWidgetManager mgr, int appWidgetId) {
        try {
            boolean persian = Prefs.isPersian(ctx);
            RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout_2x2);

            Intent cfgIntent = new Intent(ctx, WidgetConfigActivity.class);
            cfgIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            cfgIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            cfgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent cfgPi = PendingIntent.getActivity(ctx, appWidgetId, cfgIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, cfgPi);

            int[] emojiIds = {R.id.emoji0, R.id.emoji1, R.id.emoji2};
            int[] symIds   = {R.id.sym0,   R.id.sym1,   R.id.sym2};
            int[] chgIds   = {R.id.chg0,   R.id.chg1,   R.id.chg2};
            int[] priceIds = {R.id.price0, R.id.price1, R.id.price2};

            // CRITICAL: set sizes in Java to prevent Samsung launcher from shrinking them
            for (int i = 0; i < 3; i++) {
                views.setTextViewTextSize(emojiIds[i], TypedValue.COMPLEX_UNIT_SP, 20);
                views.setTextViewTextSize(symIds[i],   TypedValue.COMPLEX_UNIT_SP, 14);
                views.setTextViewTextSize(chgIds[i],   TypedValue.COMPLEX_UNIT_SP, 12);
                views.setTextViewTextSize(priceIds[i], TypedValue.COMPLEX_UNIT_SP, 19);
            }

            for (int slot = 0; slot < 3; slot++) {
                String key = Prefs.getSlot(ctx, appWidgetId, slot);
                PriceItem item = PriceRegistry.get(key);
                if (item == null) {
                    views.setTextViewText(priceIds[slot], "—");
                    views.setTextViewText(symIds[slot],   "");
                    views.setTextViewText(emojiIds[slot], "");
                    views.setTextViewText(chgIds[slot],   "");
                    continue;
                }

                double price  = Prefs.getCachedPrice(ctx, key);
                double change = Prefs.getCachedChange(ctx, key);

                String symStr   = persian ? item.getSymbolFa() : item.getSymbolEn();
                String priceStr = Formatter.formatPrice(price, item.getType(), persian);
                String chgStr   = Formatter.formatChange(change, item.getType(), persian);

                if (persian) {
                    priceStr = Formatter.toPersianDigits(priceStr);
                    chgStr   = Formatter.toPersianDigits(chgStr);
                }

                views.setTextViewText(emojiIds[slot], item.getEmoji());
                views.setTextViewText(symIds[slot],   symStr);
                views.setTextViewText(priceIds[slot], priceStr);
                views.setTextViewText(chgIds[slot],   chgStr);

                int changeColor = change >= 0 ? Color.parseColor("#E53935") : Color.parseColor("#43A047");
                views.setTextColor(chgIds[slot], changeColor);
            }

            mgr.updateAppWidget(appWidgetId, views);
        } catch (Exception e) {
            Log.e(TAG, "update failed id=" + appWidgetId, e);
        }
    }
}
