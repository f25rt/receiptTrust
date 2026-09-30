import { FormEvent, useEffect, useState } from 'react';
import { friendApi, profileApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import type { FriendRequest, SearchResult, UserSummary } from '../api/types';
import { Card, Empty, ErrorBanner } from '../components/ui';

export default function FriendsPage() {
  const [friends, setFriends] = useState<UserSummary[]>([]);
  const [requests, setRequests] = useState<FriendRequest[]>([]);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try {
      const [f, r] = await Promise.all([friendApi.list(), friendApi.incoming()]);
      setFriends(f.data);
      setRequests(r.data);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  useEffect(() => {
    load();
  }, []);

  const search = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const r = await profileApi.search(query);
      setResults(r.data);
    } catch (e2) {
      setError(apiErrorMessage(e2));
    }
  };

  const act = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
      await load();
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <div className="page">
      <h1>Friends</h1>
      <ErrorBanner message={error} />

      <Card title="Find people">
        <form className="inline-form" onSubmit={search}>
          <input
            placeholder="Search by username or email"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <button className="btn-primary">Search</button>
        </form>
        {results.length > 0 && (
          <ul className="list">
            {results.map((u) => (
              <li key={u.id}>
                <span>{u.fullName} <span className="muted">@{u.username}</span></span>
                <button className="btn-ghost" onClick={() => act(() => friendApi.send(u.username))}>
                  Add friend
                </button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card title={`Incoming requests (${requests.length})`}>
        {requests.length === 0 ? (
          <Empty text="No pending requests." />
        ) : (
          <ul className="list">
            {requests.map((req) => (
              <li key={req.requestId}>
                <span>{req.requester.fullName} <span className="muted">@{req.requester.username}</span></span>
                <span className="row-actions">
                  <button className="btn-primary" onClick={() => act(() => friendApi.accept(req.requestId))}>Accept</button>
                  <button className="btn-ghost" onClick={() => act(() => friendApi.reject(req.requestId))}>Reject</button>
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card title={`My friends (${friends.length})`}>
        {friends.length === 0 ? (
          <Empty text="No friends yet. Search above to connect." />
        ) : (
          <ul className="list">
            {friends.map((f) => (
              <li key={f.id}>
                <span>{f.fullName} <span className="muted">@{f.username}</span>
                  <span className="score-badge small">{f.trustScore}</span>
                </span>
                <button className="btn-ghost" onClick={() => act(() => friendApi.remove(f.id))}>Remove</button>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
