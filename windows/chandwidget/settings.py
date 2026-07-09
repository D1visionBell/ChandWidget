"""
JSON-backed settings, equivalent of Prefs.java (SharedPreferences on Android).
Stored at %APPDATA%/ChandWidget/settings.json on Windows (falls back to
~/.chandwidget on other OSes, useful for dev/testing on this machine).
"""
import json
import os
import sys
import threading

from . import registry as R

APP_NAME = "ChandWidget"

DEFAULT_SLOTS = [R.KEY_USD, R.KEY_EUR, R.KEY_GOLD18]


def _config_dir() -> str:
    appdata = os.environ.get("APPDATA")
    if appdata:
        path = os.path.join(appdata, APP_NAME)
    else:
        path = os.path.join(os.path.expanduser("~"), f".{APP_NAME.lower()}")
    os.makedirs(path, exist_ok=True)
    return path


_SETTINGS_PATH = os.path.join(_config_dir(), "settings.json")

_DEFAULTS = {
    "language_persian": True,
    "slots": DEFAULT_SLOTS,
    "widget_pos": None,          # [x, y] or None = let OS place it first run
    "always_on_top": True,
    "run_at_startup": False,
    "refresh_seconds": 60,
    "cache": {},                 # key -> {"price": .., "change": ..}
    "cache_time": 0,
}

_lock = threading.Lock()


def _load() -> dict:
    if os.path.exists(_SETTINGS_PATH):
        try:
            with open(_SETTINGS_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
            merged = dict(_DEFAULTS)
            merged.update(data)
            return merged
        except (json.JSONDecodeError, OSError):
            pass
    return dict(_DEFAULTS)


def _save(data: dict):
    with _lock:
        tmp_path = _SETTINGS_PATH + ".tmp"
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        os.replace(tmp_path, _SETTINGS_PATH)


class Settings:
    """Thin wrapper mirroring the shape of Prefs.java's static methods."""

    def __init__(self):
        self._data = _load()

    # -- language --
    def is_persian(self) -> bool:
        return bool(self._data.get("language_persian", True))

    def set_language(self, persian: bool):
        self._data["language_persian"] = persian
        _save(self._data)

    # -- slots (which symbol sits in widget row 0/1/2) --
    def get_slot(self, slot: int) -> str:
        slots = self._data.get("slots", DEFAULT_SLOTS)
        if slot < len(slots):
            return slots[slot]
        return DEFAULT_SLOTS[slot] if slot < len(DEFAULT_SLOTS) else DEFAULT_SLOTS[0]

    def set_slot(self, slot: int, key: str):
        slots = list(self._data.get("slots", DEFAULT_SLOTS))
        while len(slots) <= slot:
            slots.append(DEFAULT_SLOTS[0])
        slots[slot] = key
        self._data["slots"] = slots
        _save(self._data)

    def get_slots(self):
        return list(self._data.get("slots", DEFAULT_SLOTS))

    # -- widget window position --
    def get_widget_pos(self):
        return self._data.get("widget_pos")

    def set_widget_pos(self, x: int, y: int):
        self._data["widget_pos"] = [x, y]
        _save(self._data)

    # -- misc toggles --
    def get_always_on_top(self) -> bool:
        return bool(self._data.get("always_on_top", True))

    def set_always_on_top(self, val: bool):
        self._data["always_on_top"] = val
        _save(self._data)

    def get_run_at_startup(self) -> bool:
        return bool(self._data.get("run_at_startup", False))

    def set_run_at_startup(self, val: bool):
        self._data["run_at_startup"] = val
        _save(self._data)

    def get_refresh_seconds(self) -> int:
        return int(self._data.get("refresh_seconds", 60))

    # -- price cache (so the widget shows last-known values instantly on launch) --
    def cache_price(self, key: str, price: float, change: float):
        cache = self._data.setdefault("cache", {})
        cache[key] = {"price": price, "change": change}
        _save(self._data)

    def get_cached(self, key: str):
        entry = self._data.get("cache", {}).get(key)
        if entry:
            return entry.get("price", 0.0), entry.get("change", 0.0)
        return 0.0, 0.0

    def set_cache_time(self, millis: int):
        self._data["cache_time"] = millis
        _save(self._data)

    def get_cache_time(self) -> int:
        return int(self._data.get("cache_time", 0))


# ── Windows "run at startup" registration ──────────────────────────────────
_RUN_KEY_NAME = "ChandWidget"


def set_startup_enabled(enabled: bool, exe_path: str = None):
    """Registers/unregisters the app in
    HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run
    so it launches automatically when Windows starts. No-op on non-Windows
    platforms (useful during development on this machine)."""
    if sys.platform != "win32":
        return
    import winreg  # only available on Windows

    exe_path = exe_path or sys.executable
    key = winreg.OpenKey(
        winreg.HKEY_CURRENT_USER,
        r"Software\Microsoft\Windows\CurrentVersion\Run",
        0,
        winreg.KEY_SET_VALUE,
    )
    try:
        if enabled:
            winreg.SetValueEx(key, _RUN_KEY_NAME, 0, winreg.REG_SZ, f'"{exe_path}"')
        else:
            try:
                winreg.DeleteValue(key, _RUN_KEY_NAME)
            except FileNotFoundError:
                pass
    finally:
        winreg.CloseKey(key)
