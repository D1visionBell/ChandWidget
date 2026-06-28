package com.saeed.chandwidget.config;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    private PriceAdapter adapter;

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

        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_config);

        itemList = PriceRegistry.getAll();

        spinners[0] = findViewById(R.id.spinner0);
        spinners[1] = findViewById(R.id.spinner1);
        spinners[2] = findViewById(R.id.spinner2);
        langSwitch  = findViewById(R.id.lang_switch);

        // Single adapter instance shared across all spinners
        adapter = new PriceAdapter();
        for (int i = 0; i < 3; i++) {
            // Each spinner needs its own adapter instance to avoid selection conflicts
            spinners[i].setAdapter(new PriceAdapter());
            selectCurrentSlot(i);
        }

        langSwitch.setChecked(Prefs.isPersian(this));

        // Refresh adapter labels when language is toggled
        langSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            Prefs.setLanguage(this, isChecked);
            for (Spinner s : spinners) {
                ((PriceAdapter) s.getAdapter()).notifyDataSetChanged();
            }
        });

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
    }

    private void selectCurrentSlot(int slotIndex) {
        String currentKey = Prefs.getSlot(this, appWidgetId, slotIndex);
        for (int j = 0; j < itemList.size(); j++) {
            if (itemList.get(j).getKey().equals(currentKey)) {
                spinners[slotIndex].setSelection(j);
                return;
            }
        }
    }

    private void save() {
        for (int i = 0; i < 3; i++) {
            int pos = spinners[i].getSelectedItemPosition();
            if (pos >= 0 && pos < itemList.size()) {
                Prefs.setSlot(this, appWidgetId, i, itemList.get(pos).getKey());
            }
        }
        Prefs.setLanguage(this, langSwitch.isChecked());

        Intent svc = new Intent(this, PriceUpdateService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        PriceWidgetProvider.updateWidget(this, mgr, appWidgetId);

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    // Use BaseAdapter directly to avoid ArrayAdapter's internal list conflicts
    private class PriceAdapter extends BaseAdapter {

        @Override public int getCount()              { return itemList.size(); }
        @Override public Object getItem(int pos)     { return itemList.get(pos); }
        @Override public long getItemId(int pos)     { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            return buildView(pos, convertView, parent, false);
        }

        @Override
        public View getDropDownView(int pos, View convertView, ViewGroup parent) {
            return buildView(pos, convertView, parent, true);
        }

        private View buildView(int pos, View convertView, ViewGroup parent, boolean dropdown) {
            if (convertView == null) {
                convertView = LayoutInflater.from(WidgetConfigActivity.this)
                        .inflate(R.layout.item_price_selector, parent, false);
            }
            PriceItem item = itemList.get(pos);
            TextView tvEmoji = convertView.findViewById(R.id.item_emoji);
            TextView tvName  = convertView.findViewById(R.id.item_name);
            TextView tvSym   = convertView.findViewById(R.id.item_symbol);

            tvEmoji.setText(item.getEmoji());
            boolean persian = Prefs.isPersian(WidgetConfigActivity.this);
            tvName.setText(persian ? item.getNameFa() : item.getNameEn());
            tvSym.setText(persian ? item.getSymbolFa() : item.getSymbolEn());
            return convertView;
        }
    }
}
