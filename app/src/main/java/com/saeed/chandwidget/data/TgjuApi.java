package com.saeed.chandwidget.data;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;

/**
 * TGJU API fetcher.
 *
 * Non-crypto:  GET https://api.tgju.org/v1/market/tmp?keys=KEY1,KEY2,...
 *   Response: response.indicators[].{ name, p, d, dt }
 *   p = price in Rial, d = change in Rial, dt = "high"|"low"|null
 *
 * Crypto:      GET https://api.tgju.org/v1/widget/tmp?keys=398096,398097,...
 *   Same structure but identified by item_id; we match by name field.
 *   p = price in USD, d = change in USD
 *   "crypto-tether" (USDT) price in Toman is available as p_irr (but we treat via CRYPTO_TOMAN)
 *
 * Crypto item_ids:
 *   crypto-bitcoin       = 398096
 *   crypto-ethereum      = 398097
 *   crypto-binance-coin  = 398115
 *   crypto-solana        = 535605
 *   (litecoin, bch, eos not in widget API — fall back to summary-table)
 */
public class TgjuApi {
    private static final String TAG = "TgjuApi";
    private static final String TMP_URL  = "https://api.tgju.org/v1/market/tmp?keys=";
    private static final String WGT_URL  = "https://api.tgju.org/v1/widget/tmp?keys=131398,131400,131403,131404,137119,137123,398096,398097,398115,535605,398102,1696858,1696863,1696864,1244756,137454";
    // Crypto item_ids for widget API
    private static final String CRYPTO_WIDGET_KEYS = "398096,398097,398115,535605";
    private static final int TIMEOUT = 12000;

    // Keys that are served by the widget/tmp endpoint (by name field)
    private static final java.util.Set<String> WIDGET_CRYPTO_NAMES = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "crypto-bitcoin", "crypto-ethereum", "crypto-binance-coin", "crypto-solana"
            )
    );

    public static PriceData fetch(String key) {
        try {
            if (isCryptoWidgetKey(key)) {
                return fetchFromWidgetApi(key);
            } else if (isCryptoKey(key)) {
                // litecoin, bch, eos — fallback to summary-table
                return fetchFromSummaryTable(key);
            } else {
                return fetchFromTmpApi(key);
            }
        } catch (Exception e) {
            Log.e(TAG, "fetch error for " + key, e);
            return null;
        }
    }

    private static boolean isCryptoKey(String key) {
        return key.startsWith("crypto-");
    }

    private static boolean isCryptoWidgetKey(String key) {
        return WIDGET_CRYPTO_NAMES.contains(key);
    }

    /** Fetch non-crypto prices from /v1/market/tmp */
    private static PriceData fetchFromTmpApi(String key) throws Exception {
        String urlStr = TMP_URL + key;
        String json = httpGet(urlStr);
        if (json == null) return null;

        JSONObject root = new JSONObject(json);
        JSONObject response = root.getJSONObject("response");
        JSONArray indicators = response.getJSONArray("indicators");

        for (int i = 0; i < indicators.length(); i++) {
            JSONObject ind = indicators.getJSONObject(i);
            String name = ind.optString("name", "");
            // For items with null name, match by slug or title isn't reliable; skip
            if (name.equals(key)) {
                double price = parseDouble(ind.optString("p", "0"));
                double change = parseDouble(ind.optString("d", "0"));
                String dt = ind.optString("dt", "");

                // API returns Rial; convert to Toman
                PriceItem item = PriceRegistry.get(key);
                if (item != null && item.getType() != PriceItem.PriceType.CRYPTO_USD) {
                    price = price / 10.0;
                    change = change / 10.0;
                }
                // dt="low" means price went down (d is absolute, negative direction)
                if ("low".equals(dt)) change = -Math.abs(change);
                else change = Math.abs(change);

                return new PriceData(key, price, change);
            }
        }
        Log.w(TAG, "Key not found in tmp response: " + key);
        return null;
    }

    /** Fetch crypto prices from /v1/widget/tmp */
    private static PriceData fetchFromWidgetApi(String key) throws Exception {
        String urlStr = "https://api.tgju.org/v1/widget/tmp?keys=" + CRYPTO_WIDGET_KEYS;
        String json = httpGet(urlStr);
        if (json == null) return null;

        JSONObject root = new JSONObject(json);
        JSONObject response = root.getJSONObject("response");
        JSONArray indicators = response.getJSONArray("indicators");

        for (int i = 0; i < indicators.length(); i++) {
            JSONObject ind = indicators.getJSONObject(i);
            String name = ind.optString("name", "");
            if (name.equals(key)) {
                double price = parseDouble(ind.optString("p", "0"));
                double change = parseDouble(ind.optString("d", "0"));
                String dt = ind.optString("dt", "");

                if ("low".equals(dt)) change = -Math.abs(change);
                else change = Math.abs(change);

                // USDT: convert to Toman if we need (p_irr is in Rial)
                PriceItem item = PriceRegistry.get(key);
                if (item != null && item.getType() == PriceItem.PriceType.CRYPTO_TOMAN) {
                    String pIrr = ind.optString("p_irr", "").replace(",", "");
                    if (!pIrr.isEmpty()) {
                        price = parseDouble(pIrr) / 10.0;
                    }
                }
                return new PriceData(key, price, change);
            }
        }
        return null;
    }

    /** Fallback: summary-table for crypto not in widget API (ltc, bch, eos) */
    private static PriceData fetchFromSummaryTable(String key) throws Exception {
        String urlStr = "https://api.tgju.org/v1/market/indicator/summary-table-data/" + key;
        String json = httpGet(urlStr);
        if (json == null) return null;

        JSONObject root = new JSONObject(json);
        JSONArray dataArr = root.getJSONArray("data");
        if (dataArr.length() == 0) return null;

        JSONArray row = dataArr.getJSONArray(0);
        double price = parseDouble(row.getString(1).replace(",", "").trim());
        double change = 0;
        try { change = parseDouble(row.getString(3).replace(",", "").trim()); } catch (Exception ignored) {}
        return new PriceData(key, price, change);
    }

    private static String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestProperty("User-Agent", "ChandWidget/1.0");
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.e(TAG, "HTTP " + code + " for " + urlStr);
                return null;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "httpGet error: " + urlStr, e);
            return null;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.replace(",", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
