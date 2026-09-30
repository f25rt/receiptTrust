import { FormEvent, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { debtApi, paymentApi, receiptApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import type {
  DebtExplanation,
  DebtHistory,
  DebtSummary,
  Payment,
  PaymentMethod,
} from '../api/types';
import { Card, Empty, ErrorBanner, Icon } from '../components/ui';
import { money } from '../lib/format';

const METHODS: PaymentMethod[] = ['CASH', 'BANK_TRANSFER', 'GCASH', 'MAYA', 'OTHER'];

const EVENT_ICON: Record<string, string> = {
  RECEIPT_UPLOADED: 'receipt_long',
  DEBT_CREATED: 'request_quote',
  PAYMENT_SUBMITTED: 'payments',
  PAYMENT_APPROVED: 'check_circle',
  PAYMENT_REJECTED: 'cancel',
  DEBT_SETTLED: 'verified',
};

export default function DebtDetailPage() {
  const { id } = useParams();
  const debtId = Number(id);
  const { profile, refreshProfile } = useAuth();
  const navigate = useNavigate();

  const [debt, setDebt] = useState<DebtSummary | null>(null);
  const [explanation, setExplanation] = useState<DebtExplanation | null>(null);
  const [history, setHistory] = useState<DebtHistory | null>(null);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<PaymentMethod>('GCASH');
  const [notes, setNotes] = useState('');

  const load = async () => {
    try {
      const [d, ex, h, p] = await Promise.all([
        debtApi.get(debtId),
        debtApi.explanation(debtId),
        debtApi.history(debtId),
        paymentApi.list(debtId),
      ]);
      setDebt(d.data);
      setExplanation(ex.data);
      setHistory(h.data);
      setPayments(p.data);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debtId]);

  const iAmCreditor = explanation?.paidByUsername === profile?.username;

  const submitPayment = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await paymentApi.submit(debtId, amount, method, notes);
      setAmount('');
      setNotes('');
      await load();
    } catch (err) {
      setError(apiErrorMessage(err));
    }
  };

  const decide = async (paymentId: number, approve: boolean) => {
    setError(null);
    try {
      if (approve) await paymentApi.approve(paymentId);
      else await paymentApi.reject(paymentId);
      await Promise.all([load(), refreshProfile()]);
    } catch (err) {
      setError(apiErrorMessage(err));
    }
  };

  if (!debt || !explanation) {
    return (
      <div className="flex flex-col w-full px-margin pt-space-md gap-space-md">
        <ErrorBanner message={error} />
        <Empty text="Loading debt…" icon="hourglass_empty" />
      </div>
    );
  }

  const settled = debt.status === 'SETTLED';
  const paidPct =
    (1 - Number(debt.outstandingAmount) / Number(debt.originalAmount)) * 100;

  return (
    <div className="flex flex-col w-full px-margin lg:px-0 pb-space-xl gap-space-lg">
      <div className="flex items-center gap-space-sm pt-space-xs">
        <button
          onClick={() => navigate(-1)}
          className="w-9 h-9 rounded-full bg-surface-container-high flex items-center justify-center text-on-surface-variant active:scale-95 transition-transform"
          aria-label="Back"
        >
          <Icon name="arrow_back" className="text-[20px]" />
        </button>
        <div className="flex flex-col">
          <h1 className="font-headline-lg text-on-surface">Debt #{debt.debtId}</h1>
          <span className="font-label-sm text-on-surface-variant">
            {iAmCreditor ? `${debt.counterpartyUsername} owes you` : `You owe ${debt.counterpartyUsername}`}
          </span>
        </div>
      </div>

      <ErrorBanner message={error} />

      {/* Amount summary */}
      <Card className="flex flex-col gap-space-md">
        <div className="flex items-center justify-between">
          <div className="flex flex-col">
            <span className="font-label-sm text-on-surface-variant uppercase tracking-wider">
              Outstanding
            </span>
            <span className={`font-display ${settled ? 'text-tertiary' : iAmCreditor ? 'text-tertiary' : 'text-error'}`}>
              {money(debt.outstandingAmount)}
            </span>
          </div>
          <span
            className={`pill px-space-sm py-1 ${
              settled ? 'bg-tertiary/20 text-tertiary' : 'bg-surface-container-high text-on-surface-variant'
            }`}
          >
            {settled ? <Icon name="verified" className="text-[14px]" /> : <Icon name="schedule" className="text-[14px]" />}
            {debt.status}
          </span>
        </div>
        <div className="w-full bg-surface-container rounded-full h-2 overflow-hidden">
          <div
            className="bg-tertiary h-full rounded-full transition-all duration-700"
            style={{ width: `${Math.max(0, Math.min(100, paidPct))}%` }}
          />
        </div>
        <div className="flex items-center justify-between font-label-sm text-on-surface-variant">
          <span>Original {money(debt.originalAmount)}</span>
          <span>Due {debt.dueDate}</span>
        </div>
      </Card>

      {/* Why do I owe this */}
      <Card className="flex flex-col gap-space-md">
        <div className="flex items-center gap-space-xs">
          <Icon name="help" className="text-secondary text-[20px]" />
          <span className="font-headline-sm text-on-surface">Why do I owe this?</span>
        </div>
        <div className="grid grid-cols-2 gap-space-sm">
          <div className="rt-card-inner">
            <span className="font-label-sm text-on-surface-variant">Paid by</span>
            <p className="font-headline-sm text-on-surface">{explanation.paidByUsername}</p>
          </div>
          <div className="rt-card-inner">
            <span className="font-label-sm text-on-surface-variant">Store</span>
            <p className="font-headline-sm text-on-surface truncate">{explanation.storeName}</p>
          </div>
        </div>
        <div className="flex flex-col gap-space-2xs">
          {explanation.items.map((it, i) => (
            <div key={i} className="flex items-center justify-between py-space-2xs border-b border-white/[0.05] last:border-0">
              <span className="font-body-md text-on-surface">{it.itemName}</span>
              <span className="font-tabular-amount text-on-surface">{money(it.amount)}</span>
            </div>
          ))}
          <div className="flex items-center justify-between pt-space-xs">
            <span className="font-headline-sm text-on-surface">Total</span>
            <span className="font-headline-sm text-tertiary">{money(explanation.totalDebt)}</span>
          </div>
        </div>
        {explanation.receiptImageUrl && (
          <a
            href={receiptApi.imageUrl(explanation.receiptId)}
            target="_blank"
            rel="noreferrer"
            className="font-label-md text-primary flex items-center gap-space-2xs"
          >
            <Icon name="image" className="text-[18px]" /> View receipt image
          </a>
        )}
      </Card>

      {/* Submit payment (debtor only) */}
      {!iAmCreditor && !settled && (
        <Card className="flex flex-col gap-space-md">
          <span className="font-headline-sm text-on-surface">Submit a payment</span>
          <form className="flex flex-col gap-space-md" onSubmit={submitPayment}>
            <div>
              <label className="field-label">Amount</label>
              <input className="field-input" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder={money(debt.outstandingAmount)} required />
            </div>
            <div>
              <label className="field-label">Method</label>
              <div className="flex flex-wrap gap-space-2xs">
                {METHODS.map((m) => (
                  <button
                    type="button"
                    key={m}
                    onClick={() => setMethod(m)}
                    className={`pill px-space-sm py-1.5 border transition-colors ${
                      method === m
                        ? 'bg-primary-container text-on-primary border-transparent'
                        : 'bg-surface-container text-on-surface-variant border-white/[0.08]'
                    }`}
                  >
                    {m.replace('_', ' ')}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <label className="field-label">Notes (optional)</label>
              <input className="field-input" value={notes} onChange={(e) => setNotes(e.target.value)} />
            </div>
            <button className="btn-primary w-full py-3">
              <Icon name="payments" className="text-[18px]" /> Submit payment
            </button>
          </form>
        </Card>
      )}

      {/* Payments list */}
      <div className="flex flex-col gap-space-sm">
        <h2 className="font-headline-md text-on-surface">Payments</h2>
        {payments.length === 0 ? (
          <Card>
            <Empty text="No payments yet." icon="payments" />
          </Card>
        ) : (
          payments.map((p) => (
            <Card key={p.id} className="flex items-center justify-between gap-space-sm">
              <div className="flex items-center gap-space-sm min-w-0">
                <div className="w-10 h-10 rounded-full bg-surface-container-high flex items-center justify-center text-on-surface-variant shrink-0">
                  <Icon name="payments" className="text-[20px]" />
                </div>
                <div className="flex flex-col min-w-0">
                  <span className="font-headline-sm text-on-surface">{money(p.amount)}</span>
                  <span className="font-label-sm text-on-surface-variant">{p.method.replace('_', ' ')}</span>
                </div>
              </div>
              <div className="flex items-center gap-space-sm shrink-0">
                <span
                  className={`pill px-space-sm py-1 ${
                    p.status === 'APPROVED'
                      ? 'bg-tertiary/20 text-tertiary'
                      : p.status === 'REJECTED'
                      ? 'bg-error-container/40 text-error'
                      : 'bg-surface-container-high text-on-surface-variant'
                  }`}
                >
                  {p.status}
                </span>
                {iAmCreditor && p.status === 'PENDING' && (
                  <div className="flex gap-space-2xs">
                    <button
                      className="w-9 h-9 rounded-full bg-tertiary-container/30 flex items-center justify-center text-tertiary active:scale-95 transition-transform"
                      onClick={() => decide(p.id, true)}
                      aria-label="Approve"
                    >
                      <Icon name="check" className="text-[18px]" />
                    </button>
                    <button
                      className="w-9 h-9 rounded-full bg-error-container/40 flex items-center justify-center text-error active:scale-95 transition-transform"
                      onClick={() => decide(p.id, false)}
                      aria-label="Reject"
                    >
                      <Icon name="close" className="text-[18px]" />
                    </button>
                  </div>
                )}
              </div>
            </Card>
          ))
        )}
      </div>

      {/* History timeline */}
      <Card className="flex flex-col gap-space-md">
        <span className="font-headline-sm text-on-surface">Lifecycle</span>
        {history && history.events.length > 0 ? (
          <ol className="flex flex-col gap-0">
            {history.events.map((ev, i) => (
              <li key={i} className="flex gap-space-sm">
                <div className="flex flex-col items-center">
                  <div className="w-8 h-8 rounded-full bg-surface-container-high flex items-center justify-center text-secondary shrink-0">
                    <Icon name={EVENT_ICON[ev.type] ?? 'circle'} className="text-[16px]" />
                  </div>
                  {i < history.events.length - 1 && <div className="w-0.5 flex-1 bg-white/[0.08] my-1" />}
                </div>
                <div className="flex flex-col pb-space-md min-w-0">
                  <div className="flex items-center gap-space-xs flex-wrap">
                    <span className="font-headline-sm text-on-surface">{ev.type.replace(/_/g, ' ')}</span>
                    {ev.amount && <span className="font-tabular-amount text-tertiary">{money(ev.amount)}</span>}
                  </div>
                  <span className="font-body-sm text-on-surface-variant">{ev.detail}</span>
                  <span className="font-label-sm text-on-surface-variant mt-space-2xs">
                    {new Date(ev.at).toLocaleString()}
                    {ev.remainingBalance !== null && ev.remainingBalance !== undefined
                      ? ` · balance ${money(ev.remainingBalance)}`
                      : ''}
                  </span>
                </div>
              </li>
            ))}
          </ol>
        ) : (
          <Empty text="No history." icon="timeline" />
        )}
      </Card>
    </div>
  );
}
