import { useEffect, useRef, useState } from 'react';

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined;
const GSI_SRC = 'https://accounts.google.com/gsi/client';

declare global {
  interface Window {
    google?: any;
  }
}

let scriptPromise: Promise<void> | null = null;

function loadGsi(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (scriptPromise) return scriptPromise;
  scriptPromise = new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = GSI_SRC;
    s.async = true;
    s.defer = true;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error('Failed to load Google Identity Services'));
    document.head.appendChild(s);
  });
  return scriptPromise;
}

/**
 * Renders the official "Sign in with Google" button. Returns null (renders
 * nothing) when VITE_GOOGLE_CLIENT_ID is not configured, so the app works
 * unchanged until Google login is set up.
 */
export default function GoogleButton({
  onCredential,
  onError,
  text = 'continue_with',
}: {
  onCredential: (idToken: string) => void;
  onError?: (message: string) => void;
  text?: 'signin_with' | 'signup_with' | 'continue_with';
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    if (!CLIENT_ID) {
      setUnavailable(true);
      return;
    }
    let cancelled = false;
    loadGsi()
      .then(() => {
        if (cancelled || !ref.current || !window.google?.accounts?.id) return;
        window.google.accounts.id.initialize({
          client_id: CLIENT_ID,
          callback: (resp: { credential?: string }) => {
            if (resp.credential) onCredential(resp.credential);
            else onError?.('Google sign-in was cancelled');
          },
        });
        window.google.accounts.id.renderButton(ref.current, {
          theme: 'filled_black',
          size: 'large',
          shape: 'pill',
          text,
          width: 320,
        });
      })
      .catch(() => {
        setUnavailable(true);
        onError?.('Could not load Google sign-in');
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Not configured -> render nothing (feature simply absent).
  if (!CLIENT_ID || unavailable) return null;

  return <div ref={ref} className="flex justify-center" />;
}

/** True when Google sign-in is configured (client id present). */
export const googleConfigured = Boolean(CLIENT_ID);

/** An "or" divider that only renders when Google sign-in is available. */
export function GoogleDivider() {
  if (!CLIENT_ID) return null;
  return (
    <div className="flex items-center gap-space-sm py-space-2xs">
      <span className="h-px flex-1 bg-white/[0.08]" />
      <span className="font-label-sm text-on-surface-variant">or</span>
      <span className="h-px flex-1 bg-white/[0.08]" />
    </div>
  );
}
