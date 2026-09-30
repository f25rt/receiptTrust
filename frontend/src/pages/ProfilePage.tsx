import { useState } from 'react';
import { profileApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Card, ErrorBanner } from '../components/ui';

export default function ProfilePage() {
  const { profile, refreshProfile } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!profile) return null;

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

  const avgDays =
    profile.averageRepaymentDays === null
      ? '—'
      : `${profile.averageRepaymentDays.toFixed(1)} days`;

  return (
    <div className="page">
      <h1>{profile.fullName}</h1>
      <p className="muted">@{profile.username} · {profile.email}</p>
      <ErrorBanner message={error} />

      <div className="grid-2">
        <Card title="Trust reputation">
          <div className="trust-hero">
            <div className="trust-score">{profile.trustScore}</div>
            <div className="trust-level">{profile.reputationLevel.replace(/_/g, ' ')}</div>
          </div>
          <div className="metrics">
            <div className="metric"><span>Debts settled</span><strong>{profile.debtsSettled}</strong></div>
            <div className="metric"><span>Current debts</span><strong>{profile.currentDebts}</strong></div>
            <div className="metric"><span>Avg repayment</span><strong>{avgDays}</strong></div>
          </div>
        </Card>

        <Card title="Profile image">
          <label>Upload a new avatar (JPG or PNG)
            <input
              type="file"
              accept="image/jpeg,image/png"
              disabled={busy}
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) upload(f);
              }}
            />
          </label>
          {profile.profileImagePath && <p className="muted">Current: {profile.profileImagePath}</p>}
        </Card>
      </div>
    </div>
  );
}
