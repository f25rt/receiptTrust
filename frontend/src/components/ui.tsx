import { ReactNode } from 'react';

export function Card({ title, children, actions }: { title?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <div className="card-head">
          {title && <h2>{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

export function ErrorBanner({ message }: { message: string | null }) {
  if (!message) return null;
  return <div className="banner error">{message}</div>;
}

export function Empty({ text }: { text: string }) {
  return <p className="muted">{text}</p>;
}

export function money(value: string | number): string {
  const n = typeof value === 'string' ? Number(value) : value;
  return `$${n.toFixed(2)}`;
}
