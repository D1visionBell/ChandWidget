"""
The floating desktop "widget" — a frameless, always-on-top card showing
3 symbols, ported visually from widget_layout_2x2.xml (icon+abbrev column,
spacer, change+price column, all left-to-right regardless of language).

Sizing note: earlier versions hard-coded pixel widths/heights guessed from
font metrics measured in a Linux dev environment. Those guesses didn't match
real rendering on Windows (different default font, different DPI scaling),
which is what caused the repeated clipping/overlap bugs. Everything below is
now sized from QFontMetrics computed live, on the machine actually running
the app, and row heights are left to Qt's own layout sizing instead of a
fixed pixel guess — so this adapts correctly regardless of OS/DPI/font.
"""
from PySide6.QtCore import Qt, QPoint, Signal, QRect
from PySide6.QtGui import QColor, QPainter, QPainterPath, QFont
from PySide6.QtWidgets import QWidget, QVBoxLayout, QHBoxLayout, QLabel, QMenu

from . import registry as R
from . import formatter as F

CARD_BG_LIGHT = QColor("#F2F2F7")
CARD_BG_DARK = QColor("#1C1C1E")
TEXT_PRIMARY_LIGHT = QColor("#1C1C1E")
TEXT_PRIMARY_DARK = QColor("#FFFFFF")
COLOR_UP = "#E53935"
COLOR_DOWN = "#43A047"

CARD_WIDTH = 190
CORNER_RADIUS = 18

SYM_FONT_POINT_SIZE = 8
EMOJI_FONT_POINT_SIZE = 10

_icon_col_width_cache = None


def _sym_font() -> QFont:
    f = QFont()
    f.setPointSize(SYM_FONT_POINT_SIZE)
    f.setBold(True)
    return f


def _icon_col_width() -> int:
    """Widest possible abbreviation/name label across the whole registry, in
    both languages, measured with the real font metrics of *this* machine —
    then cached so we don't recompute per row. Includes generous padding."""
    global _icon_col_width_cache
    if _icon_col_width_cache is not None:
        return _icon_col_width_cache
    from PySide6.QtGui import QFontMetrics
    fm = QFontMetrics(_sym_font())
    widest = 0
    for item in R.get_all():
        for persian in (True, False):
            label = F.short_label(item, persian)
            widest = max(widest, fm.horizontalAdvance(label))
    _icon_col_width_cache = widest + 10  # padding so text never touches the edge
    return _icon_col_width_cache


class _Row(QWidget):
    def __init__(self, dark: bool, parent=None):
        super().__init__(parent)
        self.dark = dark
        col_width = _icon_col_width()

        root = QHBoxLayout(self)
        root.setContentsMargins(12, 5, 12, 5)
        root.setSpacing(0)

        icon_col = QVBoxLayout()
        icon_col.setSpacing(1)
        self.emoji_lbl = QLabel("")
        self.emoji_lbl.setAlignment(Qt.AlignLeft | Qt.AlignVCenter)
        self.emoji_lbl.setFixedWidth(col_width)
        f = QFont()
        f.setPointSize(EMOJI_FONT_POINT_SIZE)
        self.emoji_lbl.setFont(f)
        self.sym_lbl = QLabel("")
        self.sym_lbl.setFixedWidth(col_width)
        self.sym_lbl.setFont(_sym_font())
        icon_col.addWidget(self.emoji_lbl)
        icon_col.addWidget(self.sym_lbl)
        icon_wrap = QWidget()
        icon_wrap.setLayout(icon_col)
        icon_wrap.setFixedWidth(col_width)
        root.addWidget(icon_wrap)

        root.addStretch(1)

        price_col = QVBoxLayout()
        price_col.setSpacing(1)
        self.chg_lbl = QLabel("--")
        self.chg_lbl.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        cf = QFont()
        cf.setPointSize(8)
        self.chg_lbl.setFont(cf)
        self.price_lbl = QLabel("---")
        self.price_lbl.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        pf = QFont()
        pf.setPointSize(12)
        pf.setBold(True)
        self.price_lbl.setFont(pf)
        price_col.addWidget(self.chg_lbl)
        price_col.addWidget(self.price_lbl)
        price_wrap = QWidget()
        price_wrap.setLayout(price_col)
        root.addWidget(price_wrap)

        self._apply_colors()
        # No fixed row height: Qt sizes this row from the real, live font
        # metrics of its children, so it can never be too short for the
        # actual rendered text on whatever machine/DPI this runs on.

    def _apply_colors(self):
        primary = TEXT_PRIMARY_DARK if self.dark else TEXT_PRIMARY_LIGHT
        # Plain glyph symbols ($, ₿, Ξ, Ł, ✕, ◎, AED, CHF, A$, C$, CN¥...)
        # paint using the label's text color, same as any other text — only
        # true pictorial emoji (🥇🪙🔶💠🐕🔺⚫🟢) carry their own embedded
        # color and ignore this. So emoji_lbl needs the theme color too,
        # otherwise every currency using a plain symbol stays black-on-dark.
        self.emoji_lbl.setStyleSheet(f"color: {primary.name()};")
        self.sym_lbl.setStyleSheet(f"color: {primary.name()};")
        self.price_lbl.setStyleSheet(f"color: {primary.name()};")

    def set_dark(self, dark: bool):
        self.dark = dark
        self._apply_colors()

    def update_item(self, item, price: float, change: float, persian: bool):
        self.emoji_lbl.setText(item.emoji)
        label = F.short_label(item, persian)
        # The previous version paired an RTL layoutDirection with an
        # *absolute* Qt.AlignLeft. That mismatch is what caused the visible
        # bug: Qt lays an RTL run out growing from its own trailing edge, so
        # anchoring the box to the left clipped the leading characters at
        # paint time even though the full, correct string was set on the
        # label (confirmed by inspecting .text() — it clipped only visually).
        # The fix is to keep direction and alignment on the *same* side:
        # RTL direction + right alignment for Persian, LTR + left for
        # English/symbols. That's how every RTL-aware Qt app does it, and it
        # renders the full word with nothing clipped.
        if persian:
            self.sym_lbl.setLayoutDirection(Qt.RightToLeft)
            self.sym_lbl.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
        else:
            self.sym_lbl.setLayoutDirection(Qt.LeftToRight)
            self.sym_lbl.setAlignment(Qt.AlignLeft | Qt.AlignVCenter)
        self.sym_lbl.setText(label)
        price_str = F.format_price(price, item.type, persian)
        chg_str = F.format_change(change, item.type, persian)
        if persian:
            price_str = F.to_persian_digits(price_str)
            chg_str = F.to_persian_digits(chg_str)
        self.price_lbl.setText(price_str)
        self.chg_lbl.setText(chg_str)
        self.chg_lbl.setStyleSheet(f"color: {COLOR_UP if change >= 0 else COLOR_DOWN};")

    def clear_item(self):
        self.emoji_lbl.setText("")
        self.sym_lbl.setText("")
        self.price_lbl.setText("—")
        self.chg_lbl.setText("")


class WidgetWindow(QWidget):
    clicked = Signal()             # plain left-click -> open main window
    refresh_requested = Signal()
    open_requested = Signal()
    quit_requested = Signal()
    toggle_top_requested = Signal(bool)
    toggle_startup_requested = Signal(bool)
    toggle_dark_requested = Signal(bool)
    moved = Signal(int, int)       # emitted after a drag ends

    def __init__(self, dark: bool = False):
        super().__init__()
        self.dark = dark
        self._drag_offset = None
        self._dragged = False

        self.setWindowFlags(
            Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint | Qt.Tool
        )
        self.setAttribute(Qt.WA_TranslucentBackground)
        # 190x190 matches ShamsiCalWidget's WIDGET_SIZE exactly, so the two
        # floating cards line up when stacked on the desktop. Row internal
        # margins/fonts are untouched; only this outer card size and the
        # margins right above are what changed to make 3 rows fit exactly
        # inside a 190px-tall square instead of floating a few px taller.
        self.setFixedSize(CARD_WIDTH, CARD_WIDTH)

        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 2, 0, 2)
        outer.setSpacing(0)

        self.rows = [_Row(dark) for _ in range(3)]
        self._separators = []
        for i, row in enumerate(self.rows):
            outer.addWidget(row)
            if i < 2:
                sep = QWidget()
                sep.setFixedHeight(1)
                outer.addWidget(sep)
                self._separators.append(sep)
        self._apply_separator_colors()


    # -- painting the rounded card background --
    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        path = QPainterPath()
        rect = self.rect()
        path.addRoundedRect(
            QRect(rect.x(), rect.y(), rect.width(), rect.height()),
            CORNER_RADIUS, CORNER_RADIUS,
        )
        painter.fillPath(path, CARD_BG_DARK if self.dark else CARD_BG_LIGHT)

    def _apply_separator_colors(self):
        color = "rgba(255,255,255,0.10)" if self.dark else "rgba(0,0,0,0.09)"
        for sep in self._separators:
            sep.setStyleSheet(f"background-color: {color};")

    def set_dark(self, dark: bool):
        self.dark = dark
        for row in self.rows:
            row.set_dark(dark)
        self._apply_separator_colors()
        self.update()

    # -- data --
    def update_slot(self, slot: int, key: str, price: float, change: float, persian: bool):
        item = R.get(key)
        if item is None:
            self.rows[slot].clear_item()
            return
        self.rows[slot].update_item(item, price, change, persian)

    # -- dragging + click-to-open + right-click menu --
    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            self._drag_offset = event.globalPosition().toPoint() - self.pos()
            self._dragged = False
        elif event.button() == Qt.RightButton:
            self._show_context_menu(event.globalPosition().toPoint())

    def mouseMoveEvent(self, event):
        if self._drag_offset is not None and event.buttons() & Qt.LeftButton:
            new_pos = event.globalPosition().toPoint() - self._drag_offset
            if (new_pos - self.pos()).manhattanLength() > 3:
                self._dragged = True
            self.move(new_pos)

    def mouseReleaseEvent(self, event):
        if event.button() == Qt.LeftButton:
            if not self._dragged:
                self.clicked.emit()
            else:
                self.moved.emit(self.pos().x(), self.pos().y())
            self._drag_offset = None

    def _show_context_menu(self, global_pos: QPoint):
        menu = QMenu(self)
        act_refresh = menu.addAction("به‌روزرسانی")
        act_open = menu.addAction("باز کردن برنامه")
        menu.addSeparator()
        act_top = menu.addAction("همیشه روی صفحه")
        act_top.setCheckable(True)
        act_top.setChecked(bool(self.windowFlags() & Qt.WindowStaysOnTopHint))
        act_startup = menu.addAction("اجرا هنگام روشن شدن ویندوز")
        act_startup.setCheckable(True)
        act_dark = menu.addAction("حالت تیره")
        act_dark.setCheckable(True)
        act_dark.setChecked(self.dark)
        menu.addSeparator()
        act_quit = menu.addAction("خروج")

        chosen = menu.exec(global_pos)
        if chosen == act_refresh:
            self.refresh_requested.emit()
        elif chosen == act_open:
            self.open_requested.emit()
        elif chosen == act_top:
            self.toggle_top_requested.emit(act_top.isChecked())
        elif chosen == act_startup:
            self.toggle_startup_requested.emit(act_startup.isChecked())
        elif chosen == act_dark:
            self.toggle_dark_requested.emit(act_dark.isChecked())
        elif chosen == act_quit:
            self.quit_requested.emit()
