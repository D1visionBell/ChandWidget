"""
Main application window — shown when the floating widget is clicked.
Top: 3 pickers choosing which symbol occupies each widget row (same idea as
WidgetConfigActivity on Android). Below: a scrolling grid of price "cards",
one per symbol, styled after the macOS Chand app (rounded white tiles, big
bold price, small colored change badge) instead of a plain table.
"""
from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QColor
from PySide6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QGridLayout, QLabel,
    QPushButton, QScrollArea, QFrame, QComboBox, QLineEdit, QGroupBox,
    QGraphicsDropShadowEffect, QSizePolicy,
)

from . import registry as R
from . import formatter as F

ROW_LABELS_FA = ["ردیف ۱", "ردیف ۲", "ردیف ۳"]
ROW_LABELS_EN = ["Row 1", "Row 2", "Row 3"]

COLOR_UP = "#E53935"
COLOR_DOWN = "#43A047"
CARD_COLUMNS = 3

_STYLE_LIGHT = """
#centralWidget { background: #F2F2F7; }
#headerTitle { color: #1C1C1E; font-size: 20px; font-weight: 700; }
#headerSubtitle { color: #8E8E93; font-size: 12px; }
#searchBox {
    background: #FFFFFF; border: 1px solid #E1E1E6; border-radius: 15px;
    padding: 7px 14px; font-size: 13px; color: #1C1C1E;
}
#searchBox:focus { border: 1px solid #007AFF; }
#pillButton {
    background: #FFFFFF; border: 1px solid #E1E1E6; border-radius: 15px;
    padding: 7px 16px; font-size: 12px; font-weight: 600; color: #1C1C1E;
}
#pillButton:hover { background: #E9E9EE; }
#refreshButton {
    background: #007AFF; border: none; border-radius: 15px;
    padding: 7px 16px; font-size: 12px; font-weight: 600; color: #FFFFFF;
}
#refreshButton:hover { background: #0068D6; }
#slotGroup {
    background: #FFFFFF; border-radius: 16px; border: none;
    margin-top: 12px; padding-top: 14px; font-size: 13px;
    font-weight: 700; color: #1C1C1E;
}
#slotGroup::title { subcontrol-origin: margin; left: 14px; padding: 0 4px; }
#slotGroup QLabel { color: #636366; font-weight: 600; font-size: 12px; }
#slotGroup QComboBox {
    border: 1px solid #E1E1E6; border-radius: 9px; padding: 4px 8px;
    background: #FAFAFC; min-height: 22px;
}
QScrollArea { border: none; background: transparent; }
#cardsContainer { background: transparent; }
#priceCard { background: #FFFFFF; border-radius: 16px; }
#cardEmoji { font-size: 22px; color: #1C1C1E; }
#cardName { color: #8E8E93; font-size: 11px; font-weight: 600; }
#cardChange { font-size: 11px; font-weight: 700; }
#cardPrice { color: #1C1C1E; font-size: 17px; font-weight: 700; }
#footer { color: #8E8E93; padding: 6px 0; font-size: 11px; }
"""

# Same layout/rules as light, only the palette changes — kept as a
# parallel sheet (rather than deriving colors at runtime) so every color
# stays reviewable in one place, same approach as CARD_BG_LIGHT/DARK in
# widget_window.py.
_STYLE_DARK = """
#centralWidget { background: #1C1C1E; }
#headerTitle { color: #FFFFFF; font-size: 20px; font-weight: 700; }
#headerSubtitle { color: #98989D; font-size: 12px; }
#searchBox {
    background: #2C2C2E; border: 1px solid #3A3A3C; border-radius: 15px;
    padding: 7px 14px; font-size: 13px; color: #FFFFFF;
}
#searchBox:focus { border: 1px solid #0A84FF; }
#pillButton {
    background: #2C2C2E; border: 1px solid #3A3A3C; border-radius: 15px;
    padding: 7px 16px; font-size: 12px; font-weight: 600; color: #FFFFFF;
}
#pillButton:hover { background: #3A3A3C; }
#refreshButton {
    background: #0A84FF; border: none; border-radius: 15px;
    padding: 7px 16px; font-size: 12px; font-weight: 600; color: #FFFFFF;
}
#refreshButton:hover { background: #3396FF; }
#slotGroup {
    background: #2C2C2E; border-radius: 16px; border: none;
    margin-top: 12px; padding-top: 14px; font-size: 13px;
    font-weight: 700; color: #FFFFFF;
}
#slotGroup::title { subcontrol-origin: margin; left: 14px; padding: 0 4px; }
#slotGroup QLabel { color: #98989D; font-weight: 600; font-size: 12px; }
#slotGroup QComboBox {
    border: 1px solid #3A3A3C; border-radius: 9px; padding: 4px 8px;
    background: #1C1C1E; color: #FFFFFF; min-height: 22px;
}
QScrollArea { border: none; background: transparent; }
#cardsContainer { background: transparent; }
#priceCard { background: #2C2C2E; border-radius: 16px; }
#cardEmoji { font-size: 22px; color: #FFFFFF; }
#cardName { color: #98989D; font-size: 11px; font-weight: 600; }
#cardChange { font-size: 11px; font-weight: 700; }
#cardPrice { color: #FFFFFF; font-size: 17px; font-weight: 700; }
#footer { color: #98989D; padding: 6px 0; font-size: 11px; }
"""


class _PriceCard(QFrame):
    """One rounded tile in the price grid — mirrors the macOS app's card
    look: emoji at the leading edge, localized name at the trailing edge,
    then a small colored change badge sitting above the big bold price."""

    def __init__(self, item, parent=None):
        super().__init__(parent)
        self.item = item
        self.setObjectName("priceCard")
        self.setAttribute(Qt.WA_StyledBackground, True)
        self.setMinimumHeight(92)

        shadow = QGraphicsDropShadowEffect(self)
        shadow.setBlurRadius(14)
        shadow.setOffset(0, 2)
        shadow.setColor(QColor(0, 0, 0, 28))
        self.setGraphicsEffect(shadow)

        outer = QVBoxLayout(self)
        outer.setContentsMargins(14, 10, 14, 10)
        outer.setSpacing(6)

        top = QHBoxLayout()
        top.setSpacing(6)
        self.emoji_lbl = QLabel(item.emoji)
        self.emoji_lbl.setObjectName("cardEmoji")
        self.emoji_lbl.setAlignment(Qt.AlignLeft | Qt.AlignVCenter)
        top.addWidget(self.emoji_lbl)
        top.addStretch(1)
        self.name_lbl = QLabel()
        self.name_lbl.setObjectName("cardName")
        self.name_lbl.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        top.addWidget(self.name_lbl)
        outer.addLayout(top)

        outer.addStretch(1)

        self.chg_lbl = QLabel("--")
        self.chg_lbl.setObjectName("cardChange")
        self.chg_lbl.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        outer.addWidget(self.chg_lbl)

        self.price_lbl = QLabel("---")
        self.price_lbl.setObjectName("cardPrice")
        self.price_lbl.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        outer.addWidget(self.price_lbl)

    def set_name(self, text: str, persian: bool):
        self.name_lbl.setLayoutDirection(Qt.RightToLeft if persian else Qt.LeftToRight)
        self.name_lbl.setText(text)

    def set_price(self, price_str: str, chg_str: str, up: bool):
        self.price_lbl.setText(price_str)
        self.chg_lbl.setText(chg_str)
        self.chg_lbl.setStyleSheet(f"color: {COLOR_UP if up else COLOR_DOWN};")


class MainWindow(QMainWindow):
    refresh_requested = Signal()
    language_toggled = Signal(bool)     # new persian value
    slot_changed = Signal(int, str)     # slot index, key
    dark_mode_toggled = Signal(bool)    # new dark-mode value

    def __init__(self, settings):
        super().__init__()
        self.settings = settings
        self.setWindowTitle("Chand")
        self.resize(760, 720)
        self.setMinimumSize(520, 480)
        self.setStyleSheet(_STYLE_DARK if self.settings.get_dark_mode() else _STYLE_LIGHT)

        central = QWidget()
        central.setObjectName("centralWidget")
        self.setCentralWidget(central)
        root = QVBoxLayout(central)
        root.setContentsMargins(18, 16, 18, 12)
        root.setSpacing(10)

        # -- header --
        header = QVBoxLayout()
        header.setSpacing(0)
        self.header_title = QLabel("Chand?!")
        self.header_title.setObjectName("headerTitle")
        header.addWidget(self.header_title)
        self.header_subtitle = QLabel()
        self.header_subtitle.setObjectName("headerSubtitle")
        header.addWidget(self.header_subtitle)
        root.addLayout(header)

        # -- top bar --
        top_bar = QHBoxLayout()
        top_bar.setSpacing(8)
        self.search_box = QLineEdit()
        self.search_box.setObjectName("searchBox")
        self.search_box.setPlaceholderText("جستجو…")
        self.search_box.textChanged.connect(self._apply_filter)
        top_bar.addWidget(self.search_box, 1)

        self.lang_btn = QPushButton()
        self.lang_btn.setObjectName("pillButton")
        self.lang_btn.clicked.connect(self._on_lang_clicked)
        top_bar.addWidget(self.lang_btn)

        self.dark_btn = QPushButton()
        self.dark_btn.setObjectName("pillButton")
        self.dark_btn.clicked.connect(self._on_dark_clicked)
        top_bar.addWidget(self.dark_btn)

        self.refresh_btn = QPushButton("↻ به‌روزرسانی")
        self.refresh_btn.setObjectName("refreshButton")
        self.refresh_btn.clicked.connect(self.refresh_requested.emit)
        top_bar.addWidget(self.refresh_btn)
        root.addLayout(top_bar)

        # -- widget row pickers --
        self.slot_group = QGroupBox("نمادهای ویجت دسکتاپ")
        self.slot_group.setObjectName("slotGroup")
        slot_layout = QGridLayout(self.slot_group)
        slot_layout.setContentsMargins(14, 4, 14, 12)
        slot_layout.setHorizontalSpacing(10)
        slot_layout.setVerticalSpacing(6)
        self.slot_label_widgets = []
        self.slot_combos = []
        items = R.get_all()
        for i in range(3):
            lbl = QLabel()
            combo = QComboBox()
            for item in items:
                combo.addItem(self._combo_text(item), userData=item.key)
            current_key = self.settings.get_slot(i)
            idx = combo.findData(current_key)
            combo.setCurrentIndex(idx if idx >= 0 else 0)
            combo.currentIndexChanged.connect(
                lambda _idx, slot=i, c=combo: self.slot_changed.emit(slot, c.currentData())
            )
            slot_layout.addWidget(lbl, i, 0)
            slot_layout.addWidget(combo, i, 1)
            self.slot_label_widgets.append(lbl)
            self.slot_combos.append(combo)
        root.addWidget(self.slot_group)

        # -- price card grid (scrollable) --
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        cards_container = QWidget()
        cards_container.setObjectName("cardsContainer")
        self.cards_grid = QGridLayout(cards_container)
        self.cards_grid.setContentsMargins(0, 4, 0, 4)
        self.cards_grid.setHorizontalSpacing(12)
        self.cards_grid.setVerticalSpacing(12)
        scroll.setWidget(cards_container)
        root.addWidget(scroll, 1)

        self._cards = {}
        self._build_cards(items)
        self._refresh_lang_texts()
        self._refresh_dark_text()

        # -- footer --
        footer = QLabel("نسخه 1.0.0  -  توسعه و طراحی توسط حمید قاسمی")
        footer.setObjectName("footer")
        footer.setAlignment(Qt.AlignCenter)
        root.addWidget(footer)

    # -- building / filtering --
    def _combo_text(self, item) -> str:
        if self.settings.is_persian():
            return f"{item.emoji}  {item.name_fa}"
        return f"{item.emoji}  {item.name_en}"

    def _build_cards(self, items):
        for idx, item in enumerate(items):
            card = _PriceCard(item)
            card.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
            row, col = divmod(idx, CARD_COLUMNS)
            self.cards_grid.addWidget(card, row, col)
            self._cards[item.key] = card
        for c in range(CARD_COLUMNS):
            self.cards_grid.setColumnStretch(c, 1)

    def _name_for(self, item) -> str:
        if self.settings.is_persian():
            return item.name_fa
        return item.name_en

    def _apply_filter(self, text: str):
        text = text.strip().lower()
        for key, card in self._cards.items():
            item = R.get(key)
            haystack = f"{item.name_en} {item.name_fa} {item.symbol_en} {item.symbol_fa}".lower()
            card.setVisible(text in haystack)

    # -- language --
    def _refresh_lang_texts(self):
        persian = self.settings.is_persian()
        self.lang_btn.setText("English" if persian else "فارسی")
        self.setLayoutDirection(Qt.RightToLeft if persian else Qt.LeftToRight)
        self.header_subtitle.setText(
            "قیمت لحظه‌ای ارز، طلا و ارز دیجیتال" if persian
            else "Live currency, gold & crypto prices"
        )
        self.refresh_btn.setText("↻ به‌روزرسانی" if persian else "↻ Refresh")
        self.search_box.setPlaceholderText("جستجو…" if persian else "Search…")
        labels = ROW_LABELS_FA if persian else ROW_LABELS_EN
        for lbl, text in zip(self.slot_label_widgets, labels):
            lbl.setText(text)
        self.slot_group.setTitle("نمادهای ویجت دسکتاپ" if persian else "Desktop widget symbols")
        for key, card in self._cards.items():
            card.set_name(self._name_for(R.get(key)), persian)
        self._refresh_dark_text()

    def _on_lang_clicked(self):
        self.language_toggled.emit(not self.settings.is_persian())

    def _refresh_dark_text(self):
        persian = self.settings.is_persian()
        if self.settings.get_dark_mode():
            self.dark_btn.setText("☀️ روشن" if persian else "☀️ Light")
        else:
            self.dark_btn.setText("🌙 تیره" if persian else "🌙 Dark")

    def _on_dark_clicked(self):
        self.dark_mode_toggled.emit(not self.settings.get_dark_mode())

    def set_dark(self, dark: bool):
        self.setStyleSheet(_STYLE_DARK if dark else _STYLE_LIGHT)
        self._refresh_dark_text()

    def apply_language_changed(self):
        """Call after settings.set_language() to refresh all labels/text in place."""
        self._refresh_lang_texts()
        for combo in self.slot_combos:
            current = combo.currentData()
            combo.blockSignals(True)
            for i in range(combo.count()):
                item = R.get(combo.itemData(i))
                combo.setItemText(i, self._combo_text(item))
            idx = combo.findData(current)
            combo.setCurrentIndex(idx if idx >= 0 else 0)
            combo.blockSignals(False)

    def sync_slot_combos(self):
        for i, combo in enumerate(self.slot_combos):
            key = self.settings.get_slot(i)
            idx = combo.findData(key)
            combo.blockSignals(True)
            combo.setCurrentIndex(idx if idx >= 0 else 0)
            combo.blockSignals(False)

    # -- price updates --
    def update_price(self, key: str, price: float, change: float):
        card = self._cards.get(key)
        if not card:
            return
        item = R.get(key)
        persian = self.settings.is_persian()
        price_str = F.format_price(price, item.type, persian)
        chg_str = F.format_change(change, item.type, persian)
        if persian:
            price_str = F.to_persian_digits(price_str)
            chg_str = F.to_persian_digits(chg_str)
        card.set_price(price_str, chg_str, up=change >= 0)
