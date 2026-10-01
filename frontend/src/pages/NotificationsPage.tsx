import { useEffect, useState } from 'react';
import { notificationApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import type { Notification, NotificationType } from '../api/types';
import { Card, Empty, ErrorBanner, Icon } from '../components/ui';

const META: Record<NotificationType, { icon: string; color: string }> = {
  FRIEND_REQUEST_RECEIVED: { icon: 'person_add', color: 'text-secondary' },
  FRIEND_REQUEST_ACCEPTED: { icon: 'group', color: 'text-tertiary' },
  DEBT_CREATED: { icon: 'request_quote', color: 'text-primary' },
  PAYMENT_SUBMITTED: { icon: 'payments', color: 'text-secondary' },
  PAYMENT_APPROVED: { icon: 'check_circle', color: 'text-tertiary' },
  PAYMENT_REJECTED: { icon: 'cancel', color: 'text-error' },
  DEBT_SETTLED: { icon: 'verified', color: 'text-tertiary' },
  DEBT_MARKED_PAID: { icon: 'task_alt', color: 'text-tertiary' },
  DEBT_COMMENT: { icon: 'forum', color: 'text-secondary' },
};

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

  const unreadCount = items.filter((n) => !n.read).length;

  return (
    <div className="flex flex-col w-full px-margin lg:px-0 pb-space-xl gap-space-lg">
      <div className="flex items-center justify-between pt-space-xs">
        <div className="flex flex-col">
          <h1 className="font-display-mobile lg:text-display text-on-surface">Activity &amp; Audit</h1>
          <span className="hidden lg:block font-body-sm text-on-surface-variant">
            Real-time settlement activity and receipt-backed audit trail.
          </span>
        </div>
        {unreadCount > 0 && (
          <span className="font-label-md text-on-primary bg-primary-container px-space-sm py-1 rounded-full">
            {unreadCount} new
          </span>
        )}
      </div>

      <ErrorBanner message={error} />

      <div className="flex flex-col gap-space-lg lg:grid lg:grid-cols-3 lg:gap-space-lg lg:items-start">
        <div className="flex flex-col gap-space-sm lg:col-span-2">
      {items.length === 0 ? (
        <Card>
          <Empty text="Nothing here yet." icon="notifications" />
        </Card>
      ) : (
        <div className="flex flex-col gap-space-sm">
          {items.map((n) => {
            const meta = META[n.type];
            return (
              <Card
                key={n.id}
                className={`flex items-center gap-space-sm ${n.read ? 'opacity-70' : ''}`}
              >
                <div className={`w-10 h-10 rounded-full bg-surface-container-high flex items-center justify-center shrink-0 ${meta.color}`}>
                  <Icon name={meta.icon} className="text-[20px]" />
                </div>
                <div className="flex flex-col min-w-0 flex-1">
                  <span className={`font-headline-sm ${n.read ? 'text-on-surface-variant' : 'text-on-surface'}`}>
                    {n.type.replace(/_/g, ' ')}
                  </span>
                  <span className="font-body-sm text-on-surface-variant">{n.message}</span>
                  <span className="font-label-sm text-on-surface-variant mt-space-2xs">
                    {new Date(n.createdAt).toLocaleString()}
                  </span>
                </div>
                {!n.read && (
                  <button
                    className="w-9 h-9 rounded-full bg-surface-container-high flex items-center justify-center text-primary active:scale-95 transition-transform shrink-0"
                    onClick={() => markRead(n.id)}
                    aria-label="Mark read"
                  >
                    <Icon name="done" className="text-[18px]" />
                  </button>
                )}
              </Card>
            );
          })}
        </div>
      )}
        </div>

        {/* Sidebar: audit health + compliance (desktop) */}
        <aside className="hidden lg:flex flex-col gap-space-lg">
          <Card className="flex flex-col gap-space-md">
            <div className="flex items-center gap-space-xs">
              <Icon name="shield" className="text-secondary text-[20px]" />
              <span className="font-headline-sm text-on-surface">Audit Trail Health</span>
            </div>
            <div className="flex items-baseline gap-space-xs">
              <span className="font-display text-secondary">100%</span>
              <span className="font-label-sm text-on-surface-variant">receipt-backed</span>
            </div>
            <div className="grid grid-cols-2 gap-space-sm">
              <div className="rt-card-inner flex flex-col gap-space-2xs">
                <span className="font-headline-md text-on-surface">0</span>
                <span className="font-label-sm text-on-surface-variant">Open disputes</span>
              </div>
              <div className="rt-card-inner flex flex-col gap-space-2xs">
                <span className="font-headline-md text-tertiary">{items.length}</span>
                <span className="font-label-sm text-on-surface-variant">Logged events</span>
              </div>
            </div>
          </Card>

          <Card className="flex flex-col gap-space-sm">
            <span className="font-headline-sm text-on-surface">Every debt is proof-backed</span>
            <p className="font-body-sm text-on-surface-variant">
              Each debt links to a stored receipt image and an itemized breakdown, so settlements
              stay transparent and dispute-free.
            </p>
            <button className="btn-glass w-full justify-start" onClick={() => (window.location.href = '/')}>
              <Icon name="dashboard" className="text-[18px] text-secondary" /> Go to dashboard
            </button>
          </Card>
        </aside>
      </div>
    </div>
  );
}
