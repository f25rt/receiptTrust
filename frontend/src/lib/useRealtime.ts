import { useEffect, useRef } from 'react';
import { api, API_BASE, tokenStore } from '../api/client';

/**
 * SSE event names pushed by the backend. The hook re-dispatches each as a
 * window CustomEvent prefixed with "rt:" so any component can listen without
 * sharing the EventSource instance.
 */
export const RT_EVENTS = ['notification', 'message', 'assignment'] as const;
export type RtEvent = (typeof RT_EVENTS)[number];

export const rtEventName = (e: RtEvent) => `rt:${e}`;

/**
 * Opens a single Server-Sent Events connection to the backend while mounted
 * (typically once, high in the tree). Incoming server events are re-dispatched
 * as window events ("rt:notification", "rt:message", "rt:assignment") that
 * feature areas subscribe to. Reconnects (with backoff) are handled here, each
 * using a freshly minted single-use stream ticket.
 */
export function useRealtime(enabled: boolean) {
  useEffect(() => {
    if (!enabled || !tokenStore.access) return;

    let es: EventSource | null = null;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let retryMs = 2000;
    let stopped = false;

    // The stream URL carries a short-lived, single-use ticket (not the access
    // token), so it's safe if it lands in a log. Tickets are consumed on use, so
    // every (re)connect must mint a new one — EventSource's own auto-retry would
    // replay the spent ticket and be rejected, hence we manage reconnects here.
    const connect = async () => {
      if (stopped) return;
      try {
        const { data } = await api.post<{ ticket: string }>('/stream-ticket');
        if (stopped) return;
        es = new EventSource(`${API_BASE}/stream?ticket=${encodeURIComponent(data.ticket)}`);
      } catch {
        scheduleRetry();
        return;
      }

      for (const name of RT_EVENTS) {
        es.addEventListener(name, (ev) => {
          const detail = (ev as MessageEvent).data as string;
          window.dispatchEvent(new CustomEvent(rtEventName(name), { detail }));
        });
      }
      es.onopen = () => {
        retryMs = 2000; // healthy connection: reset the backoff
      };
      es.onerror = () => {
        es?.close();
        es = null;
        scheduleRetry();
      };
    };

    const scheduleRetry = () => {
      if (stopped) return;
      retryTimer = setTimeout(connect, retryMs);
      retryMs = Math.min(retryMs * 2, 30000);
    };

    connect();

    return () => {
      stopped = true;
      if (retryTimer) clearTimeout(retryTimer);
      es?.close();
    };
  }, [enabled]);
}

/**
 * Subscribes a callback to one or more realtime event types for the lifetime of
 * the component. Use this in a feature area to refresh just that data when the
 * server signals a change.
 */
export function useRealtimeEvent(events: RtEvent | RtEvent[], callback: () => void) {
  const saved = useRef(callback);
  saved.current = callback;

  const key = Array.isArray(events) ? events.join(',') : events;
  useEffect(() => {
    const list = key.split(',') as RtEvent[];
    const handler = () => saved.current();
    const names = list.map(rtEventName);
    names.forEach((n) => window.addEventListener(n, handler));
    return () => names.forEach((n) => window.removeEventListener(n, handler));
  }, [key]);
}
