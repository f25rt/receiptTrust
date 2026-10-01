import { FormEvent, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { messageApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import type { DirectMessage } from '../api/types';
import { Avatar, Card, Empty, ErrorBanner, Icon } from '../components/ui';
import { usePolling } from '../lib/usePolling';

export default function ChatPage() {
  const { username = '' } = useParams();
  const navigate = useNavigate();
  const [messages, setMessages] = useState<DirectMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const endRef = useRef<HTMLDivElement | null>(null);

  const load = async () => {
    try {
      const r = await messageApi.conversation(username);
      setMessages(r.data);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [username]);

  // Light polling so a reply shows up without a manual refresh.
  usePolling(load, 5000);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  const send = async (e: FormEvent) => {
    e.preventDefault();
    const body = draft.trim();
    if (!body) return;
    setError(null);
    setSending(true);
    try {
      const r = await messageApi.send(username, body);
      setMessages((prev) => [...prev, r.data]);
      setDraft('');
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="flex flex-col w-full px-margin lg:px-0 pb-space-xl gap-space-md">
      <div className="flex items-center gap-space-sm pt-space-xs">
        <button
          onClick={() => navigate('/friends')}
          className="w-9 h-9 rounded-full bg-surface-container-high flex items-center justify-center text-on-surface-variant active:scale-95 transition-transform"
          aria-label="Back"
        >
          <Icon name="arrow_back" className="text-[20px]" />
        </button>
        <Avatar name={username} size={40} />
        <div className="flex flex-col min-w-0">
          <h1 className="font-headline-lg text-on-surface truncate">@{username}</h1>
          <span className="font-label-sm text-on-surface-variant">Direct message</span>
        </div>
      </div>

      <ErrorBanner message={error} />

      <Card className="flex flex-col gap-space-sm min-h-[50vh]">
        {messages.length === 0 ? (
          <Empty text={`No messages yet. Say hi to @${username}.`} icon="chat" />
        ) : (
          <div className="flex flex-col gap-space-sm">
            {messages.map((m) => (
              <div
                key={m.id}
                className={`flex flex-col gap-space-2xs max-w-[85%] rounded-lg px-space-sm py-space-xs ${
                  m.mine
                    ? 'self-end bg-primary-container/20 items-end'
                    : 'self-start bg-surface-container-high'
                }`}
              >
                <span className="font-body-md text-on-surface whitespace-pre-wrap break-words">{m.body}</span>
                <span className="font-label-sm text-on-surface-variant">
                  {new Date(m.createdAt).toLocaleString()}
                </span>
              </div>
            ))}
            <div ref={endRef} />
          </div>
        )}
      </Card>

      <form className="flex items-end gap-space-sm sticky bottom-20 lg:bottom-0" onSubmit={send}>
        <input
          className="field-input flex-1"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={`Message @${username}…`}
          maxLength={2000}
        />
        <button
          className="btn-primary px-space-md py-3"
          disabled={!draft.trim() || sending}
          aria-label="Send message"
        >
          <Icon name="send" className="text-[18px]" />
        </button>
      </form>
    </div>
  );
}
