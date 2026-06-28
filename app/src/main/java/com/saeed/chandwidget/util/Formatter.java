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
            case CRYPTO_USD:     return formatCryptoUsd(price, persian);
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

    public static String toPersianDigits(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') sb.append((char)(c - '0' + '\u06F0'));
            else sb.append(c);
        }
        return sb.toString();
    }
}
