"""
Direct port of TgjuApi.java from the Android app.

Non-crypto (currencies, gold, coins):
    GET https://api.tgju.org/v1/market/tmp?keys=KEY
    response.indicators[].{ name, p (Rial), d (Rial), dt ("high"|"low") }
    Exception: "ons" (gold ounce) is already quoted in USD, not Rial.

Crypto BTC/ETH/BNB/SOL via widget endpoint:
    GET https://api.tgju.org/v1/widget/tmp?keys=398096,398097,398115,535605

Crypto LTC/BCH/EOS/XRP/TRX/DOGE fallback:
    GET https://api.tgju.org/v1/market/indicator/summary-table-data/KEY

Tether (USDT), in Toman like every other currency:
    GET https://call4.tgju.org/ajax.json?rev=...
    response.current["crypto-tether-irr"].p (Rial)
"""
import logging
from dataclasses import dataclass
from typing import Optional

import requests
import urllib3

from . import registry as R

# Only fires on the verify=False retry path above; the primary request is
# always verified normally.
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

log = logging.getLogger("tgju_api")

TIMEOUT = 12  # seconds

TMP_URL = "https://api.tgju.org/v1/market/tmp?keys="
WIDGET_URL = "https://api.tgju.org/v1/widget/tmp?keys=398096,398097,398115,535605"
SUMMARY_URL = "https://api.tgju.org/v1/market/indicator/summary-table-data/"
CALL4_URL = (
    "https://call4.tgju.org/ajax.json?rev="
    "NUInYHDaLqVjbIse8P1gUgsBjJv0zV4MhEX9NwN3U0tYFAZwZ8iYWMHqm5dN"
)

USD_TMP_KEYS = {R.KEY_GOLD_OUNCE}
WIDGET_KEYS = {R.KEY_BTC, R.KEY_ETH, R.KEY_BNB, R.KEY_SOL}
SUMMARY_KEYS = {R.KEY_LTC, R.KEY_BCH, R.KEY_EOS, R.KEY_XRP, R.KEY_TRX, R.KEY_DOGE}

_HEADERS = {"User-Agent": "ChandWidget-Win/1.0", "Accept": "application/json"}


@dataclass
class PriceData:
    key: str
    price: float
    change: float


def _parse_double(s) -> float:
    try:
        return float(str(s).replace(",", "").strip())
    except (ValueError, TypeError):
        return 0.0


def _http_get_json(url: str):
    try:
        resp = requests.get(url, headers=_HEADERS, timeout=TIMEOUT)
        if resp.status_code != 200:
            return None
        return resp.json()
    except requests.exceptions.SSLError:
        # Same fallback used in the Tehran Market Terminal project: on some
        # networks in Iran, DPI/SSL interference breaks cert verification
        # even though the connection itself works. Retry once without
        # verification rather than failing outright.
        try:
            resp = requests.get(url, headers=_HEADERS, timeout=TIMEOUT, verify=False)
            if resp.status_code != 200:
                return None
            return resp.json()
        except Exception as e:
            log.error("httpGet retry (no verify) failed: %s (%s)", url, e)
            return None
    except Exception as e:
        log.error("httpGet error: %s (%s)", url, e)
        return None


def fetch(key: str) -> Optional[PriceData]:
    try:
        if key == R.KEY_USDT:
            return _fetch_tether_irr()
        if key in WIDGET_KEYS:
            return _fetch_from_widget(key)
        if key in SUMMARY_KEYS:
            return _fetch_from_summary(key)
        return _fetch_from_tmp(key)
    except Exception as e:
        log.error("fetch error for %s: %s", key, e)
        return None


def _fetch_tether_irr() -> Optional[PriceData]:
    data = _http_get_json(CALL4_URL)
    if not data:
        return None
    current = data.get("current") or {}
    ind = current.get("crypto-tether-irr")
    if not ind:
        log.warning("crypto-tether-irr not found in call4 feed")
        return None

    price = _parse_double(ind.get("p", "0")) / 10.0
    change = _parse_double(ind.get("d", "0")) / 10.0
    dt = ind.get("dt", "")
    change = -abs(change) if dt == "low" else abs(change)
    return PriceData(R.KEY_USDT, price, change)


def _fetch_from_tmp(key: str) -> Optional[PriceData]:
    data = _http_get_json(TMP_URL + key)
    if not data:
        return None
    indicators = data.get("response", {}).get("indicators", [])
    for ind in indicators:
        if ind.get("name", "") != key:
            continue
        price = _parse_double(ind.get("p", "0"))
        change = _parse_double(ind.get("d", "0"))
        dt = ind.get("dt", "")
        change = -abs(change) if dt == "low" else abs(change)
        if key not in USD_TMP_KEYS:
            price /= 10.0
            change /= 10.0
        return PriceData(key, price, change)
    log.warning("Key not found in tmp: %s", key)
    return None


def _fetch_from_widget(key: str) -> Optional[PriceData]:
    data = _http_get_json(WIDGET_URL)
    if data:
        indicators = data.get("response", {}).get("indicators", [])
        for ind in indicators:
            if ind.get("name", "") != key:
                continue
            price = _parse_double(ind.get("p", "0"))
            change = _parse_double(ind.get("d", "0"))
            dt = ind.get("dt", "")
            change = -abs(change) if dt == "low" else abs(change)
            return PriceData(key, price, change)
    return _fetch_from_summary(key)


def _fetch_from_summary(key: str) -> Optional[PriceData]:
    data = _http_get_json(SUMMARY_URL + key)
    if not data:
        return None
    rows = data.get("data", [])
    if not rows:
        return None
    row = rows[0]
    price = _parse_double(str(row[1]).replace(",", ""))
    change = 0.0
    try:
        change = _parse_double(str(row[3]).replace(",", ""))
    except (IndexError, ValueError):
        pass
    return PriceData(key, price, change)
