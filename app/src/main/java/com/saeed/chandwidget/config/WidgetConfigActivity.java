package com.saeed.chandwidget.config;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
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
    private boolean initializing = true; // block listeners during setup

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        // Return CANCELED so launcher removes widget if user backs out
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_config);

        itemList = PriceRegistry.getAll();

        spinners[0] = findViewById(R.id.spinner0);
        spinners[1] = findViewById(R.id.spinner1);
        spinners[2] = findViewById(R.id.spinner2);
        langSwitch  = findViewById(R.id.lang_switch);

        // Set adapters while initializing=true to suppress listeners
        for (int i = 0; i < 3; i++) {
            spinners[i].setAdapter(new PriceAdapter());
        }

        // Restore saved selections
        for (int i = 0; i < 3; i++) {
            String key = Prefs.getSlot(this, appWidgetId, i);
            setSpinnerToKey(spinners[i], key);
        }

        langSwitch.setChecked(Prefs.isPersian(this));

        // Now safe to allow interaction
        initializing = false;

        // Language toggle: refresh adapter labels
        langSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            if (initializing) return;
            Prefs.setLanguage(this, isChecked);
            for (Spinner s : spinners) {
                ((PriceAdapter) s.getAdapter()).notifyDataSetChanged();
            }
        });

        // Attach dummy listeners to prevent "no selection" state issues
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
                spinner.setSelection(j, false); // false = no animation, no listener fire
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

        // Start fetch service
        Intent svc = new Intent(this, PriceUpdateService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        // Update widget with cached data immediately
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        PriceWidgetProvider.updateWidget(this, mgr, appWidgetId);

        // IMPORTANT: must return RESULT_OK with widget id for launcher to accept widget
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
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
                // Use the activity's layout inflater (not application context) for proper theming
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
            } catch (Exception e) {
                // safety net — should never happen
            }
            return convertView;
        }
    }
}
