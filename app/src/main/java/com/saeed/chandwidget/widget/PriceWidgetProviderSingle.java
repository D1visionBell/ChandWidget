package com.saeed.chandwidget.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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

public class PriceWidgetProviderSingle extends AppWidgetProvider {
    private static final String TAG = "WidgetSingle";

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
        // The config screen's "3 ردیف" switch (Prefs.getSmallWidgetMultiRow) was
        // being saved but never read here — this widget always rendered the
        // single-big-price layout regardless of what the switch said. When the
        // switch is on, reuse the exact 3-row renderer that the dedicated 2×2
        // "3 قیمت" widget already uses, since both fit the same 2×2 footprint.
        if (Prefs.getSmallWidgetMultiRow(ctx, appWidgetId)) {
            PriceWidgetProvider2x2.update(ctx, mgr, appWidgetId);
            return;
        }
        try {
            boolean persian = Prefs.isPersian(ctx);
            RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_layout_small);

            Intent cfgIntent = new Intent(ctx, WidgetConfigActivity.class);
            cfgIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            cfgIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            cfgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent cfgPi = PendingIntent.getActivity(ctx, appWidgetId, cfgIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, cfgPi);

            // Must set sizes in Java — Samsung launcher overrides XML textSize on each RemoteViews update
            views.setTextViewTextSize(R.id.emoji0, TypedValue.COMPLEX_UNIT_SP, 30);
            views.setTextViewTextSize(R.id.name0,  TypedValue.COMPLEX_UNIT_SP, 17);
            views.setTextViewTextSize(R.id.sym0,   TypedValue.COMPLEX_UNIT_SP, 13);
            views.setTextViewTextSize(R.id.chg0,   TypedValue.COMPLEX_UNIT_SP, 20);
            views.setTextViewTextSize(R.id.price0, TypedValue.COMPLEX_UNIT_SP, 42);
            views.setTextViewTextSize(R.id.update_time, TypedValue.COMPLEX_UNIT_SP, 9);

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

            // Same fix as the 4×2 widget: name0's box spans the full row width
            // (it's next to the emoji, not a narrow fixed column), so blindly
            // flipping to END for every row whenever the app is in Persian
            // mode is even more visible here — entries with no Farsi
            // translation (like EOS, which stores its Latin ticker as nameFa
            // too) would jump all the way to the far edge of the widget
            // instead of sitting next to the emoji like every other item.
            // Basing the gravity on the actual text being shown fixes that
            // without affecting genuinely Persian names/symbols.
            int nameGravity = Gravity.START | Gravity.CENTER_VERTICAL;
            int symGravity  = Gravity.START | Gravity.CENTER_VERTICAL;
            views.setInt(R.id.name0, "setGravity", nameGravity);
            views.setInt(R.id.sym0,  "setGravity", symGravity);

            views.setTextViewText(R.id.emoji0, item.getEmoji());
            views.setTextViewText(R.id.name0,  nameStr);
            views.setTextViewText(R.id.sym0,   symStr);
            views.setTextViewText(R.id.price0, priceStr);
            views.setTextViewText(R.id.chg0,   chgStr);

            int changeColor = change >= 0 ? Color.parseColor("#E53935") : Color.parseColor("#43A047");
            views.setTextColor(R.id.chg0, changeColor);

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
}
