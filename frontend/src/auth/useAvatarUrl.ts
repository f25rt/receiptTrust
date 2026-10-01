import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { useAuth } from './AuthContext';

/**
 * Fetches the current user's profile image (auth-protected) as an object URL so
 * it can be shown in an <img>. Returns null when there is no image or it has
 * expired; callers then fall back to the initials avatar.
 *
 * Re-fetches whenever the profile's image path changes (upload or expiry).
 */
export function useAvatarUrl(): string | null {
  const { profile } = useAuth();
  const [url, setUrl] = useState<string | null>(null);
  const key = profile?.profileImagePath ?? null;

  useEffect(() => {
    let cancelled = false;
    let created: string | null = null;

    if (!key) {
      setUrl(null);
      return;
    }
    api
      .get('/me/profile-image', { responseType: 'blob' })
      .then((resp) => {
        if (cancelled) return;
        created = URL.createObjectURL(resp.data as Blob);
        setUrl(created);
      })
      .catch(() => {
        if (!cancelled) setUrl(null);
      });

    return () => {
      cancelled = true;
      if (created) URL.revokeObjectURL(created);
    };
  }, [key]);

  return url;
}
