import { useEffect, useRef } from 'react';
import { API_BASE, tokenStore } from '../api/client';

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
 * feature areas subscribe to. EventSource reconnects automatically; this hook
 * also rebuilds the connection if the access token changes.
 */
export function useRealtime(enabled: boolean) {
  useEffect(() => {
    if (!enabled) return;
    const token = tokenStore.access;
    if (!token) return;

    const url = `${API_BASE}/stream?token=${encodeURIComponent(token)}`;
    const es = new EventSource(url);

    const handlers: Array<[string, EventListener]> = [];
    for (const name of RT_EVENTS) {
      const handler: EventListener = (ev) => {
        const data = (ev as MessageEvent).data as string;
        window.dispatchEvent(new CustomEvent(rtEventName(name), { detail: data }));
      };
      es.addEventListener(name, handler);
      handlers.push([name, handler]);
    }

    return () => {
      handlers.forEach(([name, h]) => es.removeEventListener(name, h));
      es.close();
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
