import { FormEvent, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { friendApi, receiptApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import type { Assignment, Receipt, ReceiptItem, SplitType, UserSummary } from '../api/types';
import { Card, Empty, ErrorBanner, money } from '../components/ui';

export default function ReceiptDetailPage() {
  const { id } = useParams();
  const receiptId = Number(id);
  const navigate = useNavigate();

  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [items, setItems] = useState<ReceiptItem[]>([]);
  const [assignments, setAssignments] = useState<Record<number, Assignment[]>>({});
  const [friends, setFriends] = useState<UserSummary[]>([]);
  const [error, setError] = useState<string | null>(null);

  // New-item form
  const [name, setName] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [unitPrice, setUnitPrice] = useState('');

  const load = async () => {
    try {
      const [r, it, fr] = await Promise.all([
        receiptApi.get(receiptId),
        receiptApi.items(receiptId),
        friendApi.list(),
      ]);
      setReceipt(r.data);
      setItems(it.data);
      setFriends(fr.data);
      const map: Record<number, Assignment[]> = {};
      await Promise.all(
        it.data.map(async (item) => {
          const a = await receiptApi.listAssignments(receiptId, item.id);
          map[item.id] = a.data;
        })
      );
      setAssignments(map);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [receiptId]);

  const addItem = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await receiptApi.addItem(receiptId, name, quantity, unitPrice);
      setName('');
      setQuantity(1);
      setUnitPrice('');
      await load();
    } catch (err) {
      setError(apiErrorMessage(err));
    }
  };

  const assign = async (itemId: number, splitType: SplitType, usernames: string[]) => {
    setError(null);
    if (usernames.length === 0) {
      setError('Pick at least one friend to assign.');
      return;
    }
    try {
      await receiptApi.assign(receiptId, itemId, splitType, usernames);
      await load();
    } catch (err) {
      setError(apiErrorMessage(err));
    }
  };

  const finalize = async () => {
    setError(null);
    try {
      await receiptApi.finalize(receiptId);
      await load();
    } catch (err) {
      setError(apiErrorMessage(err));
    }
  };

  if (!receipt) {
    return (
      <div className="page">
        <ErrorBanner message={error} />
        <p className="muted">Loading receipt…</p>
      </div>
    );
  }

  return (
    <div className="page">
      <h1>{receipt.storeName}</h1>
      <p className="muted">
        {receipt.purchaseDate} · {receipt.finalized ? 'Finalized' : 'Draft'}
      </p>
      <ErrorBanner message={error} />

      <div className="grid-2">
        <Card title="Receipt image">
          {receipt.imageContentType === 'application/pdf' ? (
            <a href={receiptApi.imageUrl(receipt.id)} target="_blank" rel="noreferrer">Open PDF</a>
          ) : (
            <img className="receipt-img" src={receiptApi.imageUrl(receipt.id)} alt="Receipt" />
          )}
          {receipt.notes && <p className="muted">{receipt.notes}</p>}
        </Card>

        <Card title="Items">
          {items.length === 0 ? (
            <Empty text="No items yet. Add some below." />
          ) : (
            <div className="item-list">
              {items.map((item) => (
                <ItemRow
                  key={item.id}
                  item={item}
                  assignments={assignments[item.id] ?? []}
                  friends={friends}
                  finalized={receipt.finalized}
                  onAssign={assign}
                  onDelete={async () => {
                    try {
                      await receiptApi.deleteItem(receiptId, item.id);
                      await load();
                    } catch (e) {
                      setError(apiErrorMessage(e));
                    }
                  }}
                />
              ))}
            </div>
          )}

          {!receipt.finalized && (
            <form className="inline-form mt" onSubmit={addItem}>
              <input placeholder="Item" value={name} onChange={(e) => setName(e.target.value)} required />
              <input
                type="number"
                min={1}
                value={quantity}
                onChange={(e) => setQuantity(Number(e.target.value))}
                style={{ width: 70 }}
              />
              <input
                placeholder="Unit price"
                value={unitPrice}
                onChange={(e) => setUnitPrice(e.target.value)}
                required
                style={{ width: 110 }}
              />
              <button className="btn-primary">Add</button>
            </form>
          )}
        </Card>
      </div>

      {!receipt.finalized && items.length > 0 && (
        <Card>
          <div className="finalize-bar">
            <span className="muted">Finalizing generates debts from the assignments and locks the receipt.</span>
            <button className="btn-primary" onClick={finalize}>Finalize &amp; generate debts</button>
          </div>
        </Card>
      )}

      {receipt.finalized && (
        <Card>
          <p>This receipt is finalized. See the <a onClick={() => navigate('/')} className="link">dashboard</a> for the resulting debts.</p>
        </Card>
      )}
    </div>
  );
}

function ItemRow({
  item,
  assignments,
  friends,
  finalized,
  onAssign,
  onDelete,
}: {
  item: ReceiptItem;
  assignments: Assignment[];
  friends: UserSummary[];
  finalized: boolean;
  onAssign: (itemId: number, splitType: SplitType, usernames: string[]) => void;
  onDelete: () => void;
}) {
  const [split, setSplit] = useState<SplitType>('INDIVIDUAL');
  const [picked, setPicked] = useState<string[]>([]);

  const toggle = (username: string) => {
    setPicked((prev) =>
      prev.includes(username) ? prev.filter((u) => u !== username) : [...prev, username]
    );
  };

  return (
    <div className="item-card">
      <div className="item-head">
        <strong>{item.name}</strong>
        <span>
          {item.quantity} × {money(item.unitPrice)} = {money(item.lineTotal)}
        </span>
        {!finalized && <button className="btn-ghost tiny" onClick={onDelete}>✕</button>}
      </div>

      {assignments.length > 0 && (
        <div className="assign-tags">
          {assignments.map((a) => (
            <span key={a.id} className="tag">
              {a.assigneeUsername}: {money(a.shareAmount)}
            </span>
          ))}
        </div>
      )}

      {!finalized && (
        <div className="assign-form">
          <select value={split} onChange={(e) => setSplit(e.target.value as SplitType)}>
            <option value="INDIVIDUAL">Individual</option>
            <option value="EQUAL">Equal split</option>
          </select>
          <div className="friend-picker">
            {friends.length === 0 && <span className="muted">Add friends first.</span>}
            {friends.map((f) => (
              <label key={f.id} className="chk">
                <input
                  type="checkbox"
                  checked={picked.includes(f.username)}
                  onChange={() => toggle(f.username)}
                />
                {f.username}
              </label>
            ))}
          </div>
          <button
            className="btn-ghost"
            onClick={() => {
              onAssign(item.id, split, picked);
              setPicked([]);
            }}
          >
            Assign
          </button>
        </div>
      )}
    </div>
  );
}
