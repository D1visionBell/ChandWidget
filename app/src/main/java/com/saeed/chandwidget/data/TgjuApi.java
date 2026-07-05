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
 *   Exception: "ons" (gold ounce) is already quoted in USD, not Rial —
 *   see USD_TMP_KEYS below.
 *
 * Crypto BTC/ETH/BNB/SOL via widget endpoint:
 *   GET https://api.tgju.org/v1/widget/tmp?keys=398096,398097,398115,535605
 *   p = price in USD
 *
 * Crypto LTC/BCH/EOS/XRP/TRX/DOGE fallback:
 *   GET https://api.tgju.org/v1/market/indicator/summary-table-data/KEY
 *
 * Tether (USDT), in Toman like every other currency:
 *   GET https://call4.tgju.org/ajax.json?rev=...
 *   response.current["crypto-tether-irr"].p (Rial)
 */
public class TgjuApi {
    private static final String TAG = "TgjuApi";
    private static final int TIMEOUT = 12000;

    private static final String TMP_URL     = "https://api.tgju.org/v1/market/tmp?keys=";
    private static final String WIDGET_URL  = "https://api.tgju.org/v1/widget/tmp?keys=398096,398097,398115,535605";
    private static final String SUMMARY_URL = "https://api.tgju.org/v1/market/indicator/summary-table-data/";

    // Non-crypto tmp keys that are already quoted in USD (world commodity
    // price), unlike the Toman-denominated tmp keys (price_dollar_rl,
    // geram18, etc.), so fetchFromTmp() must NOT apply the Rial→Toman
    // (÷10) conversion to these.
    private static final java.util.Set<String> USD_TMP_KEYS = new java.util.HashSet<>(
            java.util.Arrays.asList(PriceItem.KEY_GOLD_OUNCE)
    );

    // BTC, ETH, BNB, SOL از widget endpoint
    // NOTE: previously hardcoded as literal strings here, and the BNB literal
    // ("crypto-binancecoin") didn't match PriceItem.KEY_BNB ("crypto-binance-coin").
    // That mismatch meant BNB always fell through to fetchFromTmp() and silently
    // failed (widget always showed "—" for BNB). Referencing the PriceItem
    // constants directly makes it impossible for the two to drift apart again.
    private static final java.util.Set<String> WIDGET_KEYS = new java.util.HashSet<>(
            java.util.Arrays.asList(PriceItem.KEY_BTC, PriceItem.KEY_ETH, PriceItem.KEY_BNB, PriceItem.KEY_SOL)
    );

    // LTC, BCH, EOS, XRP, TRX, DOGE از summary-table (USD)
    // NOTE: XRP/TRX/DOGE slugs are a best guess (see comment on their
    // PriceItem constants) and haven't been confirmed against a real API
    // response the way the others here have.
    private static final java.util.Set<String> SUMMARY_KEYS = new java.util.HashSet<>(
            java.util.Arrays.asList(PriceItem.KEY_LTC, PriceItem.KEY_BCH, PriceItem.KEY_EOS,
                    PriceItem.KEY_XRP, PriceItem.KEY_TRX, PriceItem.KEY_DOGE)
    );

    // Tether (USDT) — confirmed real Rial-denominated indicator from TGJU's
    // ajax feed (verified against the live "1,740,500" value shown on
    // tgju.org). This is what tgju.org itself uses to show Tether next to
    // the dollar in Toman, as opposed to the USD-denominated crypto summary
    // table used for the other coins above.
    private static final String CALL4_URL =
            "https://call4.tgju.org/ajax.json?rev=NUInYHDaLqVjbIse8P1gUgsBjJv0zV4MhEX9NwN3U0tYFAZwZ8iYWMHqm5dN";

    public static PriceData fetch(String key) {
        try {
            if (PriceItem.KEY_USDT.equals(key)) return fetchTetherIrr();
            if (WIDGET_KEYS.contains(key))  return fetchFromWidget(key);
            if (SUMMARY_KEYS.contains(key)) return fetchFromSummary(key);
            return fetchFromTmp(key);
        } catch (Exception e) {
            Log.e(TAG, "fetch error for " + key, e);
            return null;
        }
    }

    private static PriceData fetchTetherIrr() throws Exception {
        String json = httpGet(CALL4_URL);
        if (json == null) return null;

        JSONObject current = new JSONObject(json).optJSONObject("current");
        if (current == null) return null;
        JSONObject ind = current.optJSONObject("crypto-tether-irr");
        if (ind == null) {
            Log.w(TAG, "crypto-tether-irr not found in call4 feed");
            return null;
        }

        // Feed is Rial, like price_dollar_rl and the rest of the tmp keys —
        // divide by 10 for Toman, same convention as fetchFromTmp().
        double price  = parseDouble(ind.optString("p", "0")) / 10.0;
        double change = parseDouble(ind.optString("d", "0")) / 10.0;
        String dt     = ind.optString("dt", "");
        if ("low".equals(dt)) change = -Math.abs(change);
        else change = Math.abs(change);

        return new PriceData(PriceItem.KEY_USDT, price, change);
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

            // Rial → Toman (skip for keys already quoted in USD, e.g. gold ounce)
            if (!USD_TMP_KEYS.contains(key)) {
                price  /= 10.0;
                change /= 10.0;
            }

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
