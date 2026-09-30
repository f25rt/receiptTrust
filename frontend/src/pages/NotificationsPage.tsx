import { useEffect, useState } from 'react';
import { notificationApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import type { Notification } from '../api/types';
import { Card, Empty, ErrorBanner } from '../components/ui';

export default function NotificationsPage() {
  const [items, setItems] = useState<Notification[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try {
      const r = await notificationApi.list();
      setItems(r.data);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  useEffect(() => {
    load();
  }, []);

  const markRead = async (id: number) => {
    try {
      await notificationApi.markRead(id);
      await load();
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <div className="page">
      <h1>Notifications</h1>
      <ErrorBanner message={error} />
      <Card>
        {items.length === 0 ? (
          <Empty text="Nothing here yet." />
        ) : (
          <ul className="list">
            {items.map((n) => (
              <li key={n.id} className={n.read ? 'read' : 'unread'}>
                <span>
                  <span className="ev-type">{n.type.replace(/_/g, ' ')}</span>
                  <span className="muted"> {n.message}</span>
                </span>
                {!n.read && <button className="btn-ghost" onClick={() => markRead(n.id)}>Mark read</button>}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
