import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { profileApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Avatar, Card, ErrorBanner, Icon } from '../components/ui';
import { trustTier, reputationLabel, nextTier, pointsToNextTier } from '../lib/format';

const REPUTATION_MAX = 1000;

/** Circular reputation ring rendered with SVG. */
function ReputationRing({ score }: { score: number }) {
  const size = 160;
  const stroke = 12;
  const r = (size - stroke) / 2;
  const circ = 2 * Math.PI * r;
  const pct = Math.min(1, score / REPUTATION_MAX);
  return (
    <div className="relative" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} stroke="currentColor" strokeWidth={stroke}
          className="text-surface-container-high" fill="none" />
        <circle cx={size / 2} cy={size / 2} r={r} stroke="currentColor" strokeWidth={stroke}
          className="text-secondary" fill="none" strokeLinecap="round"
          strokeDasharray={circ} strokeDashoffset={circ * (1 - pct)}
          style={{ transition: 'stroke-dashoffset 0.7s ease-out' }} />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="font-display text-on-surface leading-none">{score}</span>
        <span className="font-label-sm text-on-surface-variant mt-space-2xs">
          out of {REPUTATION_MAX} pts
        </span>
      </div>
    </div>
  );
}

export default function ProfilePage() {
  const { profile, refreshProfile, logout } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!profile) return null;

  const tier = trustTier(profile.trustScore);
  const next = nextTier(profile.trustScore);
  const toNext = pointsToNextTier(profile.trustScore);
  const avgDays =
    profile.averageRepaymentDays === null ? '—' : `< ${Math.ceil(profile.averageRepaymentDays)}d`;

  const upload = async (file: File) => {
    setError(null);
    setBusy(true);
    try {
      await profileApi.uploadImage(file);
      await refreshProfile();
    } catch (e) {
      setError(apiErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="flex flex-col w-full px-margin lg:px-0 pb-space-xl gap-space-lg">
      {/* Identity */}
      <div className="flex flex-col items-center lg:flex-row lg:items-center pt-space-md gap-space-sm lg:gap-space-md">
        <div className="relative">
          <Avatar name={profile.fullName} size={80} imagePath={profile.profileImagePath} />
          <span className="absolute bottom-0 right-0 w-6 h-6 rounded-full bg-secondary ring-4 ring-surface flex items-center justify-center text-on-secondary">
            <Icon name="verified" className="text-[14px]" />
          </span>
        </div>
        <div className="flex flex-col items-center lg:items-start">
          <h1 className="font-headline-lg lg:text-display text-on-surface flex items-center gap-space-2xs">
            {profile.fullName}
          </h1>
          <span className="font-body-sm text-on-surface-variant">
            @{profile.username} · {profile.email}
          </span>
          <span className="font-label-sm text-tertiary flex items-center gap-1 mt-space-2xs">
            <span className="w-1.5 h-1.5 rounded-full bg-tertiary" /> Verified Member
          </span>
        </div>
      </div>

      <ErrorBanner message={error} />

      <div className="flex flex-col gap-space-lg lg:grid lg:grid-cols-2 lg:gap-space-lg lg:items-start">
      {/* Left: reputation engine */}
      <Card className="flex flex-col gap-space-md">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-space-xs">
            <Icon name="shield" className="text-secondary text-[20px]" />
            <span className="font-headline-sm text-on-surface">Reputation Engine</span>
          </div>
          <span className={`pill ${tier.bg} ${tier.color} px-space-sm py-1`}>{tier.label} Tier</span>
        </div>

        <div className="flex justify-center py-space-xs">
          <ReputationRing score={profile.trustScore} />
        </div>

        <div className="flex items-center justify-between font-label-sm">
          <span className="text-on-surface-variant">{reputationLabel(profile.reputationLevel)}</span>
          {toNext !== null && next ? (
            <span className="text-secondary">{toNext} pts to {next.label}</span>
          ) : (
            <span className="text-tertiary">Top tier reached</span>
          )}
        </div>

        {next && (
          <div className="flex items-start gap-space-xs bg-surface-container-high/60 p-space-sm rounded-lg">
            <Icon name="lock" className="text-secondary text-[18px] shrink-0 mt-0.5" />
            <p className="t-body-sm text-on-surface-variant">
              Reach the <strong className="text-on-surface font-medium">{next.label}</strong> tier
              by settling debts on time. Every full, on-time settlement raises your score.
            </p>
          </div>
        )}
      </Card>

      {/* Right column */}
      <div className="flex flex-col gap-space-lg">
      {/* Financial ledger */}
      <div className="flex flex-col gap-space-sm">
        <div className="flex items-center justify-between">
          <h2 className="font-headline-md text-on-surface">Financial Ledger</h2>
          <span className="font-label-sm text-secondary flex items-center gap-1">
            <Icon name="verified" className="text-[14px]" /> Verified record
          </span>
        </div>
        <div className="grid grid-cols-3 gap-space-sm">
          <Metric icon="task_alt" label="Settled" value={profile.debtsSettled} />
          <Metric icon="pending_actions" label="Active" value={profile.currentDebts} />
          <Metric icon="bolt" label="Repay speed" value={avgDays} />
        </div>
      </div>

      {/* Avatar upload */}
      <Card className="flex flex-col gap-space-sm">
        <span className="font-headline-sm text-on-surface">Profile image</span>
        <label className="btn-glass w-full py-3 cursor-pointer">
          <Icon name="photo_camera" className="text-[18px]" />
          {busy ? 'Uploading…' : 'Upload avatar (JPG or PNG)'}
          <input
            type="file"
            accept="image/jpeg,image/png"
            className="hidden"
            disabled={busy}
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) upload(f);
            }}
          />
        </label>
      </Card>

      {/* Sign-in method / set password */}
      <SetPasswordCard />

      {/* Log out */}
      <button className="btn-destructive w-full py-3" onClick={handleLogout}>
        <Icon name="logout" className="text-[18px]" />
        Log out of ReceiptTrust
      </button>
      </div>
      </div>
    </div>
  );
}

function Metric({ icon, label, value }: { icon: string; label: string; value: number | string }) {
  return (
    <div className="rt-card flex flex-col items-center gap-space-2xs py-space-md">
      <Icon name={icon} className="text-secondary text-[22px]" />
      <span className="font-headline-md text-on-surface">{value}</span>
      <span className="font-label-sm text-on-surface-variant">{label}</span>
    </div>
  );
}

function SetPasswordCard() {
  const { profile, refreshProfile } = useAuth();
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  if (!profile) return null;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setDone(false);
    setBusy(true);
    try {
      await profileApi.setPassword(password);
      setPassword('');
      setDone(true);
      await refreshProfile();
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="flex flex-col gap-space-sm">
      <span className="font-headline-sm text-on-surface">Sign-in method</span>
      <div className="flex items-center gap-space-sm">
        <div className="w-9 h-9 rounded-full bg-surface-container-high flex items-center justify-center text-secondary">
          <Icon name={profile.provider === 'LOCAL' ? 'password' : 'account_circle'} className="text-[18px]" />
        </div>
        <div className="flex flex-col">
          <span className="font-body-md text-on-surface">
            {profile.provider === 'GOOGLE' ? 'Google' : profile.provider === 'FACEBOOK' ? 'Facebook' : 'Email & password'}
          </span>
          <span className="font-label-sm text-on-surface-variant">
            {profile.hasPassword ? 'Password sign-in enabled' : 'No password set'}
          </span>
        </div>
      </div>

      {!profile.hasPassword && (
        <p className="font-body-sm text-on-surface-variant">
          You signed up with {profile.provider === 'GOOGLE' ? 'Google' : 'a social account'}. Set a
          password to also sign in with your username and password.
        </p>
      )}

      <ErrorBanner message={error} />
      {done && (
        <div className="flex items-center gap-space-2xs text-tertiary font-label-md">
          <Icon name="check_circle" className="text-[16px]" /> Password updated
        </div>
      )}

      <form className="flex flex-col gap-space-sm" onSubmit={submit}>
        <div>
          <label className="field-label">{profile.hasPassword ? 'New password' : 'Set a password'} (min 8 chars)</label>
          <input
            className="field-input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={8}
            required
          />
        </div>
        <button className="btn-glass w-full py-3" disabled={busy}>
          <Icon name="lock" className="text-[18px]" />
          {busy ? 'Saving…' : profile.hasPassword ? 'Change password' : 'Set password'}
        </button>
      </form>
    </Card>
  );
}

