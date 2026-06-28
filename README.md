# ChandWidget — ویجت قیمت بازار ایران

ویجت اندروید ۲×۲ برای نمایش لحظه‌ای قیمت‌های بازار ایران — مشابه اپ Chand برای iOS

## امکانات

- نمایش ۳ قیمت دلخواه در ویجت ۲×۲
- آپدیت خودکار هر ۳۰ دقیقه
- آپدیت دستی با لمس ویجت
- پشتیبانی از Dark Mode
- دو زبان فارسی و انگلیسی
- نمایش تغییر قیمت (↑↓) با رنگ قرمز/سبز

## قیمت‌های پشتیبانی شده

**ارز**: دلار، یورو، پوند، درهم، لیر، فرانک، AUD، CAD، JPY، CNY  
**طلا**: طلا ۱۸ عیار، سکه بهار آزادی، نیم سکه، ربع سکه  
**کریپتو**: BTC، USDT، ETH، LTC، BNB، BCH، EOS

## نمایش قیمت‌ها

- ارزها: به تومان (مثلاً `۱۶۶,۶۵۰`)
- طلا و سکه: میلیون تومان (مثلاً `۱۶۸.۵۹م.ت`)
- رمزارزها: به دلار (مثلاً `$104,532`)
- تتر: به تومان

## ساختار پروژه

```
ChandWidget/
├── app/src/main/
│   ├── java/com/saeed/chandwidget/
│   │   ├── data/
│   │   │   ├── PriceItem.java       # مدل هر آیتم قیمتی
│   │   │   ├── PriceRegistry.java   # لیست کامل آیتم‌ها
│   │   │   ├── PriceData.java       # داده قیمت + تغییر
│   │   │   └── TgjuApi.java         # فراخوانی API سایت TGJU
│   │   ├── widget/
│   │   │   ├── PriceWidgetProvider.java  # AppWidgetProvider اصلی
│   │   │   └── PriceUpdateService.java   # سرویس fetch در background
│   │   ├── config/
│   │   │   └── WidgetConfigActivity.java # صفحه تنظیم ویجت
│   │   └── util/
│   │       ├── Prefs.java           # SharedPreferences
│   │       └── Formatter.java       # فرمت‌دهی قیمت
│   └── res/
│       ├── layout/
│       │   ├── widget_layout.xml    # UI ویجت
│       │   ├── activity_config.xml  # UI تنظیمات
│       │   └── item_price_selector.xml
│       └── xml/widget_info.xml      # تعریف ویجت
├── .github/workflows/build.yml      # GitHub Actions CI
└── README.md
```

## API

از [TGJU](https://tgju.org) استفاده می‌شه:
```
https://api.tgju.org/v1/market/indicator/summary-table-data/{key}
```

## Build

```bash
./gradlew assembleDebug
```
