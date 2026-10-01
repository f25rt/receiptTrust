import { useEffect, useRef } from 'react';

/**
 * Calls `callback` on an interval so a UI area stays fresh without a manual
 * page refresh. Polling pauses while the browser tab is hidden and resumes
 * (with an immediate refresh) when it becomes visible again, to avoid wasting
 * requests on a backgrounded tab.
 *
 * The callback is kept in a ref so you can pass an inline function without
 * resetting the timer on every render.
 */
export function usePolling(callback: () => void, intervalMs = 10000, enabled = true) {
  const saved = useRef(callback);
  saved.current = callback;

  useEffect(() => {
    if (!enabled) return;

    const tick = () => {
      if (document.visibilityState === 'visible') {
        saved.current();
      }
    };

    const id = setInterval(tick, intervalMs);

    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        saved.current();
      }
    };
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      clearInterval(id);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [intervalMs, enabled]);
}
