import type { Currency, ReputationLevel } from '../api/types';

const CURRENCY_SYMBOLS: Record<Currency, string> = {
  USD: '$',
  PHP: '₱',
};

// App-wide display currency, set from the logged-in user's profile. Defaults to
// USD until a profile loads. Updated via setDisplayCurrency() in AuthContext.
let displayCurrency: Currency = 'USD';

export function setDisplayCurrency(currency: Currency | null | undefined) {
  displayCurrency = currency === 'PHP' ? 'PHP' : 'USD';
}

export function currencySymbol(currency?: Currency): string {
  return CURRENCY_SYMBOLS[currency ?? displayCurrency] ?? '$';
}

/** Rounds to a whole number and groups thousands, e.g. 1523.09 -> "1,523". */
function wholeAmount(n: number): string {
  return Math.round(n).toLocaleString('en-US', { maximumFractionDigits: 0 });
}

/** Format a monetary value using the active display currency (no decimals). */
export function money(value: string | number): string {
  const n = typeof value === 'string' ? Number(value) : value;
  return `${currencySymbol()}${wholeAmount(n)}`;
}

/** Signed currency, no decimals, e.g. +$143 / -₱38. */
export function signedMoney(value: string | number, positive: boolean): string {
  const n = Math.abs(typeof value === 'string' ? Number(value) : value);
  return `${positive ? '+' : '-'}${currencySymbol()}${wholeAmount(n)}`;
}

export interface TrustTier {
  label: string;
  /** tailwind text color class */
  color: string;
  /** tailwind bg class for the pill */
  bg: string;
}

/**
 * Display tier derived from a trust score, following the redesign's
 * peer-trust scoring tiers (Starter/Fair/Reliable/Trusted/Elite).
 */
export function trustTier(score: number): TrustTier {
  if (score >= 900) return { label: 'Elite', color: 'text-tertiary', bg: 'bg-tertiary/20' };
  if (score >= 750) return { label: 'Trusted', color: 'text-secondary', bg: 'bg-secondary/15' };
  if (score >= 600) return { label: 'Reliable', color: 'text-secondary-fixed-dim', bg: 'bg-surface-container-highest' };
  if (score >= 500) return { label: 'Fair', color: 'text-on-surface-variant', bg: 'bg-surface-container-highest' };
  return { label: 'Starter', color: 'text-on-surface-variant', bg: 'bg-surface-container-highest' };
}

/** Human label for the backend reputation enum. */
export function reputationLabel(level: ReputationLevel): string {
  return level
    .split('_')
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(' ');
}

/** Max score used for the trust progress bar / reputation ring. */
export const MAX_TRUST_SCORE = 850;

/** Reputation-engine tiers with the next threshold, for the ring + "points to next tier". */
const TIER_THRESHOLDS = [
  { label: 'Starter', min: 300 },
  { label: 'Fair', min: 500 },
  { label: 'Reliable', min: 600 },
  { label: 'Trusted', min: 750 },
  { label: 'Elite', min: 900 },
];

export function nextTier(score: number): { label: string; min: number } | null {
  return TIER_THRESHOLDS.find((t) => t.min > score) ?? null;
}

export function pointsToNextTier(score: number): number | null {
  const next = nextTier(score);
  return next ? next.min - score : null;
}
