package com.saeed.chandwidget.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.saeed.chandwidget.data.PriceItem;

public class Prefs {
    private static final String PREF_NAME  = "chand_widget_prefs";
    private static final String KEY_LANG   = "lang_persian";
    private static final String SLOT_PREFIX = "slot_";

    // Defaults
    public static final String DEFAULT_SLOT0 = PriceItem.KEY_USD;
    public static final String DEFAULT_SLOT1 = PriceItem.KEY_EUR;
    public static final String DEFAULT_SLOT2 = PriceItem.KEY_GOLD18;

    public static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ── Language (global, not per-widget) ────────────────────────────────────
    public static boolean isPersian(Context ctx) {
        return get(ctx).getBoolean(KEY_LANG, false);
    }

    public static void setLanguage(Context ctx, boolean persian) {
        get(ctx).edit().putBoolean(KEY_LANG, persian).apply();
    }

    // ── Slots per widget instance ─────────────────────────────────────────────
    private static String slotKey(int appWidgetId, int slot) {
        return SLOT_PREFIX + appWidgetId + "_" + slot;
    }

    public static String getSlot(Context ctx, int appWidgetId, int slot) {
        String def = slot == 0 ? DEFAULT_SLOT0 : (slot == 1 ? DEFAULT_SLOT1 : DEFAULT_SLOT2);
        return get(ctx).getString(slotKey(appWidgetId, slot), def);
    }

    public static void setSlot(Context ctx, int appWidgetId, int slot, String key) {
        get(ctx).edit().putString(slotKey(appWidgetId, slot), key).apply();
    }

    public static void removeWidget(Context ctx, int appWidgetId) {
        SharedPreferences.Editor ed = get(ctx).edit();
        for (int i = 0; i < 3; i++) ed.remove(slotKey(appWidgetId, i));
        ed.apply();
    }

    // ── Price cache (global by key) ───────────────────────────────────────────
    private static final String CACHE_PRICE  = "cache_price_";
    private static final String CACHE_CHANGE = "cache_change_";
    private static final String CACHE_TIME   = "cache_time";

    public static void cachePrice(Context ctx, String key, double price, double change) {
        get(ctx).edit()
                .putString(CACHE_PRICE + key, String.valueOf(price))
                .putString(CACHE_CHANGE + key, String.valueOf(change))
                .apply();
    }

    public static void setCacheTime(Context ctx, long millis) {
        get(ctx).edit().putLong(CACHE_TIME, millis).apply();
    }

    public static long getCacheTime(Context ctx) {
        return get(ctx).getLong(CACHE_TIME, 0);
    }

    public static double getCachedPrice(Context ctx, String key) {
        String v = get(ctx).getString(CACHE_PRICE + key, "0");
        try { return Double.parseDouble(v); } catch (Exception e) { return 0; }
    }

    public static double getCachedChange(Context ctx, String key) {
        String v = get(ctx).getString(CACHE_CHANGE + key, "0");
        try { return Double.parseDouble(v); } catch (Exception e) { return 0; }
    }
}
