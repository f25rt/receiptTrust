import { createContext, useContext, useEffect, useMemo, useState, ReactNode } from 'react';
import { AUTH_LOGOUT_EVENT, tokenStore } from '../api/client';
import { authApi, profileApi } from '../api/services';
import type { MyProfile } from '../api/types';
import { setDisplayCurrency } from '../lib/format';

interface AuthState {
  profile: MyProfile | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<MyProfile | null>;
  loginWithGoogle: (idToken: string) => Promise<MyProfile | null>;
  register: (
    fullName: string,
    username: string,
    email: string,
    password: string,
    country?: string
  ) => Promise<void>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<MyProfile | null>;
}

const AuthCtx = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [profile, setProfile] = useState<MyProfile | null>(null);
  const [loading, setLoading] = useState(true);

  const loadProfile = async (): Promise<MyProfile | null> => {
    if (!tokenStore.access) {
      setProfile(null);
      setDisplayCurrency('USD');
      return null;
    }
    try {
      const resp = await profileApi.me();
      setProfile(resp.data);
      setDisplayCurrency(resp.data.currency);
      return resp.data;
    } catch {
      setProfile(null);
      return null;
    }
  };

  useEffect(() => {
    loadProfile().finally(() => setLoading(false));
  }, []);

  // When the API client can't refresh an expired token, it clears tokens and
  // fires this event. Reset auth state and send the user to the login screen.
  useEffect(() => {
    const onForcedLogout = () => {
      setProfile(null);
      if (window.location.pathname !== '/login') {
        window.location.assign('/login');
      }
    };
    window.addEventListener(AUTH_LOGOUT_EVENT, onForcedLogout);
    return () => window.removeEventListener(AUTH_LOGOUT_EVENT, onForcedLogout);
  }, []);

  const login = async (username: string, password: string) => {
    const resp = await authApi.login(username, password);
    tokenStore.set(resp.data.accessToken, resp.data.refreshToken);
    return loadProfile();
  };

  const loginWithGoogle = async (idToken: string) => {
    const resp = await authApi.google(idToken);
    tokenStore.set(resp.data.accessToken, resp.data.refreshToken);
    return loadProfile();
  };

  const register = async (
    fullName: string,
    username: string,
    email: string,
    password: string,
    country?: string
  ) => {
    await authApi.register(fullName, username, email, password, country);
    await login(username, password);
  };

  const logout = async () => {
    const refresh = tokenStore.refresh;
    if (refresh) {
      try {
        await authApi.logout(refresh);
      } catch {
        // ignore; clear locally regardless
      }
    }
    tokenStore.clear();
    setProfile(null);
    setDisplayCurrency('USD');
  };

  const value = useMemo<AuthState>(
    () => ({ profile, loading, login, loginWithGoogle, register, logout, refreshProfile: loadProfile }),
    [profile, loading]
  );

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthCtx);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
