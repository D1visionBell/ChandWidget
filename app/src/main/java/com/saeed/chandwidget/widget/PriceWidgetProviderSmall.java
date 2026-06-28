package com.saeed.chandwidget.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;
import android.widget.RemoteViews;
import com.saeed.chandwidget.R;
import com.saeed.chandwidget.config.WidgetConfigActivity;
import com.saeed.chandwidget.data.PriceItem;
import com.saeed.chandwidget.data.PriceRegistry;
import com.saeed.chandwidget.util.Formatter;
import com.saeed.chandwidget.util.Prefs;

public class PriceWidgetProviderSmall extends PriceWidgetProvider {

    private static final String TAG = "PriceWidgetSmall";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateSmallWidget(ctx, mgr, id);
        scheduleAlarm(ctx); // reuse parent alarm — it triggers updateAllWidgets which handles small too
    }

    @Override
    public void onDeleted(Context ctx, int[] appWidgetIds) {
        for (int id : appWidgetIds) Prefs.removeWidget(ctx, id);
    }

    public static void updateSmallWidget(Context ctx, AppWidgetManager mgr, int appWidgetId) {
        try {
            boolean persian = Prefs.isPersian(ctx);
            RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout_small);

            // Click → open config
            Intent cfgIntent = new Intent(ctx, WidgetConfigActivity.class);
            cfgIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            cfgIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            cfgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent cfgPi = PendingIntent.getActivity(
                    ctx, appWidgetId, cfgIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, cfgPi);

            String key = Prefs.getSlot(ctx, appWidgetId, 0);
            PriceItem item = PriceRegistry.get(key);

            if (item == null) {
                views.setTextViewText(R.id.price0, "—");
                views.setTextViewText(R.id.name0,  "");
                views.setTextViewText(R.id.sym0,   "");
                views.setTextViewText(R.id.emoji0, "");
                views.setTextViewText(R.id.chg0,   "");
                mgr.updateAppWidget(appWidgetId, views);
                return;
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

            views.setTextViewText(R.id.emoji0, item.getEmoji());
            views.setTextViewText(R.id.name0,  nameStr);
            views.setTextViewText(R.id.sym0,   symStr);
            views.setTextViewText(R.id.price0, priceStr);
            views.setTextViewText(R.id.chg0,   chgStr);

            int changeColor = change >= 0
                    ? Color.parseColor("#E53935")
                    : Color.parseColor("#43A047");
            views.setTextColor(R.id.chg0, changeColor);

            mgr.updateAppWidget(appWidgetId, views);

        } catch (Exception e) {
            Log.e(TAG, "updateSmallWidget failed for id=" + appWidgetId, e);
        }
    }
}
