import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { debtApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import type { Dashboard, DebtSummary } from '../api/types';
import { Card, Empty, ErrorBanner, money } from '../components/ui';

function DebtRow({ debt }: { debt: DebtSummary }) {
  return (
    <Link to={`/debts/${debt.debtId}`} className="debt-row">
      <span className="who">{debt.counterpartyUsername}</span>
      <span className={`pill ${debt.status === 'SETTLED' ? 'ok' : ''}`}>{debt.status}</span>
      <span className="amount">{money(debt.outstandingAmount)}</span>
    </Link>
  );
}

export default function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    debtApi
      .dashboard()
      .then((r) => setData(r.data))
      .catch((e) => setError(apiErrorMessage(e)));
  }, []);

  return (
    <div className="page">
      <h1>Dashboard</h1>
      <ErrorBanner message={error} />

      {data && (
        <>
          <div className="metrics">
            <div className="metric"><span>Owed to me</span><strong>{money(data.totalOwedToMe)}</strong></div>
            <div className="metric"><span>I owe</span><strong>{money(data.totalIOwe)}</strong></div>
            <div className="metric"><span>Active debts</span><strong>{data.activeDebts}</strong></div>
            <div className="metric"><span>Settled debts</span><strong>{data.settledDebts}</strong></div>
          </div>

          <div className="grid-2">
            <Card title="Money owed to me">
              {data.owedToMe.length === 0 ? (
                <Empty text="Nobody owes you yet." />
              ) : (
                <div className="debt-list">{data.owedToMe.map((d) => <DebtRow key={d.debtId} debt={d} />)}</div>
              )}
            </Card>

            <Card title="Money I owe">
              {data.iOwe.length === 0 ? (
                <Empty text="You don't owe anyone. Nice." />
              ) : (
                <div className="debt-list">{data.iOwe.map((d) => <DebtRow key={d.debtId} debt={d} />)}</div>
              )}
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
