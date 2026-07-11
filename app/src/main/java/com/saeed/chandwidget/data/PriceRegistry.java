package com.saeed.chandwidget.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PriceRegistry {

    public static final Map<String, PriceItem> ALL = new LinkedHashMap<>();

    static {
        // Currencies
        add(PriceItem.KEY_USD,    "US Dollar",        "دلار آمریکا",     "USD", "USD",  "🇺🇸", PriceItem.PriceType.CURRENCY_TOMAN);
        add(PriceItem.KEY_EUR,    "Euro",             "یورو",            "EUR", "EUR",  "🇪🇺", PriceItem.PriceType.CURRENCY_TOMAN);
        add(PriceItem.KEY_GBP,    "British Pound",    "پوند انگلیس",    "GBP", "GBP",  "🇬🇧", PriceItem.PriceType.CURRENCY_TOMAN);
        add(PriceItem.KEY_AED,    "UAE Dirham",       "درهم امارات",    "AED", "AED",  "🇦🇪", PriceItem.PriceType.CURRENCY_TOMAN);
        add(PriceItem.KEY_TRY,    "Turkish Lira",     "لیر ترکیه",      "TRY", "TRY",  "🇹🇷", PriceItem.PriceType.CURRENCY_TOMAN);
        add(PriceItem.KEY_CHF,    "Swiss Franc",      "فرانک سوئیس",   "CHF", "CHF",  "🇨🇭", PriceItem.PriceType.CURRENCY_TOMAN);
        add(PriceItem.KEY_AUD,    "Australian Dollar","دلار استرالیا",  "AUD", "AUD",  "🇦🇺", PriceItem.PriceType.CURRENCY_TOMAN);
        add(PriceItem.KEY_CAD,    "Canadian Dollar",  "دلار کانادا",    "CAD", "CAD",  "🇨🇦", PriceItem.PriceType.CURRENCY_TOMAN);
        add(PriceItem.KEY_JPY,    "Japanese Yen",     "ین ژاپن",        "JPY", "JPY",  "🇯🇵", PriceItem.PriceType.CURRENCY_TOMAN);
        add(PriceItem.KEY_CNY,    "Chinese Yuan",     "یوان چین",       "CNY", "CNY",  "🇨🇳", PriceItem.PriceType.CURRENCY_TOMAN);
        // Gold
        add(PriceItem.KEY_GOLD18, "Gold 18K",         "طلا ۱۸ عیار",   "GOLD", "طلا", "🥇", PriceItem.PriceType.GOLD_TOMAN);
        add(PriceItem.KEY_SEKKE,  "Emami Coin",       "سکه امامی",     "COIN", "سکه", "🪙", PriceItem.PriceType.GOLD_TOMAN);
        add(PriceItem.KEY_NIM,    "Half Coin",        "نیم سکه",       "HALF", "نیم", "🪙", PriceItem.PriceType.GOLD_TOMAN);
        add(PriceItem.KEY_ROB,    "Quarter Coin",     "ربع سکه",       "QTR",  "ربع", "🪙", PriceItem.PriceType.GOLD_TOMAN);
        add(PriceItem.KEY_GOLD_OUNCE, "Gold Ounce",   "اونس طلا",      "XAU",  "اونس", "🥇", PriceItem.PriceType.GOLD_USD);
        // Crypto
        add(PriceItem.KEY_BTC,    "Bitcoin",          "بیتکوین",        "BTC",  "BTC", "₿",  PriceItem.PriceType.CRYPTO_USD);
        add(PriceItem.KEY_ETH,    "Ethereum",         "اتریوم",         "ETH",  "ETH", "Ξ",  PriceItem.PriceType.CRYPTO_USD);
        add(PriceItem.KEY_LTC,    "Litecoin",         "لایت کوین",      "LTC",  "LTC", "Ł",  PriceItem.PriceType.CRYPTO_USD);
        add(PriceItem.KEY_BNB,    "Binance",          "بایننس",         "BNB",  "BNB", "🔶", PriceItem.PriceType.CRYPTO_USD);
        add(PriceItem.KEY_BCH,    "Bitcoin Cash",     "بیتکوین کش",    "BCH",  "BCH", "💠", PriceItem.PriceType.CRYPTO_USD);
        add(PriceItem.KEY_EOS,    "EOS",              "EOS",            "EOS",  "EOS", "⚫", PriceItem.PriceType.CRYPTO_USD);
        add(PriceItem.KEY_SOL,    "Solana",           "سولانا",         "SOL",  "SOL", "◎",  PriceItem.PriceType.CRYPTO_USD);
        add(PriceItem.KEY_XRP,    "XRP",              "ریپل",           "XRP",  "XRP", "✕",  PriceItem.PriceType.CRYPTO_USD);
        add(PriceItem.KEY_TRX,    "Tron",             "ترون",           "TRX",  "TRX", "🔺", PriceItem.PriceType.CRYPTO_USD);
        add(PriceItem.KEY_DOGE,   "Dogecoin",         "دوج کوین",      "DOGE", "DOGE","🐕", PriceItem.PriceType.CRYPTO_USD);
        add(PriceItem.KEY_USDT,   "Tether",           "تتر",            "USDT", "USDT","🟢", PriceItem.PriceType.CURRENCY_TOMAN);
    }

    private static void add(String key, String en, String fa,
                             String symEn, String symFa, String emoji,
                             PriceItem.PriceType type) {
        ALL.put(key, new PriceItem(key, en, fa, symEn, symFa, emoji, type));
    }

    public static List<PriceItem> getAll() {
        return new ArrayList<>(ALL.values());
    }

    public static PriceItem get(String key) {
        return ALL.get(key);
    }
}
