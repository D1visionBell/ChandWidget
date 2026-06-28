package com.saeed.chandwidget.config;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import com.saeed.chandwidget.R;
import com.saeed.chandwidget.data.PriceItem;
import com.saeed.chandwidget.data.PriceRegistry;
import com.saeed.chandwidget.util.Formatter;
import com.saeed.chandwidget.util.Prefs;
import com.saeed.chandwidget.widget.PriceUpdateService;
import com.saeed.chandwidget.widget.PriceWidgetProvider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WidgetConfigActivity extends AppCompatActivity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final Spinner[] spinners = new Spinner[3];
    private SwitchCompat langSwitch;
    private SwitchCompat multiRowSwitch;   // for small widget: single vs 3-row
    private List<PriceItem> itemList;
    private boolean initializing = true;
    private boolean isNewWidget = false;

    private final java.util.Map<String, TextView> priceViews  = new java.util.HashMap<>();
    private final java.util.Map<String, TextView> changeViews = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(this);
            android.content.ComponentName cn = new android.content.ComponentName(
                    this, PriceWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids != null && ids.length > 0) {
                appWidgetId = ids[0];
            } else {
                android.content.ComponentName cnSmall = new android.content.ComponentName(
                        this, PriceWidgetProviderSingle.class);
                int[] smallIds = mgr.getAppWidgetIds(cnSmall);
                if (smallIds != null && smallIds.length > 0) {
                    appWidgetId = smallIds[0];
                }
            }
        }

        String action = intent.getAction();
        isNewWidget = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(action);
        if (isNewWidget) setResult(RESULT_CANCELED);

        setContentView(R.layout.activity_config);

        itemList = PriceRegistry.getAll();

        // ── Price grid ───────────────────────────────────────────────────────
        LinearLayout priceGrid = findViewById(R.id.price_grid);
        buildPriceGrid(priceGrid);
        updatePriceListUI();
        updateLastUpdateTime();

        // ── Refresh button ────────────────────────────────────────────────────
        findViewById(R.id.btn_refresh_app).setOnClickListener(v -> {
            TextView lastUpdate = findViewById(R.id.last_update_time);
            lastUpdate.setText(getString(R.string.loading));
            PriceWidgetProvider.triggerFetch(this);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                updatePriceListUI();
                updateLastUpdateTime();
            }, 5000);
        });

        // ── Widget config section ─────────────────────────────────────────────
        View configSection = findViewById(R.id.widget_config_section);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            configSection.setVisibility(View.GONE);
            return;
        }

        boolean small = isSmallWidget();

        TextView sizeBadge = findViewById(R.id.widget_size_badge);
        sizeBadge.setText(small
                ? getString(R.string.widget_size_single)
                : getString(R.string.widget_size_triple));

        spinners[0] = findViewById(R.id.spinner0);
        spinners[1] = findViewById(R.id.spinner1);
        spinners[2] = findViewById(R.id.spinner2);
        langSwitch   = findViewById(R.id.lang_switch);
        multiRowSwitch = findViewById(R.id.multi_row_switch);

        // Show/hide multi-row toggle only for small widget
        View multiRowContainer = findViewById(R.id.multi_row_container);
        if (small) {
            multiRowContainer.setVisibility(View.VISIBLE);
            multiRowSwitch.setChecked(Prefs.getSmallWidgetMultiRow(this, appWidgetId));

            // When multi-row ON: show all 3 spinners; when OFF: show only slot 0
            updateSmallWidgetSlotVisibility(multiRowSwitch.isChecked());

            multiRowSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                if (initializing) return;
                Prefs.setSmallWidgetMultiRow(this, appWidgetId, isChecked);
                updateSmallWidgetSlotVisibility(isChecked);
            });
        } else {
            multiRowContainer.setVisibility(View.GONE);
            // Large widget always shows all 3 slots
            findViewById(R.id.slot1_container).setVisibility(View.VISIBLE);
            findViewById(R.id.slot2_container).setVisibility(View.VISIBLE);
        }

        for (int i = 0; i < 3; i++) {
            spinners[i].setAdapter(new PriceAdapter());
        }

        for (int i = 0; i < 3; i++) {
            String key = Prefs.getSlot(this, appWidgetId, i);
            setSpinnerToKey(spinners[i], key);
        }

        langSwitch.setChecked(Prefs.isPersian(this));
        initializing = false;

        langSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            if (initializing) return;
            Prefs.setLanguage(this, isChecked);
            for (Spinner s : spinners) {
                ((PriceAdapter) s.getAdapter()).notifyDataSetChanged();
            }
            buildPriceGrid(priceGrid);
            updatePriceListUI();
        });

        AdapterView.OnItemSelectedListener noop = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {}
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
        for (Spinner s : spinners) s.setOnItemSelectedListener(noop);

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
    }

    private void updateSmallWidgetSlotVisibility(boolean multi) {
        // Slot 0 always visible
        int vis1 = multi ? View.VISIBLE : View.GONE;
        int vis2 = multi ? View.VISIBLE : View.GONE;
        findViewById(R.id.slot1_container).setVisibility(vis1);
        findViewById(R.id.slot2_container).setVisibility(vis2);
    }

    // ── Price Grid ────────────────────────────────────────────────────────────

    private void buildPriceGrid(LinearLayout grid) {
        grid.removeAllViews();
        priceViews.clear();
        changeViews.clear();

        boolean persian = Prefs.isPersian(this);
        int colCount = 2;
        LinearLayout row = null;

        for (int i = 0; i < itemList.size(); i++) {
            if (i % colCount == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = dp(8);
                row.setLayoutParams(rowParams);
                grid.addView(row);
            }

            PriceItem item = itemList.get(i);
            View card = buildPriceCard(item, persian);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i % colCount == 0) cardParams.rightMargin = dp(4);
            else cardParams.leftMargin = dp(4);
            card.setLayoutParams(cardParams);
            if (row != null) row.addView(card);
        }

        if (itemList.size() % colCount != 0 && row != null) {
            View filler = new View(this);
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            fp.leftMargin = dp(4);
            filler.setLayoutParams(fp);
            row.addView(filler);
        }
    }

    private View buildPriceCard(PriceItem item, boolean persian) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundResource(R.drawable.card_bg_config);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(item.getEmoji());
        tvEmoji.setTextSize(20);
        tvEmoji.setPadding(0, 0, dp(8), 0);
        topRow.addView(tvEmoji);

        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ncParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameCol.setLayoutParams(ncParams);

        TextView tvName = new TextView(this);
        tvName.setText(persian ? item.getNameFa() : item.getNameEn());
        tvName.setTextSize(12);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setMaxLines(1);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameCol.addView(tvName);

        TextView tvSym = new TextView(this);
        tvSym.setText(persian ? item.getSymbolFa() : item.getSymbolEn());
        tvSym.setTextSize(10);
        tvSym.setTextColor(0xFF888888);
        nameCol.addView(tvSym);

        topRow.addView(nameCol);

        TextView tvChange = new TextView(this);
        tvChange.setText("—");
        tvChange.setTextSize(11);
        tvChange.setTextColor(0xFF888888);
        tvChange.setGravity(Gravity.END);
        topRow.addView(tvChange);

        card.addView(topRow);

        TextView tvPrice = new TextView(this);
        tvPrice.setText("—");
        tvPrice.setTextSize(18);
        tvPrice.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPrice.setGravity(Gravity.START);
        LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        priceParams.topMargin = dp(6);
        tvPrice.setLayoutParams(priceParams);
        card.addView(tvPrice);

        priceViews.put(item.getKey(), tvPrice);
        changeViews.put(item.getKey(), tvChange);

        return card;
    }

    private void updatePriceListUI() {
        boolean persian = Prefs.isPersian(this);
        for (PriceItem item : itemList) {
            double price  = Prefs.getCachedPrice(this, item.getKey());
            double change = Prefs.getCachedChange(this, item.getKey());

            TextView tvPrice  = priceViews.get(item.getKey());
            TextView tvChange = changeViews.get(item.getKey());
            if (tvPrice == null || tvChange == null) continue;

            if (price == 0) {
                tvPrice.setText("—");
                tvChange.setText("—");
                tvChange.setTextColor(0xFF888888);
                continue;
            }

            String priceStr = Formatter.formatPrice(price, item.getType(), persian);
            String chgStr   = Formatter.formatChange(change, item.getType(), persian);
            if (persian) {
                priceStr = Formatter.toPersianDigits(priceStr);
                chgStr   = Formatter.toPersianDigits(chgStr);
            }

            tvPrice.setText(priceStr);
            tvChange.setText(chgStr);

            int color = change >= 0 ? Color.parseColor("#E53935") : Color.parseColor("#43A047");
            tvChange.setTextColor(color);
        }
    }

    private void updateLastUpdateTime() {
        long cacheTime = Prefs.getCacheTime(this);
        TextView tv = findViewById(R.id.last_update_time);
        if (tv == null) return;
        if (cacheTime > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d، HH:mm", Locale.US);
            tv.setText("آخرین بروزرسانی: " + sdf.format(new Date(cacheTime)));
        } else {
            tv.setText(getString(R.string.loading));
        }
    }

    // ── Widget size detection ─────────────────────────────────────────────────

    private boolean isSmallWidget() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false;
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(this);
            AppWidgetProviderInfo info = mgr.getAppWidgetInfo(appWidgetId);
            if (info == null) return false;
            return info.provider.getClassName().contains("Small");
        } catch (Exception e) {
            return false;
        }
    }

    // ── Spinner helpers ───────────────────────────────────────────────────────

    private void setSpinnerToKey(Spinner spinner, String key) {
        for (int j = 0; j < itemList.size(); j++) {
            if (itemList.get(j).getKey().equals(key)) {
                spinner.setSelection(j, false);
                return;
            }
        }
        spinner.setSelection(0, false);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void save() {
        boolean small = isSmallWidget();
        boolean multiRow = small && multiRowSwitch != null && multiRowSwitch.isChecked();

        int slotsToSave = (small && !multiRow) ? 1 : 3;

        for (int i = 0; i < slotsToSave; i++) {
            int pos = spinners[i].getSelectedItemPosition();
            if (pos >= 0 && pos < itemList.size()) {
                Prefs.setSlot(this, appWidgetId, i, itemList.get(pos).getKey());
            }
        }
        Prefs.setLanguage(this, langSwitch.isChecked());
        if (small) {
            Prefs.setSmallWidgetMultiRow(this, appWidgetId, multiRow);
        }

        Intent svc = new Intent(this, PriceUpdateService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        if (small) {
            PriceWidgetProviderSingle.update(this, mgr, appWidgetId);
        } else {
            PriceWidgetProvider.update(this, mgr, appWidgetId);
        }

        if (isNewWidget) {
            Intent result = new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, result);
        }

        finish();
    }

    // ── Spinner Adapter ───────────────────────────────────────────────────────

    private class PriceAdapter extends BaseAdapter {
        @Override public int getCount()          { return itemList.size(); }
        @Override public Object getItem(int pos) { return itemList.get(pos); }
        @Override public long getItemId(int pos) { return pos; }
        @Override public boolean hasStableIds()  { return true; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            return buildView(pos, convertView, parent);
        }

        @Override
        public View getDropDownView(int pos, View convertView, ViewGroup parent) {
            return buildView(pos, convertView, parent);
        }

        private View buildView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(WidgetConfigActivity.this)
                        .inflate(R.layout.item_price_selector, parent, false);
            }
            try {
                PriceItem item = itemList.get(pos);
                TextView tvEmoji = convertView.findViewById(R.id.item_emoji);
                TextView tvName  = convertView.findViewById(R.id.item_name);
                TextView tvSym   = convertView.findViewById(R.id.item_symbol);

                tvEmoji.setText(item.getEmoji());
                boolean persian = Prefs.isPersian(WidgetConfigActivity.this);
                tvName.setText(persian ? item.getNameFa() : item.getNameEn());
                tvSym.setText(persian ? item.getSymbolFa() : item.getSymbolEn());
            } catch (Exception ignored) {}
            return convertView;
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
