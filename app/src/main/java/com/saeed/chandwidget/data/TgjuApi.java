package com.saeed.chandwidget.data;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * TGJU API fetcher
 *
 * Non-crypto (currencies, gold, coins):
 *   GET https://api.tgju.org/v1/market/tmp?keys=KEY
 *   response.indicators[].{ name, p (Rial), d (Rial), dt ("high"|"low") }
 *
 * Crypto BTC/ETH/BNB via widget endpoint:
 *   GET https://api.tgju.org/v1/widget/tmp?keys=398096,398097,398115
 *   p = price in USD
 *
 * Crypto LTC/BCH/EOS fallback:
 *   GET https://api.tgju.org/v1/market/indicator/summary-table-data/KEY
 */
public class TgjuApi {
    private static final String TAG = "TgjuApi";
    private static final int TIMEOUT = 12000;

    private static final String TMP_URL     = "https://api.tgju.org/v1/market/tmp?keys=";
    private static final String WIDGET_URL  = "https://api.tgju.org/v1/widget/tmp?keys=398096,398097,398115";
    private static final String SUMMARY_URL = "https://api.tgju.org/v1/market/indicator/summary-table-data/";

    // BTC, ETH, BNB از widget endpoint
    private static final java.util.Set<String> WIDGET_KEYS = new java.util.HashSet<>(
            java.util.Arrays.asList("crypto-bitcoin", "crypto-ethereum", "crypto-binancecoin")
    );

    // LTC, BCH, EOS از summary-table
    private static final java.util.Set<String> SUMMARY_KEYS = new java.util.HashSet<>(
            java.util.Arrays.asList("crypto-litecoin", "crypto-bitcoin-cash", "crypto-eos")
    );

    public static PriceData fetch(String key) {
        try {
            if (WIDGET_KEYS.contains(key))  return fetchFromWidget(key);
            if (SUMMARY_KEYS.contains(key)) return fetchFromSummary(key);
            return fetchFromTmp(key);
        } catch (Exception e) {
            Log.e(TAG, "fetch error for " + key, e);
            return null;
        }
    }

    private static PriceData fetchFromTmp(String key) throws Exception {
        String json = httpGet(TMP_URL + key);
        if (json == null) return null;

        JSONObject root = new JSONObject(json);
        JSONArray indicators = root.getJSONObject("response").getJSONArray("indicators");

        for (int i = 0; i < indicators.length(); i++) {
            JSONObject ind = indicators.getJSONObject(i);
            if (!ind.optString("name", "").equals(key)) continue;

            double price  = parseDouble(ind.optString("p", "0"));
            double change = parseDouble(ind.optString("d", "0"));
            String dt     = ind.optString("dt", "");

            if ("low".equals(dt)) change = -Math.abs(change);
            else change = Math.abs(change);

            // Rial → Toman
            price  /= 10.0;
            change /= 10.0;

            return new PriceData(key, price, change);
        }
        Log.w(TAG, "Key not found in tmp: " + key);
        return null;
    }

    private static PriceData fetchFromWidget(String key) throws Exception {
        String json = httpGet(WIDGET_URL);
        if (json == null) return null;

        JSONArray indicators = new JSONObject(json)
                .getJSONObject("response").getJSONArray("indicators");

        for (int i = 0; i < indicators.length(); i++) {
            JSONObject ind = indicators.getJSONObject(i);
            if (!ind.optString("name", "").equals(key)) continue;

            double price  = parseDouble(ind.optString("p", "0"));
            double change = parseDouble(ind.optString("d", "0"));
            String dt     = ind.optString("dt", "");

            if ("low".equals(dt)) change = -Math.abs(change);
            else change = Math.abs(change);

            return new PriceData(key, price, change);
        }
        return fetchFromSummary(key);
    }

    private static PriceData fetchFromSummary(String key) throws Exception {
        String json = httpGet(SUMMARY_URL + key);
        if (json == null) return null;

        JSONArray dataArr = new JSONObject(json).getJSONArray("data");
        if (dataArr.length() == 0) return null;

        JSONArray row = dataArr.getJSONArray(0);
        double price  = parseDouble(row.getString(1).replace(",", ""));
        double change = 0;
        try { change = parseDouble(row.getString(3).replace(",", "")); } catch (Exception ignored) {}
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
            if (conn.getResponseCode() != 200) return null;
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
        try { return Double.parseDouble(s.replace(",", "").trim()); }
        catch (Exception e) { return 0; }
    }
}
