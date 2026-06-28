package com.saeed.chandwidget.config;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.saeed.chandwidget.R;
import com.saeed.chandwidget.data.PriceItem;
import com.saeed.chandwidget.data.PriceRegistry;
import com.saeed.chandwidget.util.Prefs;
import com.saeed.chandwidget.widget.PriceUpdateService;
import com.saeed.chandwidget.widget.PriceWidgetProvider;
import java.util.ArrayList;
import java.util.List;

public class WidgetConfigActivity extends AppCompatActivity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private Spinner[] spinners = new Spinner[3];
    private Switch langSwitch;
    private List<PriceItem> itemList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get widget ID
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
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

        // Build adapter
        PriceAdapter adapter = new PriceAdapter(itemList);
        for (int i = 0; i < 3; i++) {
            spinners[i].setAdapter(adapter);
            // Set current selection
            String currentKey = Prefs.getSlot(this, i);
            for (int j = 0; j < itemList.size(); j++) {
                if (itemList.get(j).getKey().equals(currentKey)) {
                    spinners[i].setSelection(j);
                    break;
                }
            }
        }

        langSwitch.setChecked(Prefs.isPersian(this));

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
    }

    private void save() {
        // Save slots
        for (int i = 0; i < 3; i++) {
            PriceItem selected = (PriceItem) spinners[i].getSelectedItem();
            if (selected != null) {
                Prefs.setSlot(this, i, selected.getKey());
            }
        }
        Prefs.setLanguage(this, langSwitch.isChecked());

        // Trigger fetch and update
        startService(new Intent(this, PriceUpdateService.class));

        // Update widget
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        PriceWidgetProvider.updateWidget(this, mgr, appWidgetId);

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    // Custom adapter showing emoji + name
    private class PriceAdapter extends ArrayAdapter<PriceItem> {
        private final List<PriceItem> items;

        PriceAdapter(List<PriceItem> items) {
            super(WidgetConfigActivity.this, R.layout.item_price_selector, items);
            this.items = items;
        }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            return createView(pos, convertView, parent);
        }

        @Override
        public View getDropDownView(int pos, View convertView, ViewGroup parent) {
            return createView(pos, convertView, parent);
        }

        private View createView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_price_selector, parent, false);
            }
            PriceItem item = items.get(pos);
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
