import { ReactNode } from 'react';

/** Material Symbols icon. */
export function Icon({ name, className = '' }: { name: string; className?: string }) {
  return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

export function Card({
  children,
  className = '',
}: {
  children: ReactNode;
  className?: string;
}) {
  return <section className={`rt-card ${className}`}>{children}</section>;
}

export function SectionTitle({ children }: { children: ReactNode }) {
  return <h2 className="font-headline-md text-on-surface">{children}</h2>;
}

export function ErrorBanner({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="banner-error">
      <Icon name="error" className="text-[18px] shrink-0 mt-0.5" />
      <span>{message}</span>
    </div>
  );
}

export function Empty({ text, icon = 'inbox' }: { text: string; icon?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-space-xs py-space-lg text-center">
      <div className="w-12 h-12 rounded-full bg-surface-container-high flex items-center justify-center text-on-surface-variant">
        <Icon name={icon} className="text-[24px]" />
      </div>
      <p className="font-body-sm text-on-surface-variant">{text}</p>
    </div>
  );
}

/** Avatar with an initial fallback (the app has no avatar URLs by default). */
export function Avatar({
  name,
  size = 44,
  imagePath,
}: {
  name: string;
  size?: number;
  imagePath?: string | null;
}) {
  const initial = (name || '?').charAt(0).toUpperCase();
  return (
    <div
      className="rounded-full bg-surface-container-high flex items-center justify-center shrink-0 overflow-hidden text-on-surface-variant font-headline-sm"
      style={{ width: size, height: size }}
    >
      {imagePath ? (
        <img src={imagePath} alt={name} className="w-full h-full object-cover" />
      ) : (
        <span>{initial}</span>
      )}
    </div>
  );
}

/** Small trust-score pill: number + tier styling. */
export function TrustPill({
  score,
  tierLabel,
  tierColor,
  tierBg,
}: {
  score: number;
  tierLabel?: string;
  tierColor: string;
  tierBg: string;
}) {
  return (
    <span className={`pill ${tierBg} ${tierColor} shrink-0`}>
      <span>{score}</span>
      <span className="text-[9px]">⭐{tierLabel ? ` ${tierLabel}` : ''}</span>
    </span>
  );
}
