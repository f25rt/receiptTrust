import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

const ACCESS_KEY = 'rt_access';
const REFRESH_KEY = 'rt_refresh';

export const tokenStore = {
  get access() {
    return localStorage.getItem(ACCESS_KEY);
  },
  get refresh() {
    return localStorage.getItem(REFRESH_KEY);
  },
  set(access: string, refresh: string) {
    localStorage.setItem(ACCESS_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

// In dev, Vite proxies "/api" to the backend. In production (static hosting),
// set VITE_API_BASE_URL to the deployed backend origin, e.g. https://receipttrust-api.onrender.com
export const API_BASE = `${import.meta.env.VITE_API_BASE_URL ?? ''}/api`;

export const api = axios.create({
  baseURL: API_BASE,
});

// Attach the access token to every request.
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.access;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// On a 401, try a single refresh-token rotation, then replay the request.
let refreshing: Promise<string | null> | null = null;

async function tryRefresh(): Promise<string | null> {
  const refresh = tokenStore.refresh;
  if (!refresh) return null;
  try {
    const resp = await axios.post(`${API_BASE}/auth/refresh`, { refreshToken: refresh });
    tokenStore.set(resp.data.accessToken, resp.data.refreshToken);
    return resp.data.accessToken;
  } catch {
    tokenStore.clear();
    return null;
  }
}

api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retried?: boolean };
    const status = error.response?.status;
    const isAuthCall = original?.url?.includes('/auth/');

    if (status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      refreshing = refreshing ?? tryRefresh();
      const newToken = await refreshing;
      refreshing = null;
      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      }
      // Refresh failed: bounce to login.
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export function apiErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { message?: string } | undefined;
    return data?.message ?? err.message;
  }
  return 'Unexpected error';
}
