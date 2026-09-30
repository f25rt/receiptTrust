import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { debtApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import type { Dashboard, DebtSummary } from '../api/types';
import { Avatar, Card, ErrorBanner, Empty, Icon } from '../components/ui';
import { money, signedMoney, trustTier, MAX_TRUST_SCORE } from '../lib/format';

export default function DashboardPage() {
  const { profile } = useAuth();
  const navigate = useNavigate();
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<'owed' | 'owe'>('owed');

  useEffect(() => {
    debtApi
      .dashboard()
      .then((r) => setData(r.data))
      .catch((e) => setError(apiErrorMessage(e)));
  }, []);

  const score = profile?.trustScore ?? 500;
  const tier = trustTier(score);
  const net = data ? Number(data.totalOwedToMe) - Number(data.totalIOwe) : 0;
  const firstName = (profile?.fullName ?? 'there').split(' ')[0];

  return (
    <div className="flex flex-col w-full px-margin lg:px-0 pb-space-xl gap-space-lg">
      {/* Welcome header */}
      <div className="flex items-center justify-between pt-space-xs gap-space-md">
        <div className="flex flex-col min-w-0">
          <h1 className="font-display-mobile lg:text-display text-on-surface tracking-tight truncate">
            Welcome back, {firstName} 👋
          </h1>
          <div className="flex items-center gap-space-2xs mt-space-2xs">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-tertiary opacity-75" />
              <span className="relative inline-flex rounded-full h-2 w-2 bg-tertiary" />
            </span>
            <span className="font-label-sm text-on-surface-variant">All systems synced &amp; verified</span>
          </div>
        </div>
        {/* Desktop header actions */}
        <div className="hidden lg:flex items-center gap-space-sm shrink-0">
          <button className="btn-glass" onClick={() => navigate('/friends')}>
            <Icon name="group_add" className="text-[18px]" /> Add Peer
          </button>
          <button className="btn-primary" onClick={() => navigate('/receipts')}>
            <Icon name="bolt" className="text-[18px]" /> Scan &amp; Split Receipt
          </button>
        </div>
      </div>

      <ErrorBanner message={error} />

      {data && (
        <div className="flex flex-col gap-space-lg lg:grid lg:grid-cols-3 lg:gap-space-lg lg:items-start">
          {/* Left/main column */}
          <div className="flex flex-col gap-space-lg lg:col-span-2">
          {/* Net position card */}
          <Card className="flex flex-col gap-space-md">
            <div className="flex items-center justify-between">
              <div className="flex flex-col">
                <span className="font-label-sm text-on-surface-variant uppercase tracking-wider">
                  Net Position
                </span>
                <div className="flex items-baseline gap-space-xs mt-space-2xs">
                  <span
                    className={`font-display ${
                      net >= 0
                        ? 'text-tertiary drop-shadow-[0_0_12px_rgba(78,222,163,0.35)]'
                        : 'text-error'
                    }`}
                  >
                    {signedMoney(net, net >= 0)}
                  </span>
                  <span className="font-label-md text-tertiary-fixed bg-surface-container-high px-space-xs py-0.5 rounded-full">
                    {net >= 0 ? 'Optimal' : 'Attention'}
                  </span>
                </div>
              </div>
              <div className="w-12 h-12 rounded-xl bg-surface-container-high flex items-center justify-center text-secondary">
                <Icon name="account_balance_wallet" className="text-[28px]" />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-space-sm">
              <div className="rt-card-inner flex flex-col justify-between gap-space-xs">
                <div className="flex items-center justify-between">
                  <span className="font-label-sm text-on-surface-variant">Owed to You</span>
                  <Icon name="south_west" className="text-tertiary text-[18px]" />
                </div>
                <span className="font-tabular-amount text-tertiary">
                  {signedMoney(data.totalOwedToMe, true)}
                </span>
                <button
                  onClick={() => setTab('owed')}
                  className="mt-space-2xs w-full py-1.5 px-space-xs rounded-full bg-tertiary-container/30 text-tertiary font-label-sm text-center active:scale-95 transition-transform"
                >
                  View
                </button>
              </div>
              <div className="rt-card-inner flex flex-col justify-between gap-space-xs">
                <div className="flex items-center justify-between">
                  <span className="font-label-sm text-on-surface-variant">You Owe</span>
                  <Icon name="north_east" className="text-error text-[18px]" />
                </div>
                <span className="font-tabular-amount text-error">
                  {signedMoney(data.totalIOwe, false)}
                </span>
                <button
                  onClick={() => setTab('owe')}
                  className="mt-space-2xs w-full py-1.5 px-space-xs rounded-full bg-error-container/40 text-error font-label-sm text-center active:scale-95 transition-transform"
                >
                  Settle Up
                </button>
              </div>
            </div>
          </Card>

          {/* Quick actions (mobile only; desktop has sidebar + header actions) */}
          <div className="w-full overflow-x-auto no-scrollbar -mx-margin px-margin lg:hidden">
            <div className="flex items-center gap-space-xs min-w-max pb-space-2xs">
              <button className="btn-primary" onClick={() => navigate('/receipts')}>
                <Icon name="bolt" className="text-[18px]" />
                <span>New Receipt</span>
              </button>
              <button className="chip" onClick={() => navigate('/friends')}>
                <Icon name="group_add" className="text-[18px] text-secondary" />
                <span>Add Friend</span>
              </button>
              <button className="chip" onClick={() => setTab('owe')}>
                <Icon name="payments" className="text-[18px] text-tertiary" />
                <span>Settle Up</span>
              </button>
              <button className="chip" onClick={() => navigate('/profile')}>
                <Icon name="verified_user" className="text-[18px] text-secondary-fixed-dim" />
                <span>Trust Ledger</span>
              </button>
            </div>
          </div>

          {/* Trust reputation (inline on mobile, sidebar on desktop) */}
          <Card className="flex flex-col gap-space-md lg:hidden">
            <TrustReputationBody score={score} tier={tier} debtsSettled={profile?.debtsSettled ?? 0} />
          </Card>

          {/* Active ledger */}
          <div className="flex flex-col gap-space-md">
            <div className="flex items-center justify-between">
              <h2 className="font-headline-md text-on-surface">Active Ledger</h2>
              <div className="flex items-center p-1 bg-surface-container-high rounded-full">
                <button
                  onClick={() => setTab('owed')}
                  className={`px-space-sm py-1 rounded-full font-label-md transition-all ${
                    tab === 'owed'
                      ? 'bg-primary-container text-on-primary'
                      : 'text-on-surface-variant'
                  }`}
                >
                  Owed to Me ({data.owedToMe.length})
                </button>
                <button
                  onClick={() => setTab('owe')}
                  className={`px-space-sm py-1 rounded-full font-label-md transition-all ${
                    tab === 'owe'
                      ? 'bg-primary-container text-on-primary'
                      : 'text-on-surface-variant'
                  }`}
                >
                  I Owe ({data.iOwe.length})
                </button>
              </div>
            </div>

            {tab === 'owed' ? (
              data.owedToMe.length === 0 ? (
                <Card>
                  <Empty text="Nobody owes you yet. Create a receipt to split expenses." icon="request_quote" />
                </Card>
              ) : (
                <div className="flex flex-col gap-space-sm">
                  {data.owedToMe.map((d) => (
                    <LedgerRow key={d.debtId} debt={d} owed onClick={() => navigate(`/debts/${d.debtId}`)} />
                  ))}
                </div>
              )
            ) : data.iOwe.length === 0 ? (
              <Card>
                <Empty text="You don't owe anyone. Nice." icon="check_circle" />
              </Card>
            ) : (
              <div className="flex flex-col gap-space-sm">
                {data.iOwe.map((d) => (
                  <LedgerRow key={d.debtId} debt={d} owed={false} onClick={() => navigate(`/debts/${d.debtId}`)} />
                ))}
              </div>
            )}
          </div>
          </div>

          {/* Right sidebar (desktop only) */}
          <aside className="hidden lg:flex flex-col gap-space-lg">
            <Card className="flex flex-col gap-space-md">
              <TrustReputationBody score={score} tier={tier} debtsSettled={profile?.debtsSettled ?? 0} />
            </Card>
            <Card className="flex flex-col gap-space-sm">
              <span className="font-headline-sm text-on-surface">Quick actions</span>
              <button className="btn-glass w-full justify-start" onClick={() => navigate('/receipts')}>
                <Icon name="bolt" className="text-[18px] text-secondary" /> New receipt
              </button>
              <button className="btn-glass w-full justify-start" onClick={() => navigate('/friends')}>
                <Icon name="group_add" className="text-[18px] text-secondary" /> Add friend
              </button>
              <button className="btn-glass w-full justify-start" onClick={() => setTab('owe')}>
                <Icon name="payments" className="text-[18px] text-tertiary" /> Settle up
              </button>
            </Card>
          </aside>
        </div>
      )}
    </div>
  );
}

function TrustReputationBody({
  score,
  tier,
  debtsSettled,
}: {
  score: number;
  tier: { label: string };
  debtsSettled: number;
}) {
  return (
    <>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-space-xs">
          <div className="w-8 h-8 rounded-full bg-tertiary-container/30 flex items-center justify-center text-tertiary">
            <Icon name="verified" className="text-[18px]" />
          </div>
          <div className="flex flex-col">
            <span className="font-headline-sm text-on-surface">Trust Reputation</span>
            <span className="font-label-sm text-on-surface-variant">Tier: {tier.label}</span>
          </div>
        </div>
        <div className="flex items-center gap-space-2xs bg-surface-container-high px-space-sm py-1 rounded-full">
          <span className="font-headline-sm text-tertiary-fixed">{score}</span>
          <span className="font-label-sm text-on-surface-variant">/ {MAX_TRUST_SCORE}</span>
        </div>
      </div>
      <div className="w-full bg-surface-container rounded-full h-2 overflow-hidden">
        <div
          className="bg-tertiary h-full rounded-full transition-all duration-700 ease-out"
          style={{ width: `${Math.min(100, (score / MAX_TRUST_SCORE) * 100)}%` }}
        />
      </div>
      <div className="flex items-start gap-space-xs bg-surface-container-high/60 p-space-sm rounded-lg">
        <Icon name="tips_and_updates" className="text-secondary text-[20px] shrink-0 mt-0.5" />
        <p className="font-body-sm text-on-surface-variant">
          Settle debts on time to boost peer trust and climb the reputation tiers. You've
          settled <strong className="text-tertiary font-medium">{debtsSettled}</strong> debt(s) so far.
        </p>
      </div>
    </>
  );
}

function LedgerRow({
  debt,
  owed,
  onClick,
}: {
  debt: DebtSummary;
  owed: boolean;
  onClick: () => void;
}) {
  const settled = debt.status === 'SETTLED';
  return (
    <button onClick={onClick} className="rt-card flex flex-col gap-space-sm text-left active:scale-[0.99] transition-transform">
      <div className="flex items-center justify-between gap-space-sm">
        <div className="flex items-center gap-space-sm min-w-0">
          <Avatar name={debt.counterpartyUsername} size={44} />
          <div className="flex flex-col min-w-0">
            <span className="font-headline-sm text-on-surface truncate flex items-center gap-space-2xs">
              {debt.counterpartyUsername}
              {debt.counterpartyIsLabel && (
                <span className="pill bg-secondary/15 text-secondary text-[10px] px-1.5 py-0">
                  <Icon name="label" className="text-[10px]" /> Label
                </span>
              )}
            </span>
            <span className="font-body-sm text-on-surface-variant truncate">
              Due {debt.dueDate}
            </span>
          </div>
        </div>
        <div className="flex flex-col items-end shrink-0">
          <span className={`font-tabular-amount ${owed ? 'text-tertiary' : 'text-error'}`}>
            {signedMoney(debt.outstandingAmount, owed)}
          </span>
          <span className={`font-label-sm ${settled ? 'text-tertiary' : 'text-on-surface-variant'}`}>
            {settled ? 'Settled' : `of ${money(debt.originalAmount)}`}
          </span>
        </div>
      </div>
      <div className="flex items-center justify-between pt-space-xs border-t border-white/[0.05]">
        <span className="font-label-sm text-on-surface-variant flex items-center gap-1">
          <Icon name="verified" className="text-[14px] text-tertiary" /> Receipt-backed
        </span>
        <span className="font-label-md text-primary flex items-center gap-space-2xs">
          <Icon name="arrow_forward" className="text-[16px]" />
          Details
        </span>
      </div>
    </button>
  );
}
