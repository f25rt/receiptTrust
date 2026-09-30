import { FormEvent, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
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
import { Card, Empty, ErrorBanner, money } from '../components/ui';

const METHODS: PaymentMethod[] = ['CASH', 'BANK_TRANSFER', 'GCASH', 'MAYA', 'OTHER'];

export default function DebtDetailPage() {
  const { id } = useParams();
  const debtId = Number(id);
  const { profile, refreshProfile } = useAuth();

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
      <div className="page">
        <ErrorBanner message={error} />
        <p className="muted">Loading debt…</p>
      </div>
    );
  }

  return (
    <div className="page">
      <h1>Debt #{debt.debtId}</h1>
      <p className="muted">
        {debt.status} · outstanding {money(debt.outstandingAmount)} of {money(debt.originalAmount)} · due {debt.dueDate}
      </p>
      <ErrorBanner message={error} />

      <div className="grid-2">
        <Card title="Why do I owe this?">
          <dl className="explain">
            <dt>Paid by</dt><dd>{explanation.paidByUsername}</dd>
            <dt>Store</dt><dd>{explanation.storeName}</dd>
            <dt>Purchase date</dt><dd>{explanation.purchaseDate}</dd>
          </dl>
          <table className="items-table">
            <thead><tr><th>Item</th><th>Amount</th></tr></thead>
            <tbody>
              {explanation.items.map((it, i) => (
                <tr key={i}><td>{it.itemName}</td><td>{money(it.amount)}</td></tr>
              ))}
            </tbody>
            <tfoot><tr><td>Total</td><td>{money(explanation.totalDebt)}</td></tr></tfoot>
          </table>
          {explanation.receiptImageUrl && (
            <a href={receiptApi.imageUrl(explanation.receiptId)} target="_blank" rel="noreferrer" className="link">
              View receipt image
            </a>
          )}
        </Card>

        <div>
          {!iAmCreditor && debt.status === 'ACTIVE' && (
            <Card title="Submit a payment">
              <form className="stack-form" onSubmit={submitPayment}>
                <label>Amount
                  <input value={amount} onChange={(e) => setAmount(e.target.value)} required placeholder="e.g. 15.00" />
                </label>
                <label>Method
                  <select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
                    {METHODS.map((m) => <option key={m} value={m}>{m}</option>)}
                  </select>
                </label>
                <label>Notes (optional)
                  <input value={notes} onChange={(e) => setNotes(e.target.value)} />
                </label>
                <button className="btn-primary">Submit payment</button>
              </form>
            </Card>
          )}

          <Card title="Payments">
            {payments.length === 0 ? (
              <Empty text="No payments yet." />
            ) : (
              <ul className="list">
                {payments.map((p) => (
                  <li key={p.id}>
                    <span>
                      {money(p.amount)} · {p.method} ·{' '}
                      <span className={`pill ${p.status === 'APPROVED' ? 'ok' : p.status === 'REJECTED' ? 'bad' : ''}`}>
                        {p.status}
                      </span>
                    </span>
                    {iAmCreditor && p.status === 'PENDING' && (
                      <span className="row-actions">
                        <button className="btn-primary" onClick={() => decide(p.id, true)}>Approve</button>
                        <button className="btn-ghost" onClick={() => decide(p.id, false)}>Reject</button>
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>

      <Card title="History">
        {history && history.events.length > 0 ? (
          <ol className="timeline">
            {history.events.map((ev, i) => (
              <li key={i}>
                <span className="ts">{new Date(ev.at).toLocaleString()}</span>
                <span className="ev-type">{ev.type.replace(/_/g, ' ')}</span>
                <span className="muted">{ev.detail}</span>
                {ev.amount && <span className="amount">{money(ev.amount)}</span>}
                {ev.remainingBalance !== null && ev.remainingBalance !== undefined && (
                  <span className="muted">bal {money(ev.remainingBalance)}</span>
                )}
              </li>
            ))}
          </ol>
        ) : (
          <Empty text="No history." />
        )}
      </Card>
    </div>
  );
}
