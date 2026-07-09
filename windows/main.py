"""
ChandWidget for Windows — entry point.

Run:
    python main.py

Package as a standalone .exe:
    see README.md
"""
from chandwidget.app import ChandWidgetApp

if __name__ == "__main__":
    app = ChandWidgetApp()
    app.run()
