package com.saeed.chandwidget.config;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import com.saeed.chandwidget.R;
import com.saeed.chandwidget.data.PriceItem;
import com.saeed.chandwidget.data.PriceRegistry;
import com.saeed.chandwidget.util.Prefs;
import com.saeed.chandwidget.widget.PriceUpdateService;
import com.saeed.chandwidget.widget.PriceWidgetProvider;
import java.util.List;

public class WidgetConfigActivity extends AppCompatActivity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final Spinner[] spinners = new Spinner[3];
    private SwitchCompat langSwitch;
    private List<PriceItem> itemList;
    private boolean initializing = true;
    // Whether this was opened from the launcher (new widget) vs. from clicking an existing widget
    private boolean isNewWidget = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Extract appWidgetId from Intent — works both when launched from launcher
        // (APPWIDGET_CONFIGURE action) and when user taps an existing widget
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // FIX: if ID still invalid, check action — some launchers send it differently
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Last resort: get any active widget ID (for reconfigure from tap)
            AppWidgetManager mgr = AppWidgetManager.getInstance(this);
            android.content.ComponentName cn = new android.content.ComponentName(
                    this, PriceWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids != null && ids.length > 0) {
                appWidgetId = ids[0];
            }
        }

        // Determine if this is a new widget being added (from launcher configure flow)
        String action = intent.getAction();
        isNewWidget = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(action);

        // For new widget flow: set CANCELED as default so backing out removes widget
        // For reconfigure from tap: no result needed, just finish()
        if (isNewWidget) {
            setResult(RESULT_CANCELED);
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Truly can't determine which widget — close gracefully
            finish();
            return;
        }

        setContentView(R.layout.activity_config);

        itemList = PriceRegistry.getAll();

        spinners[0] = findViewById(R.id.spinner0);
        spinners[1] = findViewById(R.id.spinner1);
        spinners[2] = findViewById(R.id.spinner2);
        langSwitch  = findViewById(R.id.lang_switch);

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
        });

        AdapterView.OnItemSelectedListener noop = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {}
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
        for (Spinner s : spinners) s.setOnItemSelectedListener(noop);

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
    }

    private void setSpinnerToKey(Spinner spinner, String key) {
        for (int j = 0; j < itemList.size(); j++) {
            if (itemList.get(j).getKey().equals(key)) {
                spinner.setSelection(j, false);
                return;
            }
        }
        spinner.setSelection(0, false);
    }

    private void save() {
        for (int i = 0; i < 3; i++) {
            int pos = spinners[i].getSelectedItemPosition();
            if (pos >= 0 && pos < itemList.size()) {
                Prefs.setSlot(this, appWidgetId, i, itemList.get(pos).getKey());
            }
        }
        Prefs.setLanguage(this, langSwitch.isChecked());

        // Trigger data fetch
        Intent svc = new Intent(this, PriceUpdateService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        // Immediately update widget with whatever is cached (may be empty first time)
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        PriceWidgetProvider.updateWidget(this, mgr, appWidgetId);

        // FIX: For new widget flow, MUST return RESULT_OK + widget ID
        // Without this the launcher discards the widget ("could not add widget")
        if (isNewWidget) {
            Intent result = new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, result);
        }

        finish();
    }

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
}
