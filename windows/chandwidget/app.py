import os
import sys
import time

from PySide6.QtCore import QTimer, Qt
from PySide6.QtGui import QIcon, QPixmap, QPainter, QColor, QFont
from PySide6.QtWidgets import QApplication, QSystemTrayIcon, QMenu

from . import registry as R
from .settings import Settings, set_startup_enabled
from .refresh import RefreshManager
from .widget_window import WidgetWindow
from .main_window import MainWindow


def _icon_path() -> str:
    """Resolves resources/icon.ico both when running from source and when
    running as a frozen PyInstaller exe. os.path.dirname(__file__) alone is
    NOT reliable once frozen: PyInstaller embeds pure-Python modules like
    this one inside the bundled archive rather than extracting them to
    disk, so __file__ doesn't point at a real, existing directory in that
    case. Data files declared via PyInstaller's `datas` (see build.spec)
    ARE extracted at runtime, to sys._MEIPASS — so that's the path to use
    once frozen (mirrors resources_util.py in the ShamsiCalWidget project)."""
    if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
        base = os.path.join(sys._MEIPASS, "chandwidget", "resources")
    else:
        base = os.path.join(os.path.dirname(__file__), "resources")
    return os.path.join(base, "icon.ico")


def _make_tray_icon() -> QIcon:
    """Loads the bundled app icon (resources/icon.ico). Falls back to a
    generated placeholder circle if the resource file is somehow missing,
    so the app still runs even from a stripped-down checkout."""
    path = _icon_path()
    if os.path.exists(path):
        icon = QIcon(path)
        if not icon.isNull():
            return icon

    size = 64
    pix = QPixmap(size, size)
    pix.fill(QColor(0, 0, 0, 0))
    p = QPainter(pix)
    p.setRenderHint(QPainter.Antialiasing)
    p.setBrush(QColor("#1C1C1E"))
    p.setPen(QColor("#F2F2F7"))
    p.drawEllipse(2, 2, size - 4, size - 4)
    p.setPen(QColor("#F2F2F7"))
    f = QFont()
    f.setBold(True)
    f.setPointSize(28)
    p.setFont(f)
    p.drawText(pix.rect(), Qt.AlignCenter, "چ")
    p.end()
    return QIcon(pix)


class ChandWidgetApp:
    def __init__(self):
        self.qapp = QApplication(sys.argv)
        self.qapp.setQuitOnLastWindowClosed(False)

        self.app_icon = _make_tray_icon()
        self.qapp.setWindowIcon(self.app_icon)

        self.settings = Settings()
        self.refresh_mgr = RefreshManager()

        self.widget = WidgetWindow(dark=self.settings.get_dark_mode())
        self.main_win = MainWindow(self.settings)
        self.main_win.setWindowIcon(self.app_icon)

        self._wire_signals()
        self._place_widget_initial()
        self.widget.show()

        self.timer = QTimer()
        self.timer.timeout.connect(self._do_refresh)
        self.timer.start(self.settings.get_refresh_seconds() * 1000)

        self._setup_tray()

        # First paint from cache (instant), then hit the network.
        self._load_from_cache()
        self._do_refresh()

    # -- wiring --
    def _wire_signals(self):
        w, m, s = self.widget, self.main_win, self.settings

        w.clicked.connect(self._show_main_window)
        w.open_requested.connect(self._show_main_window)
        w.refresh_requested.connect(self._do_refresh)
        w.quit_requested.connect(self.quit)
        w.toggle_top_requested.connect(self._set_always_on_top)
        w.toggle_startup_requested.connect(self._set_startup)
        w.toggle_dark_requested.connect(self._set_dark_mode)
        w.moved.connect(lambda x, y: self.settings.set_widget_pos(x, y))

        m.refresh_requested.connect(self._do_refresh)
        m.language_toggled.connect(self._set_language)
        m.slot_changed.connect(self._set_slot)
        m.dark_mode_toggled.connect(self._set_dark_mode)

        self.refresh_mgr.price_updated.connect(self._on_price_updated)

    def _place_widget_initial(self):
        pos = self.settings.get_widget_pos()
        if pos:
            self.widget.move(pos[0], pos[1])
        else:
            screen = self.qapp.primaryScreen().availableGeometry()
            self.widget.move(screen.right() - self.widget.width() - 24, 40)

    def _load_from_cache(self):
        persian = self.settings.is_persian()
        for i, key in enumerate(self.settings.get_slots()):
            price, change = self.settings.get_cached(key)
            self.widget.update_slot(i, key, price, change, persian)
        for item in R.get_all():
            price, change = self.settings.get_cached(item.key)
            if price:
                self.main_win.update_price(item.key, price, change)

    # -- refresh --
    def _do_refresh(self):
        keys = set(self.settings.get_slots())
        # Also refresh everything visible in the main window if it's open,
        # so the full list stays live without a separate timer.
        if self.main_win.isVisible():
            keys.update(R.ALL.keys())
        self.refresh_mgr.refresh(list(keys))

    def _on_price_updated(self, key: str, price: float, change: float):
        self.settings.cache_price(key, price, change)
        self.settings.set_cache_time(int(time.time() * 1000))

        persian = self.settings.is_persian()
        for i, slot_key in enumerate(self.settings.get_slots()):
            if slot_key == key:
                self.widget.update_slot(i, key, price, change, persian)
        self.main_win.update_price(key, price, change)

    # -- main window --
    def _show_main_window(self):
        self.main_win.show()
        self.main_win.raise_()
        self.main_win.activateWindow()
        self._do_refresh()

    # -- settings changes from UI --
    def _set_language(self, persian: bool):
        self.settings.set_language(persian)
        self.main_win.apply_language_changed()
        for i, key in enumerate(self.settings.get_slots()):
            price, change = self.settings.get_cached(key)
            self.widget.update_slot(i, key, price, change, persian)
        # The line above only re-renders the widget's 3 rows. The main
        # window's full table also needs every already-cached price/change
        # re-rendered in the new language — otherwise it keeps showing the
        # old language's digits until the next network refresh happens to
        # land, which is exactly the bug that was reported.
        for item in R.get_all():
            price, change = self.settings.get_cached(item.key)
            if price:
                self.main_win.update_price(item.key, price, change)

    def _set_slot(self, slot: int, key: str):
        self.settings.set_slot(slot, key)
        price, change = self.settings.get_cached(key)
        self.widget.update_slot(slot, key, price, change, self.settings.is_persian())
        self._do_refresh()

    def _set_always_on_top(self, on: bool):
        self.settings.set_always_on_top(on)
        flags = self.widget.windowFlags()
        if on:
            flags |= Qt.WindowStaysOnTopHint
        else:
            flags &= ~Qt.WindowStaysOnTopHint
        self.widget.setWindowFlags(flags)
        self.widget.show()

    def _set_startup(self, on: bool):
        self.settings.set_run_at_startup(on)
        set_startup_enabled(on)

    def _set_dark_mode(self, on: bool):
        self.settings.set_dark_mode(on)
        self.widget.set_dark(on)
        self.main_win.set_dark(on)

    # -- tray --
    def _setup_tray(self):
        self.tray = QSystemTrayIcon(self.app_icon)
        self.tray.setToolTip("Chand Widget")
        menu = QMenu()
        menu.addAction("باز کردن برنامه", self._show_main_window)
        menu.addAction("نمایش/مخفی کردن ویجت", self._toggle_widget_visible)
        menu.addAction("به‌روزرسانی", self._do_refresh)
        menu.addSeparator()
        menu.addAction("خروج", self.quit)
        self.tray.setContextMenu(menu)
        self.tray.activated.connect(self._on_tray_activated)
        self.tray.show()

    def _on_tray_activated(self, reason):
        if reason == QSystemTrayIcon.Trigger:
            self._toggle_widget_visible()

    def _toggle_widget_visible(self):
        self.widget.setVisible(not self.widget.isVisible())

    # -- lifecycle --
    def _save_widget_pos(self):
        pos = self.widget.pos()
        self.settings.set_widget_pos(pos.x(), pos.y())

    def quit(self):
        self._save_widget_pos()
        self.refresh_mgr.shutdown()
        self.qapp.quit()

    def run(self):
        exit_code = self.qapp.exec()
        sys.exit(exit_code)
