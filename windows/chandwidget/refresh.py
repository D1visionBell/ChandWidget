"""
Fetches prices in background threads and reports results back to the Qt
main thread via signals (Qt automatically queues cross-thread signal
emissions, so this is safe without extra locking on the receiving side).
"""
from concurrent.futures import ThreadPoolExecutor

from PySide6.QtCore import QObject, Signal

from . import api


class RefreshManager(QObject):
    price_updated = Signal(str, float, float)   # key, price, change
    fetch_failed = Signal(str)                  # key
    all_done = Signal()

    def __init__(self, max_workers: int = 6):
        super().__init__()
        self._pool = ThreadPoolExecutor(max_workers=max_workers)

    def refresh(self, keys):
        keys = list(dict.fromkeys(keys))  # de-dupe, keep order
        if not keys:
            self.all_done.emit()
            return
        remaining = {"n": len(keys)}

        def _done_one(_):
            remaining["n"] -= 1
            if remaining["n"] <= 0:
                self.all_done.emit()

        for key in keys:
            future = self._pool.submit(self._fetch_one, key)
            future.add_done_callback(_done_one)

    def _fetch_one(self, key: str):
        data = api.fetch(key)
        if data is None:
            self.fetch_failed.emit(key)
        else:
            self.price_updated.emit(data.key, data.price, data.change)

    def shutdown(self):
        self._pool.shutdown(wait=False, cancel_futures=True)
