import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { apiErrorMessage } from '../api/client';
import { ErrorBanner, Icon } from '../components/ui';
import GoogleButton, { GoogleDivider } from '../auth/GoogleButton';

export default function RegisterPage() {
  const { register, loginWithGoogle } = useAuth();
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

  const onGoogle = async (idToken: string) => {
    setError(null);
    try {
      await loginWithGoogle(idToken);
      navigate('/');
    } catch (err) {
      setError(apiErrorMessage(err));
    }
  };

  return (
    <div className="min-h-screen bg-surface text-on-surface flex items-center justify-center px-margin py-space-xl">
      <div className="w-full max-w-sm flex flex-col gap-space-lg">
        <div className="flex flex-col items-center gap-space-sm">
          <div className="w-14 h-14 rounded-2xl bg-primary-container/20 flex items-center justify-center text-primary">
            <Icon name="verified_user" className="text-[32px]" />
          </div>
          <h1 className="font-headline-lg text-on-surface">Create account</h1>
        </div>

        <form className="rt-card flex flex-col gap-space-md" onSubmit={submit}>
          <ErrorBanner message={error} />
          <div>
            <label className="field-label">Full name</label>
            <input className="field-input" value={fullName} onChange={(e) => setFullName(e.target.value)} autoFocus />
          </div>
          <div>
            <label className="field-label">Username</label>
            <input className="field-input" value={username} onChange={(e) => setUsername(e.target.value)} />
          </div>
          <div>
            <label className="field-label">Email</label>
            <input className="field-input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div>
            <label className="field-label">Password (min 8 chars)</label>
            <input className="field-input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          <button className="btn-primary w-full py-3" disabled={busy}>
            {busy ? 'Creating…' : 'Create account'}
          </button>

          <GoogleDivider />
          <GoogleButton text="signup_with" onCredential={onGoogle} onError={setError} />
        </form>

        <p className="font-body-sm text-on-surface-variant text-center">
          Have an account? <Link to="/login" className="text-primary">Sign in</Link>
        </p>
      </div>
    </div>
  );
}
