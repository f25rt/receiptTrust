import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { apiErrorMessage } from '../api/client';
import { ErrorBanner, Icon } from '../components/ui';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(username, password);
      navigate('/');
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface text-on-surface flex items-center justify-center px-margin">
      <div className="w-full max-w-sm flex flex-col gap-space-lg">
        <div className="flex flex-col items-center gap-space-sm">
          <div className="w-14 h-14 rounded-2xl bg-primary-container/20 flex items-center justify-center text-primary">
            <Icon name="verified_user" className="text-[32px]" />
          </div>
          <h1 className="font-headline-lg text-on-surface">ReceiptTrust</h1>
          <p className="font-body-sm text-on-surface-variant">Receipt-backed debt &amp; trust</p>
        </div>

        <form className="rt-card flex flex-col gap-space-md" onSubmit={submit}>
          <ErrorBanner message={error} />
          <div>
            <label className="field-label">Username</label>
            <input className="field-input" value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
          </div>
          <div>
            <label className="field-label">Password</label>
            <input className="field-input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          <button className="btn-primary w-full py-3" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="font-body-sm text-on-surface-variant text-center">
          No account? <Link to="/register" className="text-primary">Create one</Link>
        </p>
      </div>
    </div>
  );
}
