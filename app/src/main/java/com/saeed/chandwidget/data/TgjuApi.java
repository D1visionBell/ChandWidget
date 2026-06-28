package com.saeed.chandwidget.data;

import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * TGJU API fetcher.
 * endpoint: https://api.tgju.org/v1/market/indicator/summary-table-data/{key}
 * Response JSON fields we use:
 *   data[0][1] = current price (Rial for currencies/gold, USD for crypto)
 *   data[0][3] = change amount
 *   data[0][4] = change percent string e.g. "1.23%"
 */
public class TgjuApi {
    private static final String TAG = "TgjuApi";
    private static final String BASE = "https://api.tgju.org/v1/market/indicator/summary-table-data/";
    private static final int TIMEOUT = 10000;

    public interface Callback {
        void onResult(PriceData data);
        void onError(String key, String error);
    }

    public static PriceData fetch(String key) {
        try {
            URL url = new URL(BASE + key);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestProperty("User-Agent", "ChandWidget/1.0");

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.e(TAG, "HTTP " + code + " for " + key);
                return null;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            return parse(key, sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "fetch error for " + key, e);
            return null;
        }
    }

    private static PriceData parse(String key, String json) {
        try {
            JSONObject root = new JSONObject(json);
            // structure: { "data": [ ["name", "price", "open", "change", "changePct", ...], ... ] }
            org.json.JSONArray dataArr = root.getJSONArray("data");
            if (dataArr.length() == 0) return null;

            org.json.JSONArray row = dataArr.getJSONArray(0);
            // index 1 = price (string, may contain commas)
            String priceStr = row.getString(1).replace(",", "").trim();
            // index 3 = change
            String changeStr = row.getString(3).replace(",", "").trim();

            double price = Double.parseDouble(priceStr);
            double change = 0;
            try { change = Double.parseDouble(changeStr); } catch (Exception ignored) {}

            // Convert Rial to Toman for non-crypto
            PriceItem item = PriceRegistry.get(key);
            if (item != null && (item.getType() == PriceItem.PriceType.CURRENCY_TOMAN
                    || item.getType() == PriceItem.PriceType.GOLD_TOMAN
                    || item.getType() == PriceItem.PriceType.CRYPTO_TOMAN)) {
                price = price / 10.0;
                change = change / 10.0;
            }

            return new PriceData(key, price, change);
        } catch (Exception e) {
            Log.e(TAG, "parse error for " + key, e);
            return null;
        }
    }
}
