package com.saeed.chandwidget.util;

import com.saeed.chandwidget.data.PriceItem;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class Formatter {

    private static final DecimalFormatSymbols EN = new DecimalFormatSymbols(Locale.US);
    private static final DecimalFormatSymbols FA;

    static {
        FA = new DecimalFormatSymbols(new Locale("fa", "IR"));
        FA.setGroupingSeparator('،');
        FA.setDecimalSeparator('.');
    }

    public static String formatPrice(double price, PriceItem.PriceType type, boolean persian) {
        switch (type) {
            case CURRENCY_TOMAN: return formatToman(price, persian);
            case GOLD_TOMAN:     return formatGoldToman(price, persian);
            case CRYPTO_USD:
            case GOLD_USD:       return formatCryptoUsd(price, persian);
            default:             return String.valueOf((long) price);
        }
    }

    private static String formatToman(double price, boolean persian) {
        DecimalFormat df = new DecimalFormat("#,###", persian ? FA : EN);
        return df.format((long) price);
    }

    private static String formatGoldToman(double price, boolean persian) {
        double million = price / 1_000_000.0;
        if (million >= 1.0) {
            DecimalFormat df = new DecimalFormat("#,##0.00", persian ? FA : EN);
            return df.format(million) + (persian ? "م.ت" : "M");
        } else {
            DecimalFormat df = new DecimalFormat("#,##0.0", persian ? FA : EN);
            return df.format(price / 1000.0) + (persian ? "ه.ت" : "K");
        }
    }

    private static String formatCryptoUsd(double price, boolean persian) {
        if (price >= 1000) {
            DecimalFormat df = new DecimalFormat("#,##0", persian ? FA : EN);
            return "$" + df.format((long) price);
        } else if (price >= 1) {
            DecimalFormat df = new DecimalFormat("#,##0.00", persian ? FA : EN);
            return "$" + df.format(price);
        } else {
            DecimalFormat df = new DecimalFormat("0.0000", persian ? FA : EN);
            return "$" + df.format(price);
        }
    }

    public static String formatChange(double change, PriceItem.PriceType type, boolean persian) {
        if (change == 0) return "—";
        String sign = change > 0 ? "↑" : "↓";
        double abs = Math.abs(change);
        String val;
        switch (type) {
            case GOLD_TOMAN:
            case CURRENCY_TOMAN:
                if (abs >= 1_000_000)     val = fmt(abs / 1_000_000) + "M";
                else if (abs >= 1000)      val = fmt(abs / 1000) + "K";
                else                       val = fmt(abs);
                break;
            case CRYPTO_USD:
            case GOLD_USD:
                if (abs >= 1000)           val = "$" + fmt(abs / 1000) + "K";
                else                       val = "$" + fmt(abs);
                break;
            default:
                val = fmt(abs);
        }
        return sign + val;
    }

    private static String fmt(double v) {
        return new DecimalFormat("#,##0.##", EN).format(v);
    }

    /**
     * True if the string contains any Arabic/Persian-script letters.
     *
     * Used to decide per-item text alignment instead of trusting the app's
     * overall Persian/English mode: a few registry entries (e.g. EOS) have no
     * Farsi translation and keep their Latin ticker in nameFa, so checking
     * the app language alone isn't enough to know whether a given label
     * actually needs right-to-left alignment.
     */
    public static boolean containsRtl(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0x0590 && c <= 0x08FF) || (c >= 0xFB1D && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF)) {
                return true;
            }
        }
        return false;
    }

    public static String toPersianDigits(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') sb.append((char)(c - '0' + '\u06F0'));
            else sb.append(c);
        }
        return sb.toString();
    }

    /**
     * A compact label for narrow widget rows (2×2 3-row).
     *
     * In English mode this is just the 3-letter code (USD, EUR, ...), same as
     * before — that's already as compact as it gets and is a recognized
     * standard, so there's no real gain from showing the full name there.
     *
     * In Persian mode, the old code also fell back to the Latin code ("USD")
     * to avoid squeezing long Farsi names like "نیم سکه" into a fixed-width
     * column. That's a legitimate rendering constraint — the widget is only
     * ~110dp — but showing Latin abbreviations in an otherwise all-Farsi UI
     * reads as inconsistent, not "abbreviated". Gold/coin items already have
     * a short, correct Farsi word in symbolFa ("طلا", "سکه", "نیم", "ربع").
     * Currencies didn't have an equivalent short Farsi form, so this takes
     * the first word of the full Farsi name ("دلار آمریکا" → "دلار",
     * "پوند انگلیس" → "پوند"), which is how a person would actually
     * abbreviate it, rather than switching alphabets.
     *
     * Note this doesn't create full-name-fits-in-3-rows: it swaps one
     * abbreviation for a better one. Two currencies sharing a first word
     * (e.g. "دلار آمریکا" / "دلار استرالیا" both start with "دلار") will look
     * the same here; the flag emoji next to it is what disambiguates them.
     */
    public static String shortLabel(PriceItem item, boolean persian) {
        if (!persian) return item.getSymbolEn();
        String fa = item.getSymbolFa();
        String name = item.getNameFa();
        if (fa != null && !fa.equals(item.getSymbolEn())) return fa; // already a real Farsi word (gold/coins)
        if (name == null || name.isEmpty()) return fa;
        int sp = name.indexOf(' ');
        return sp > 0 ? name.substring(0, sp) : name;
    }
}
