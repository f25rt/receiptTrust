import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { apiErrorMessage } from '../api/client';
import { ErrorBanner } from '../components/ui';

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
    <div className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <h1>ReceiptTrust</h1>
        <p className="muted">Sign in to your account</p>
        <ErrorBanner message={error} />
        <label>Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        </label>
        <label>Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        <button className="btn-primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="muted">No account? <Link to="/register">Create one</Link></p>
      </form>
    </div>
  );
}
