import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { debtApi, friendApi, messageApi, profileApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useAvatarUrl } from '../auth/useAvatarUrl';
import type { Dashboard, FriendRequest, SearchResult, UserSummary } from '../api/types';
import { Avatar, Card, Empty, ErrorBanner, Icon } from '../components/ui';
import { trustTier, signedMoney } from '../lib/format';
import { useDebounce } from '../lib/useDebounce';
import { usePolling } from '../lib/usePolling';
import { useRealtimeEvent } from '../lib/useRealtime';

const TIERS = [
  { min: '300+', label: 'Starter', dot: 'bg-outline' },
  { min: '500+', label: 'Fair', dot: 'bg-on-surface-variant' },
  { min: '600+', label: 'Reliable', dot: 'bg-secondary-fixed-dim' },
  { min: '750+', label: 'Trusted', dot: 'bg-secondary' },
  { min: '900+', label: 'Elite', dot: 'bg-tertiary' },
];

/** Net balance with one peer, aggregated from the debt dashboard. */
interface PeerBalance {
  /** positive = they owe me, negative = I owe them, 0 = settled */
  net: number;
  debtId: number | null;
}

function buildPeerBalances(dash: Dashboard): Record<string, PeerBalance> {
  const map: Record<string, PeerBalance> = {};
  for (const d of dash.owedToMe) {
    if (d.status !== 'ACTIVE') continue;
    const cur = map[d.counterpartyUsername] ?? { net: 0, debtId: null };
    cur.net += Number(d.outstandingAmount);
    cur.debtId = d.debtId;
    map[d.counterpartyUsername] = cur;
  }
  for (const d of dash.iOwe) {
    if (d.status !== 'ACTIVE') continue;
    const cur = map[d.counterpartyUsername] ?? { net: 0, debtId: null };
    cur.net -= Number(d.outstandingAmount);
    cur.debtId = d.debtId;
    map[d.counterpartyUsername] = cur;
  }
  return map;
}

export default function FriendsPage() {
  const navigate = useNavigate();
  const { profile } = useAuth();
  const avatarUrl = useAvatarUrl();
  const myTier = trustTier(profile?.trustScore ?? 500);
  const [friends, setFriends] = useState<UserSummary[]>([]);
  const [requests, setRequests] = useState<FriendRequest[]>([]);
  const [balances, setBalances] = useState<Record<string, PeerBalance>>({});
  const [unread, setUnread] = useState<Record<string, number>>({});
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try {
      const [f, r, dash, u] = await Promise.all([
        friendApi.list(),
        friendApi.incoming(),
        debtApi.dashboard(),
        messageApi.unreadBySender(),
      ]);
      setFriends(f.data);
      setRequests(r.data);
      setBalances(buildPeerBalances(dash.data));
      setUnread(u.data);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  useEffect(() => {
    load();
  }, []);

  // Live refresh on server push (friend requests/accepts, new messages), slow poll fallback.
  useRealtimeEvent(['notification', 'assignment', 'message'], load);
  usePolling(load, 30000);

  // Live search as the user types (debounced).
  const debouncedQuery = useDebounce(query, 300);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    const q = debouncedQuery.trim();
    if (q.length < 1) {
      setResults([]);
      return;
    }
    let cancelled = false;
    setSearching(true);
    profileApi
      .search(q)
      .then((r) => {
        if (!cancelled) setResults(r.data);
      })
      .catch((e2) => {
        if (!cancelled) setError(apiErrorMessage(e2));
      })
      .finally(() => {
        if (!cancelled) setSearching(false);
      });
    return () => {
      cancelled = true;
    };
  }, [debouncedQuery]);

  const act = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
      await load();
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <div className="flex flex-col w-full px-margin lg:px-0 pb-space-xl gap-space-lg">
      <div className="flex items-center justify-between pt-space-xs">
        <h1 className="font-display-mobile lg:text-display text-on-surface">Friends &amp; Peers</h1>
        <span className="font-label-md text-secondary bg-secondary/15 px-space-sm py-1 rounded-full">
          {friends.length} Connected
        </span>
      </div>

      <ErrorBanner message={error} />

      {/* Search (live/debounced) */}
      <div className="relative">
        <Icon
          name="search"
          className="absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px]"
        />
        <input
          className="field-input pl-10 pr-10"
          placeholder="Search by @username or email"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        {searching && (
          <Icon
            name="progress_activity"
            className="absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px] animate-spin"
          />
        )}
      </div>

      <div className="flex flex-col gap-space-lg lg:grid lg:grid-cols-3 lg:gap-space-lg lg:items-start">
        <div className="flex flex-col gap-space-lg lg:col-span-2">
      {debouncedQuery.trim().length > 0 && results.length === 0 && !searching && (
        <Card>
          <Empty text={`No users match "${debouncedQuery.trim()}".`} icon="person_search" />
        </Card>
      )}

      {results.length > 0 && (
        <Card className="flex flex-col gap-space-sm">
          <span className="font-label-md text-on-surface-variant uppercase tracking-wider">
            Search results
          </span>
          {results.map((u) => (
            <div key={u.id} className="flex items-center justify-between gap-space-sm">
              <div className="flex items-center gap-space-sm min-w-0">
                <Avatar name={u.fullName} size={40} />
                <div className="flex flex-col min-w-0">
                  <span className="font-headline-sm text-on-surface truncate">{u.fullName}</span>
                  <span className="font-body-sm text-on-surface-variant truncate">@{u.username}</span>
                </div>
              </div>
              <button className="btn-glass" onClick={() => act(() => friendApi.send(u.username))}>
                <Icon name="person_add" className="text-[16px]" />
                <span>Add</span>
              </button>
            </div>
          ))}
        </Card>
      )}

      {/* Pending requests */}
      <div className="flex flex-col gap-space-sm">
        <span className="font-label-md text-on-surface-variant uppercase tracking-wider">
          Pending connections ({requests.length})
        </span>
        {requests.length === 0 ? (
          <Card>
            <Empty text="No pending requests." icon="hourglass_empty" />
          </Card>
        ) : (
          requests.map((req) => {
            const tier = trustTier(req.requester.trustScore);
            return (
              <Card key={req.requestId} className="flex flex-col gap-space-sm">
                <div className="flex items-center justify-between gap-space-sm">
                  <div className="flex items-center gap-space-sm min-w-0">
                    <Avatar name={req.requester.fullName} size={44} />
                    <div className="flex flex-col min-w-0">
                      <div className="flex items-center gap-space-2xs">
                        <span className="font-headline-sm text-on-surface truncate">
                          {req.requester.fullName}
                        </span>
                        <span className={`pill ${tier.bg} ${tier.color}`}>
                          {req.requester.trustScore} ⭐ {tier.label}
                        </span>
                      </div>
                      <span className="font-body-sm text-on-surface-variant truncate">
                        @{req.requester.username}
                      </span>
                    </div>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-space-sm">
                  <button className="btn-glass" onClick={() => act(() => friendApi.reject(req.requestId))}>
                    Decline
                  </button>
                  <button className="btn-primary" onClick={() => act(() => friendApi.accept(req.requestId))}>
                    <Icon name="check_circle" className="text-[16px]" />
                    Accept
                  </button>
                </div>
              </Card>
            );
          })
        )}
      </div>

      {/* Active peers */}
      <div className="flex flex-col gap-space-sm">
        <span className="font-label-md text-on-surface-variant uppercase tracking-wider">
          Active peers ({friends.length})
        </span>
        {friends.length === 0 ? (
          <Card>
            <Empty text="No friends yet. Search above to connect." icon="group" />
          </Card>
        ) : (
          friends.map((f) => {
            const tier = trustTier(f.trustScore);
            const bal = balances[f.username];
            const net = bal?.net ?? 0;
            const owesMe = net > 0.001;
            const iOwe = net < -0.001;
            return (
              <Card key={f.id} className="flex flex-col gap-space-sm">
                <div className="flex items-start justify-between gap-space-sm">
                  <div className="flex items-center gap-space-sm min-w-0">
                    <Avatar name={f.fullName} size={44} />
                    <div className="flex flex-col min-w-0">
                      <div className="flex items-center gap-space-2xs">
                        <span className="font-headline-sm text-on-surface truncate">{f.fullName}</span>
                      </div>
                      <span className="font-body-sm text-on-surface-variant truncate">@{f.username}</span>
                      <span className={`pill ${tier.bg} ${tier.color} mt-space-2xs self-start`}>
                        {f.trustScore} ⭐ {tier.label}
                      </span>
                    </div>
                  </div>
                  <div className="flex flex-col items-end shrink-0">
                    {owesMe && (
                      <>
                        <span className="font-label-sm text-tertiary">Owes you</span>
                        <span className="font-tabular-amount text-tertiary">{signedMoney(net, true)}</span>
                      </>
                    )}
                    {iOwe && (
                      <>
                        <span className="font-label-sm text-error">You owe</span>
                        <span className="font-tabular-amount text-error">{signedMoney(net, false)}</span>
                      </>
                    )}
                    {!owesMe && !iOwe && (
                      <span className="font-label-sm text-on-surface-variant flex items-center gap-1">
                        <Icon name="check_circle" className="text-[14px] text-tertiary" /> Settled
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex items-center gap-space-sm pt-space-xs border-t border-white/[0.05]">
                  <button
                    className="btn-glass flex-1 relative"
                    onClick={() => {
                      setUnread((prev) => ({ ...prev, [f.username]: 0 }));
                      navigate(`/messages/${f.username}`);
                    }}
                  >
                    <Icon name="chat" className="text-[16px]" /> Message
                    {unread[f.username] > 0 && (
                      <span className="absolute -top-1.5 -right-1.5 min-w-[20px] h-5 px-1.5 rounded-full bg-secondary text-on-secondary text-[11px] font-label-sm flex items-center justify-center">
                        {unread[f.username] > 99 ? '99+' : unread[f.username]}
                      </span>
                    )}
                  </button>
                  {bal?.debtId ? (
                    <button
                      className={iOwe ? 'btn-destructive flex-1' : 'btn-primary flex-1'}
                      onClick={() => navigate(`/debts/${bal.debtId}`)}
                    >
                      <Icon name={iOwe ? 'payments' : 'visibility'} className="text-[16px]" />
                      {iOwe ? 'Settle' : 'View'}
                    </button>
                  ) : (
                    <button
                      className="w-11 h-11 rounded-full bg-surface-container-high flex items-center justify-center text-on-surface-variant active:scale-95 transition-transform shrink-0"
                      aria-label="Remove friend"
                      onClick={() => act(() => friendApi.remove(f.id))}
                    >
                      <Icon name="person_remove" className="text-[18px]" />
                    </button>
                  )}
                </div>
              </Card>
            );
          })
        )}
      </div>
        </div>

        {/* Sidebar */}
        <aside className="flex flex-col gap-space-lg">
          {/* My network reputation */}
          <Card className="flex flex-col gap-space-sm">
            <span className="font-label-md text-on-surface-variant uppercase tracking-wider">
              My network reputation
            </span>
            <div className="flex items-center gap-space-sm">
              <Avatar name={profile?.fullName ?? '?'} size={48} imagePath={avatarUrl} />
              <div className="flex flex-col">
                <span className="font-headline-sm text-on-surface">{profile?.fullName}</span>
                <span className={`pill ${myTier.bg} ${myTier.color} self-start`}>
                  {profile?.trustScore ?? '—'} ⭐ {myTier.label}
                </span>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-space-sm mt-space-2xs">
              <div className="rt-card-inner flex flex-col items-center gap-space-2xs py-space-sm">
                <span className="font-headline-md text-on-surface">{friends.length}</span>
                <span className="font-label-sm text-on-surface-variant">Peers</span>
              </div>
              <div className="rt-card-inner flex flex-col items-center gap-space-2xs py-space-sm">
                <span className="font-headline-md text-tertiary">{profile?.debtsSettled ?? 0}</span>
                <span className="font-label-sm text-on-surface-variant">Settled</span>
              </div>
            </div>
          </Card>

          {/* Trust scoring tiers legend */}
          <Card className="flex flex-col gap-space-md">
            <div className="flex items-center gap-space-xs">
              <Icon name="shield" className="text-tertiary text-[20px]" />
              <span className="font-headline-sm text-on-surface">Peer Trust Scoring Tiers</span>
            </div>
            <p className="font-body-sm text-on-surface-variant">
              Scored transparently from verified receipt-split approvals, prompt repayment cycles,
              and dispute-free settlements.
            </p>
            <div className="flex flex-col gap-space-2xs">
              {TIERS.map((t) => (
                <div key={t.label} className="flex items-center justify-between py-space-2xs border-b border-white/[0.05] last:border-0">
                  <span className="flex items-center gap-space-xs">
                    <span className={`w-2 h-2 rounded-full ${t.dot}`} />
                    <span className="font-label-md text-on-surface">{t.label}</span>
                  </span>
                  <span className="font-label-sm text-on-surface-variant">{t.min}</span>
                </div>
              ))}
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}
