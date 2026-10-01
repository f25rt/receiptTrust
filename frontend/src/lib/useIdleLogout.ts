import { useEffect, useRef } from 'react';

const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'touchstart', 'scroll', 'click'];

/**
 * Logs the user out after `idleMs` with no interaction, to trim idle sessions
 * and open connections. Any mouse/keyboard/touch/scroll activity resets the
 * timer. Activity is throttled so frequent events (mousemove) don't thrash.
 */
export function useIdleLogout(onIdle: () => void, idleMs = 5 * 60 * 1000, enabled = true) {
  const onIdleRef = useRef(onIdle);
  onIdleRef.current = onIdle;

  useEffect(() => {
    if (!enabled) return;

    let timer: ReturnType<typeof setTimeout>;
    let lastReset = 0;

    const arm = () => {
      clearTimeout(timer);
      timer = setTimeout(() => onIdleRef.current(), idleMs);
    };

    const onActivity = () => {
      const now = Date.now();
      // Throttle resets to at most once per second.
      if (now - lastReset < 1000) return;
      lastReset = now;
      arm();
    };

    arm();
    ACTIVITY_EVENTS.forEach((e) => window.addEventListener(e, onActivity, { passive: true }));

    return () => {
      clearTimeout(timer);
      ACTIVITY_EVENTS.forEach((e) => window.removeEventListener(e, onActivity));
    };
  }, [idleMs, enabled]);
}
