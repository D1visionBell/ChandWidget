"""
Direct port of PriceItem.java + PriceRegistry.java from the Android app.
Keep this list in sync with the Android registry — same keys, same order.
"""
from dataclasses import dataclass
from enum import Enum
from collections import OrderedDict


class PriceType(Enum):
    CURRENCY_TOMAN = "CURRENCY_TOMAN"
    GOLD_TOMAN = "GOLD_TOMAN"
    CRYPTO_USD = "CRYPTO_USD"
    GOLD_USD = "GOLD_USD"


@dataclass(frozen=True)
class PriceItem:
    key: str
    name_en: str
    name_fa: str
    symbol_en: str
    symbol_fa: str
    emoji: str
    type: PriceType


# Keys (mirrors PriceItem.java constants)
KEY_USD = "price_dollar_rl"
KEY_EUR = "price_eur"
KEY_GBP = "price_gbp"
KEY_AED = "price_aed"
KEY_TRY = "price_try"
KEY_CHF = "price_chf"
KEY_AUD = "price_aud"
KEY_CAD = "price_cad"
KEY_JPY = "price_jpy"
KEY_CNY = "price_cny"

KEY_GOLD18 = "geram18"
KEY_SEKKE = "sekeb"
KEY_NIM = "nim"
KEY_ROB = "rob"
KEY_GOLD_OUNCE = "ons"

KEY_BTC = "crypto-bitcoin"
KEY_ETH = "crypto-ethereum"
KEY_LTC = "crypto-litecoin"
KEY_BNB = "crypto-binance-coin"
KEY_BCH = "crypto-bitcoin-cash"
KEY_EOS = "crypto-eos"
KEY_SOL = "crypto-solana"
KEY_XRP = "crypto-ripple"
KEY_TRX = "crypto-tron"
KEY_DOGE = "crypto-dogecoin"
KEY_USDT = "crypto-tether"

ALL: "OrderedDict[str, PriceItem]" = OrderedDict()


def _add(key, en, fa, sym_en, sym_fa, emoji, type_):
    ALL[key] = PriceItem(key, en, fa, sym_en, sym_fa, emoji, type_)


# Currencies
# NOTE: flag emojis (🇺🇸 etc.) are regional-indicator letter *pairs*; if the
# renderer/font doesn't compose them into a flag glyph, Unicode's defined
# fallback is to show the raw two letters instead (exactly the "US" seen in
# the screenshot on Windows). Using plain currency symbols avoids that
# platform-dependent composition step entirely.
_add(KEY_USD, "US Dollar", "دلار آمریکا", "USD", "USD", "$", PriceType.CURRENCY_TOMAN)
_add(KEY_EUR, "Euro", "یورو", "EUR", "EUR", "€", PriceType.CURRENCY_TOMAN)
_add(KEY_GBP, "British Pound", "پوند انگلیس", "GBP", "GBP", "£", PriceType.CURRENCY_TOMAN)
_add(KEY_AED, "UAE Dirham", "درهم امارات", "AED", "AED", "AED", PriceType.CURRENCY_TOMAN)
_add(KEY_TRY, "Turkish Lira", "لیر ترکیه", "TRY", "TRY", "₺", PriceType.CURRENCY_TOMAN)
_add(KEY_CHF, "Swiss Franc", "فرانک سوئیس", "CHF", "CHF", "CHF", PriceType.CURRENCY_TOMAN)
_add(KEY_AUD, "Australian Dollar", "دلار استرالیا", "AUD", "AUD", "A$", PriceType.CURRENCY_TOMAN)
_add(KEY_CAD, "Canadian Dollar", "دلار کانادا", "CAD", "CAD", "C$", PriceType.CURRENCY_TOMAN)
_add(KEY_JPY, "Japanese Yen", "ین ژاپن", "JPY", "JPY", "¥", PriceType.CURRENCY_TOMAN)
_add(KEY_CNY, "Chinese Yuan", "یوان چین", "CNY", "CNY", "CN¥", PriceType.CURRENCY_TOMAN)

# Gold
_add(KEY_GOLD18, "Gold 18K", "طلا ۱۸ عیار", "GOLD", "طلا", "🥇", PriceType.GOLD_TOMAN)
_add(KEY_SEKKE, "Azadi Coin", "سکه بهار آزادی", "COIN", "سکه", "🪙", PriceType.GOLD_TOMAN)
_add(KEY_NIM, "Half Coin", "نیم سکه", "HALF", "نیم", "🪙", PriceType.GOLD_TOMAN)
_add(KEY_ROB, "Quarter Coin", "ربع سکه", "QTR", "ربع", "🪙", PriceType.GOLD_TOMAN)
_add(KEY_GOLD_OUNCE, "Gold Ounce", "اونس طلا", "XAU", "اونس", "🥇", PriceType.GOLD_USD)

# Crypto
_add(KEY_BTC, "Bitcoin", "بیتکوین", "BTC", "BTC", "₿", PriceType.CRYPTO_USD)
_add(KEY_ETH, "Ethereum", "اتریوم", "ETH", "ETH", "Ξ", PriceType.CRYPTO_USD)
_add(KEY_LTC, "Litecoin", "لایت کوین", "LTC", "LTC", "Ł", PriceType.CRYPTO_USD)
_add(KEY_BNB, "Binance", "بایننس", "BNB", "BNB", "🔶", PriceType.CRYPTO_USD)
_add(KEY_BCH, "Bitcoin Cash", "بیتکوین کش", "BCH", "BCH", "💠", PriceType.CRYPTO_USD)
_add(KEY_EOS, "EOS", "EOS", "EOS", "EOS", "⚫", PriceType.CRYPTO_USD)
_add(KEY_SOL, "Solana", "سولانا", "SOL", "SOL", "◎", PriceType.CRYPTO_USD)
_add(KEY_XRP, "XRP", "ریپل", "XRP", "XRP", "✕", PriceType.CRYPTO_USD)
_add(KEY_TRX, "Tron", "ترون", "TRX", "TRX", "🔺", PriceType.CRYPTO_USD)
_add(KEY_DOGE, "Dogecoin", "دوج کوین", "DOGE", "DOGE", "🐕", PriceType.CRYPTO_USD)
_add(KEY_USDT, "Tether", "تتر", "USDT", "USDT", "🟢", PriceType.CURRENCY_TOMAN)


def get_all():
    return list(ALL.values())


def get(key: str):
    return ALL.get(key)
