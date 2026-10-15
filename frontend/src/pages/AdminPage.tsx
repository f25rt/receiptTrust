import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { adminApi, taxApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import type { AdminMetrics, AdminMetricPoint, AdminUser, TaxSetting } from '../api/types';
import { Card, Empty, ErrorBanner, Icon } from '../components/ui';
import { usePolling } from '../lib/usePolling';

export default function AdminPage() {
  const { profile } = useAuth();
  const navigate = useNavigate();
  const [metrics, setMetrics] = useState<AdminMetrics | null>(null);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [query, setQuery] = useState('');
  const [error, setError] = useState<string | null>(null);

  const loadMetrics = async () => {
    try {
      const r = await adminApi.metrics();
      setMetrics(r.data);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  const loadUsers = async () => {
    try {
      const r = await adminApi.users(query);
      setUsers(r.data);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  useEffect(() => {
    loadMetrics();
    loadUsers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  usePolling(loadMetrics, 15000);

  const moderate = async (id: number, body: { locked?: boolean; flagged?: boolean }) => {
    setError(null);
    try {
      await adminApi.moderate(id, body);
      await Promise.all([loadUsers(), loadMetrics()]);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  // Non-admins never reach here (guarded by the route), but double-guard anyway.
  if (profile && profile.role !== 'ADMIN') {
    return (
      <div className="flex flex-col w-full px-margin pt-space-lg gap-space-md">
        <Empty text="Admins only." icon="lock" />
      </div>
    );
  }

  return (
    <div className="flex flex-col w-full px-margin lg:px-0 pb-space-xl gap-space-lg">
      <div className="flex items-center justify-between pt-space-xs">
        <div className="flex flex-col">
          <h1 className="font-display-mobile lg:text-display text-on-surface">Admin</h1>
          <span className="font-body-sm text-on-surface-variant">Metrics &amp; moderation</span>
        </div>
        <span className="font-label-md text-tertiary bg-tertiary/15 px-space-sm py-1 rounded-full flex items-center gap-1">
          <span className="relative flex h-2 w-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-tertiary opacity-75" />
            <span className="relative inline-flex rounded-full h-2 w-2 bg-tertiary" />
          </span>
          {metrics?.onlineNow ?? 0} online
        </span>
      </div>

      <ErrorBanner message={error} />

      {!metrics ? (
        <Card><Empty text="Loading metrics…" icon="hourglass_empty" /></Card>
      ) : (
        <>
          {/* Headline stats */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-space-sm">
            <Stat icon="group" label="Total users" value={metrics.totalUsers} />
            <Stat icon="bolt" label="Active today" value={metrics.activeToday} />
            <Stat icon="calendar_month" label="Active this month" value={metrics.activeThisMonth} />
            <Stat icon="shield_person" label="Admins" value={metrics.admins} />
            <Stat icon="flag" label="Flagged" value={metrics.flaggedUsers} tone="warning" />
            <Stat icon="lock" label="Locked" value={metrics.lockedUsers} tone="error" />
            <Stat icon="receipt_long" label="Receipts" value={metrics.totalReceipts} />
            <Stat icon="request_quote" label="Debts" value={metrics.totalDebts} />
          </div>

          {/* OCR quota gauge */}
          <OcrQuota
            used={metrics.ocrScansThisMonth}
            limit={metrics.ocrMonthlyLimit}
            totalReceipts={metrics.totalReceipts}
          />

          <div className="grid lg:grid-cols-2 gap-space-lg items-start">
            {/* Registrations chart */}
            <Card className="flex flex-col gap-space-sm">
              <span className="font-headline-sm text-on-surface">Registrations (last 14 days)</span>
              <BarChart points={metrics.registrationsByDay} />
            </Card>

            {/* OCR usage by month */}
            <Card className="flex flex-col gap-space-sm">
              <span className="font-headline-sm text-on-surface">OCR scans by month</span>
              {metrics.ocrUsageByMonth.length === 0 ? (
                <Empty text="No cloud OCR usage recorded yet." icon="document_scanner" />
              ) : (
                <BarChart points={metrics.ocrUsageByMonth} />
              )}
            </Card>
          </div>

          {/* Trust rankings */}
          <div className="grid lg:grid-cols-2 gap-space-lg items-start">
            <RankCard title="Highest trust" users={metrics.topTrust} />
            <RankCard title="Lowest trust" users={metrics.bottomTrust} />
          </div>

          {/* Country tax / VAT settings */}
          <TaxSettingsCard onError={setError} />

          {/* User management */}
          <Card className="flex flex-col gap-space-sm">
            <span className="font-headline-sm text-on-surface">User management</span>
            <div className="relative">
              <Icon name="search" className="absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px]" />
              <input
                className="field-input pl-10"
                placeholder="Search users by name, @username, or email"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && loadUsers()}
              />
            </div>
            <div className="flex flex-col gap-space-2xs mt-space-2xs">
              {users.length === 0 ? (
                <Empty text="No users." icon="group" />
              ) : (
                users.map((u) => (
                  <div key={u.id} className="flex items-center justify-between gap-space-sm rt-card-inner">
                    <div className="flex flex-col min-w-0">
                      <span className="font-headline-sm text-on-surface truncate flex items-center gap-space-2xs">
                        {u.fullName}
                        {u.role === 'ADMIN' && (
                          <span className="pill bg-secondary/15 text-secondary text-[10px] px-1.5 py-0">Admin</span>
                        )}
                        {u.flagged && (
                          <span className="pill bg-tertiary/15 text-tertiary text-[10px] px-1.5 py-0">Flagged</span>
                        )}
                        {u.locked && (
                          <span className="pill bg-error-container/40 text-error text-[10px] px-1.5 py-0">Locked</span>
                        )}
                      </span>
                      <span className="font-label-sm text-on-surface-variant truncate">
                        @{u.username} · {u.trustScore} ⭐ · {u.lastActiveAt ? `active ${new Date(u.lastActiveAt).toLocaleDateString()}` : 'never active'}
                      </span>
                    </div>
                    <div className="flex items-center gap-space-2xs shrink-0">
                      <button
                        className={`pill px-space-sm py-1 border transition-colors ${
                          u.flagged ? 'bg-tertiary/20 text-tertiary border-transparent' : 'bg-surface-container text-on-surface-variant border-white/[0.08]'
                        }`}
                        onClick={() => moderate(u.id, { flagged: !u.flagged })}
                      >
                        <Icon name="flag" className="text-[14px]" /> {u.flagged ? 'Unflag' : 'Flag'}
                      </button>
                      <button
                        className={`pill px-space-sm py-1 border transition-colors ${
                          u.locked ? 'bg-error-container/40 text-error border-transparent' : 'bg-surface-container text-on-surface-variant border-white/[0.08]'
                        }`}
                        onClick={() => moderate(u.id, { locked: !u.locked })}
                      >
                        <Icon name={u.locked ? 'lock_open' : 'lock'} className="text-[14px]" /> {u.locked ? 'Unlock' : 'Lock'}
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>
          </Card>

          <button className="btn-glass self-start" onClick={() => navigate('/')}>
            <Icon name="arrow_back" className="text-[16px]" /> Back to dashboard
          </button>
        </>
      )}
    </div>
  );
}

function Stat({
  icon,
  label,
  value,
  tone = 'default',
}: {
  icon: string;
  label: string;
  value: number;
  tone?: 'default' | 'warning' | 'error';
}) {
  const color = tone === 'error' ? 'text-error' : tone === 'warning' ? 'text-tertiary' : 'text-secondary';
  return (
    <div className="rt-card flex flex-col gap-space-2xs py-space-md">
      <Icon name={icon} className={`${color} text-[22px]`} />
      <span className="font-headline-md text-on-surface">{value}</span>
      <span className="font-label-sm text-on-surface-variant">{label}</span>
    </div>
  );
}

function OcrQuota({ used, limit, totalReceipts }: { used: number; limit: number; totalReceipts: number }) {
  const pct = Math.min(100, limit > 0 ? (used / limit) * 100 : 0);
  const danger = pct >= 90;
  const warn = pct >= 70;
  const barColor = danger ? 'bg-error' : warn ? 'bg-tertiary' : 'bg-secondary';
  return (
    <Card className="flex flex-col gap-space-sm">
      <div className="flex items-center justify-between">
        <span className="font-headline-sm text-on-surface">OCR usage this month</span>
        <span className={`font-label-md ${danger ? 'text-error' : warn ? 'text-tertiary' : 'text-on-surface-variant'}`}>
          {used} / {limit} scans
        </span>
      </div>
      <div className="w-full bg-surface-container rounded-full h-3 overflow-hidden">
        <div className={`${barColor} h-full rounded-full transition-all duration-700`} style={{ width: `${pct}%` }} />
      </div>
      <span className="font-label-sm text-on-surface-variant">
        {Math.max(0, limit - used)} scans left on the free tier · {totalReceipts} receipts uploaded all-time
      </span>
    </Card>
  );
}

/** Minimal dependency-free SVG bar chart. */
function BarChart({ points }: { points: AdminMetricPoint[] }) {
  if (points.length === 0) return <Empty text="No data yet." icon="bar_chart" />;
  const max = Math.max(1, ...points.map((p) => p.value));
  const w = 100 / points.length;
  return (
    <div className="flex flex-col gap-space-2xs">
      <svg viewBox="0 0 100 40" className="w-full h-28" preserveAspectRatio="none">
        {points.map((p, i) => {
          const h = (p.value / max) * 36;
          return (
            <rect
              key={i}
              x={i * w + w * 0.15}
              y={40 - h}
              width={w * 0.7}
              height={Math.max(0.5, h)}
              rx={0.6}
              className="fill-secondary"
            >
              <title>{`${p.label}: ${p.value}`}</title>
            </rect>
          );
        })}
      </svg>
      <div className="flex justify-between font-label-sm text-on-surface-variant">
        <span>{points[0].label}</span>
        <span>peak {max}</span>
        <span>{points[points.length - 1].label}</span>
      </div>
    </div>
  );
}

function RankCard({ title, users }: { title: string; users: AdminUser[] }) {
  return (
    <Card className="flex flex-col gap-space-sm">
      <span className="font-headline-sm text-on-surface">{title}</span>
      {users.length === 0 ? (
        <Empty text="No users." icon="leaderboard" />
      ) : (
        users.map((u, i) => (
          <div key={u.id} className="flex items-center justify-between py-space-2xs border-b border-white/[0.05] last:border-0">
            <span className="font-body-md text-on-surface truncate flex items-center gap-space-2xs">
              <span className="font-label-sm text-on-surface-variant w-5">{i + 1}.</span>
              {u.fullName}
              {u.flagged && <Icon name="flag" className="text-tertiary text-[14px]" />}
              {u.locked && <Icon name="lock" className="text-error text-[14px]" />}
            </span>
            <span className="font-tabular-amount text-on-surface">{u.trustScore}</span>
          </div>
        ))
      )}
    </Card>
  );
}

/**
 * Admin manager for per-country VAT/tax settings. Admins add a country, set its
 * rate, and choose whether tax is included in prices or added on top. These feed
 * the receipt page's VAT defaults.
 */
function TaxSettingsCard({ onError }: { onError: (m: string | null) => void }) {
  const [rows, setRows] = useState<TaxSetting[]>([]);
  const [loading, setLoading] = useState(true);
  const [newCode, setNewCode] = useState('');
  const [newName, setNewName] = useState('');
  const [newRate, setNewRate] = useState('');
  const [newInclusive, setNewInclusive] = useState(true);
  const [savingId, setSavingId] = useState<number | 'new' | null>(null);

  const load = async () => {
    try {
      const r = await taxApi.list();
      setRows(r.data);
    } catch (e) {
      onError(apiErrorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Persist one existing row (rate / mode / name). Country code is the key.
  const saveRow = async (row: TaxSetting, patch: Partial<TaxSetting>) => {
    const next = { ...row, ...patch };
    setRows((prev) => prev.map((r) => (r.id === row.id ? next : r)));
    setSavingId(row.id);
    onError(null);
    try {
      const r = await taxApi.save({
        countryCode: next.countryCode,
        countryName: next.countryName,
        rate: Number(next.rate) || 0,
        inclusive: next.inclusive,
      });
      setRows((prev) => prev.map((x) => (x.id === r.data.id ? r.data : x)));
    } catch (e) {
      onError(apiErrorMessage(e));
      load(); // revert optimistic change on failure
    } finally {
      setSavingId(null);
    }
  };

  const addCountry = async () => {
    if (!newCode.trim() || !newName.trim()) {
      onError('Enter a 2-letter country code and a name.');
      return;
    }
    setSavingId('new');
    onError(null);
    try {
      await taxApi.save({
        countryCode: newCode.trim().toUpperCase(),
        countryName: newName.trim(),
        rate: Number(newRate) || 0,
        inclusive: newInclusive,
      });
      setNewCode('');
      setNewName('');
      setNewRate('');
      setNewInclusive(true);
      await load();
    } catch (e) {
      onError(apiErrorMessage(e));
    } finally {
      setSavingId(null);
    }
  };

  const remove = async (row: TaxSetting) => {
    onError(null);
    try {
      await taxApi.remove(row.id);
      setRows((prev) => prev.filter((r) => r.id !== row.id));
    } catch (e) {
      onError(apiErrorMessage(e));
    }
  };

  return (
    <Card className="flex flex-col gap-space-sm">
      <div className="flex flex-col">
        <span className="font-headline-sm text-on-surface">Country tax / VAT settings</span>
        <span className="t-label-sm text-on-surface-variant">
          Set each country&apos;s rate and whether tax is included in prices or added on top.
        </span>
      </div>

      {loading ? (
        <Empty text="Loading tax settings…" icon="hourglass_empty" />
      ) : (
        <>
          <div className="flex flex-col gap-space-2xs">
            {rows.length === 0 ? (
              <Empty text="No countries configured yet." icon="public" />
            ) : (
              rows.map((row) => (
                <div
                  key={row.id}
                  className="flex flex-wrap items-center gap-space-sm rt-card-inner"
                >
                  <div className="flex items-center gap-space-2xs min-w-0 flex-1">
                    <span className="pill bg-secondary/15 text-secondary px-1.5">{row.countryCode}</span>
                    <span className="font-body-md text-on-surface truncate">{row.countryName}</span>
                  </div>

                  <label className="flex items-center gap-1 t-label-sm text-on-surface-variant">
                    Rate
                    <input
                      className="field-input w-16 px-2 text-right py-1"
                      value={String(row.rate)}
                      onChange={(e) =>
                        setRows((prev) =>
                          prev.map((r) =>
                            r.id === row.id ? { ...r, rate: Number(e.target.value) || 0 } : r
                          )
                        )
                      }
                      onBlur={() => saveRow(row, { rate: row.rate })}
                      inputMode="decimal"
                    />
                    <span>%</span>
                  </label>

                  <div className="flex items-center gap-space-2xs">
                    <TaxModeChip
                      active={row.inclusive}
                      onClick={() => saveRow(row, { inclusive: true })}
                      label="Included"
                    />
                    <TaxModeChip
                      active={!row.inclusive}
                      onClick={() => saveRow(row, { inclusive: false })}
                      label="Added on top"
                    />
                  </div>

                  <button
                    className="pill bg-error-container/40 text-error px-space-sm py-1"
                    onClick={() => remove(row)}
                    disabled={savingId === row.id}
                    title="Remove country"
                  >
                    <Icon name="delete" className="text-[14px]" />
                  </button>
                </div>
              ))
            )}
          </div>

          {/* Add a new country */}
          <div className="flex flex-wrap items-end gap-space-sm pt-space-sm border-t border-white/[0.08]">
            <div className="flex flex-col">
              <span className="t-label-sm text-on-surface-variant mb-space-2xs">Code</span>
              <input
                className="field-input w-16 px-2 py-1 uppercase"
                placeholder="PH"
                maxLength={2}
                value={newCode}
                onChange={(e) => setNewCode(e.target.value)}
              />
            </div>
            <div className="flex flex-col flex-1 min-w-[8rem]">
              <span className="t-label-sm text-on-surface-variant mb-space-2xs">Country</span>
              <input
                className="field-input px-2 py-1"
                placeholder="Philippines"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
              />
            </div>
            <div className="flex flex-col">
              <span className="t-label-sm text-on-surface-variant mb-space-2xs">Rate %</span>
              <input
                className="field-input w-16 px-2 py-1 text-right"
                placeholder="12"
                value={newRate}
                onChange={(e) => setNewRate(e.target.value)}
                inputMode="decimal"
              />
            </div>
            <div className="flex items-center gap-space-2xs pb-1">
              <TaxModeChip active={newInclusive} onClick={() => setNewInclusive(true)} label="Included" />
              <TaxModeChip active={!newInclusive} onClick={() => setNewInclusive(false)} label="Added on top" />
            </div>
            <button
              className="btn-primary px-space-md py-2"
              onClick={addCountry}
              disabled={savingId === 'new'}
            >
              <Icon name="add" className="text-[16px]" /> Add
            </button>
          </div>
        </>
      )}
    </Card>
  );
}

/** Segmented chip for picking a country's tax mode in the admin table. */
function TaxModeChip({
  active,
  onClick,
  label,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={`t-label-sm rounded-full px-space-sm py-1 border transition-colors ${
        active
          ? 'bg-primary-container text-on-primary border-transparent'
          : 'bg-surface-container text-on-surface-variant border-white/[0.08]'
      }`}
    >
      {label}
    </button>
  );
}
