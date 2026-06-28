package com.saeed.chandwidget.data;

public class PriceItem {
    // Currencies — use /v1/market/tmp endpoint
    public static final String KEY_USD    = "price_dollar_rl";
    public static final String KEY_EUR    = "price_eur";
    public static final String KEY_GBP    = "price_gbp";
    public static final String KEY_AED    = "price_aed";
    public static final String KEY_TRY    = "price_try";
    public static final String KEY_CHF    = "price_chf";
    public static final String KEY_AUD    = "price_aud";
    public static final String KEY_CAD    = "price_cad";
    public static final String KEY_JPY    = "price_jpy";
    public static final String KEY_CNY    = "price_cny";
    // Gold — use /v1/market/tmp endpoint
    public static final String KEY_GOLD18 = "geram18";
    public static final String KEY_SEKKE  = "sekeb";   // سکه بهار آزادی
    public static final String KEY_NIM    = "nim";     // نیم سکه
    public static final String KEY_ROB    = "rob";     // ربع سکه
    // Crypto — use /v1/widget/tmp endpoint (name field)
    public static final String KEY_BTC    = "crypto-bitcoin";
    public static final String KEY_ETH    = "crypto-ethereum";
    public static final String KEY_LTC    = "crypto-litecoin";
    public static final String KEY_BNB    = "crypto-binance-coin";
    public static final String KEY_BCH    = "crypto-bitcoin-cash";
    public static final String KEY_EOS    = "crypto-eos";

    public enum PriceType {
        CURRENCY_TOMAN,
        GOLD_TOMAN,
        CRYPTO_USD,
    }

    private final String key;
    private final String nameEn;
    private final String nameFa;
    private final String symbolEn;
    private final String symbolFa;
    private final String emoji;
    private final PriceType type;

    public PriceItem(String key, String nameEn, String nameFa,
                     String symbolEn, String symbolFa, String emoji, PriceType type) {
        this.key = key;
        this.nameEn = nameEn;
        this.nameFa = nameFa;
        this.symbolEn = symbolEn;
        this.symbolFa = symbolFa;
        this.emoji = emoji;
        this.type = type;
    }

    public String getKey()      { return key; }
    public String getNameEn()   { return nameEn; }
    public String getNameFa()   { return nameFa; }
    public String getSymbolEn() { return symbolEn; }
    public String getSymbolFa() { return symbolFa; }
    public String getEmoji()    { return emoji; }
    public PriceType getType()  { return type; }
}
