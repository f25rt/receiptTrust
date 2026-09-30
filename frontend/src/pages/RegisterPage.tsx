import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { apiErrorMessage } from '../api/client';
import { ErrorBanner } from '../components/ui';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await register(fullName, username, email, password);
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
        <h1>Create account</h1>
        <ErrorBanner message={error} />
        <label>Full name
          <input value={fullName} onChange={(e) => setFullName(e.target.value)} autoFocus />
        </label>
        <label>Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} />
        </label>
        <label>Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>Password (min 8 chars)
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        <button className="btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
        <p className="muted">Have an account? <Link to="/login">Sign in</Link></p>
      </form>
    </div>
  );
}
