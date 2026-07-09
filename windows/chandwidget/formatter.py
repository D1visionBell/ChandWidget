"""
Direct port of Formatter.java from the Android app.
"""
from .registry import PriceType, PriceItem

_PERSIAN_GROUP_SEP = "،"
_PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"


def _group_thousands(int_str: str, sep: str) -> str:
    neg = int_str.startswith("-")
    if neg:
        int_str = int_str[1:]
    parts = []
    while len(int_str) > 3:
        parts.insert(0, int_str[-3:])
        int_str = int_str[:-3]
    parts.insert(0, int_str)
    out = sep.join(parts)
    return ("-" + out) if neg else out


def _fmt_int(value: float, persian: bool) -> str:
    s = f"{int(round(value)):d}"
    return _group_thousands(s, _PERSIAN_GROUP_SEP if persian else ",")


def _fmt_decimal(value: float, decimals: int, persian: bool) -> str:
    s = f"{value:,.{decimals}f}"
    int_part, _, frac_part = s.partition(".")
    int_part = int_part.replace(",", "")
    grouped = _group_thousands(int_part, _PERSIAN_GROUP_SEP if persian else ",")
    return f"{grouped}.{frac_part}" if decimals > 0 else grouped


def format_price(price: float, ptype: PriceType, persian: bool) -> str:
    if ptype == PriceType.CURRENCY_TOMAN:
        return _fmt_int(price, persian)
    if ptype == PriceType.GOLD_TOMAN:
        return _format_gold_toman(price, persian)
    if ptype in (PriceType.CRYPTO_USD, PriceType.GOLD_USD):
        return _format_crypto_usd(price, persian)
    return str(int(price))


def _format_gold_toman(price: float, persian: bool) -> str:
    million = price / 1_000_000.0
    if million >= 1.0:
        return _fmt_decimal(million, 2, persian) + ("م.ت" if persian else "M")
    else:
        return _fmt_decimal(price / 1000.0, 1, persian) + ("ه.ت" if persian else "K")


def _format_crypto_usd(price: float, persian: bool) -> str:
    if price >= 1000:
        return "$" + _fmt_int(price, persian)
    elif price >= 1:
        return "$" + _fmt_decimal(price, 2, persian)
    else:
        return "$" + _fmt_decimal(price, 4, persian)


def _fmt_compact(v: float) -> str:
    # Always Latin digits/format regardless of language, like Formatter.fmt() in Java
    if v == int(v):
        return f"{int(v):,}"
    s = f"{v:,.2f}".rstrip("0").rstrip(".")
    return s


def format_change(change: float, ptype: PriceType, persian: bool) -> str:
    if change == 0:
        return "—"
    sign = "↑" if change > 0 else "↓"
    abs_v = abs(change)
    if ptype in (PriceType.GOLD_TOMAN, PriceType.CURRENCY_TOMAN):
        if abs_v >= 1_000_000:
            val = _fmt_compact(abs_v / 1_000_000) + "M"
        elif abs_v >= 1000:
            val = _fmt_compact(abs_v / 1000) + "K"
        else:
            val = _fmt_compact(abs_v)
    elif ptype in (PriceType.CRYPTO_USD, PriceType.GOLD_USD):
        if abs_v >= 1000:
            val = "$" + _fmt_compact(abs_v / 1000) + "K"
        else:
            val = "$" + _fmt_compact(abs_v)
    else:
        val = _fmt_compact(abs_v)
    return sign + val


def contains_rtl(s: str) -> bool:
    if not s:
        return False
    for c in s:
        cp = ord(c)
        if (0x0590 <= cp <= 0x08FF) or (0xFB1D <= cp <= 0xFDFF) or (0xFE70 <= cp <= 0xFEFF):
            return True
    return False


def to_persian_digits(s: str) -> str:
    out = []
    for c in s:
        if "0" <= c <= "9":
            out.append(_PERSIAN_DIGITS[ord(c) - ord("0")])
        else:
            out.append(c)
    return "".join(out)


def short_label(item: PriceItem, persian: bool) -> str:
    """Compact label for narrow widget rows (2x2 3-row) — mirrors Formatter.shortLabel()."""
    if not persian:
        return item.symbol_en
    fa = item.symbol_fa
    name = item.name_fa
    if fa and fa != item.symbol_en:
        return fa
    if not name:
        return fa
    sp = name.find(" ")
    return name[:sp] if sp > 0 else name
