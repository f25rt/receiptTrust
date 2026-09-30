import { FormEvent, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { friendApi, receiptApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import type { AssignTarget, Assignment, Receipt, ReceiptItem, SplitType, UserSummary } from '../api/types';
import { Card, Empty, ErrorBanner, Icon } from '../components/ui';
import { money } from '../lib/format';

export default function ReceiptDetailPage() {
  const { id } = useParams();
  const receiptId = Number(id);
  const navigate = useNavigate();

  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [items, setItems] = useState<ReceiptItem[]>([]);
  const [assignments, setAssignments] = useState<Record<number, Assignment[]>>({});
  const [friends, setFriends] = useState<UserSummary[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [unitPrice, setUnitPrice] = useState('');
  const [dueDate, setDueDate] = useState('');

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

  const assign = async (itemId: number, splitType: SplitType, targets: AssignTarget[]) => {
    setError(null);
    if (targets.length === 0) {
      setError('Pick at least one friend or add a label to assign.');
      return;
    }
    try {
      await receiptApi.assign(receiptId, itemId, splitType, targets);
      await load();
    } catch (err) {
      setError(apiErrorMessage(err));
    }
  };

  const finalize = async () => {
    setError(null);
    try {
      await receiptApi.finalize(receiptId, dueDate || undefined);
      await load();
    } catch (err) {
      setError(apiErrorMessage(err));
    }
  };

  const total = items.reduce((sum, i) => sum + Number(i.lineTotal), 0);

  if (!receipt) {
    return (
      <div className="flex flex-col w-full px-margin pt-space-md gap-space-md">
        <ErrorBanner message={error} />
        <Empty text="Loading receipt…" icon="hourglass_empty" />
      </div>
    );
  }

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
        <div className="flex flex-col min-w-0">
          <h1 className="font-headline-lg lg:text-headline-lg text-on-surface truncate">{receipt.storeName}</h1>
          <span className="font-label-sm text-on-surface-variant">
            {receipt.purchaseDate} · {receipt.finalized ? 'Finalized' : 'Draft'}
          </span>
        </div>
      </div>

      <ErrorBanner message={error} />

      <div className="flex flex-col gap-space-lg lg:grid lg:grid-cols-5 lg:gap-space-lg lg:items-start">
      {/* Left: receipt + total */}
      <Card className="flex flex-col gap-space-md lg:col-span-2 lg:sticky lg:top-20">
        <div className="flex items-center gap-space-sm">
          <div className="w-11 h-11 rounded-lg bg-surface-container-high flex items-center justify-center text-secondary shrink-0">
            <Icon name="receipt_long" className="text-[24px]" />
          </div>
          <div className="flex flex-col min-w-0 flex-1">
            <div className="flex items-center gap-space-2xs">
              <span className="font-headline-sm text-on-surface truncate">{receipt.storeName}</span>
              <Icon name="verified" className="text-secondary text-[16px]" />
            </div>
            <span className="font-label-sm text-on-surface-variant">
              {receipt.finalized ? 'Locked' : 'Editable draft'}
            </span>
          </div>
          <span className="font-label-sm text-on-surface-variant bg-surface-container-high px-space-sm py-1 rounded-full">
            {receipt.purchaseDate}
          </span>
        </div>
        <div className="flex items-center justify-between bg-surface-container-high/60 rounded-lg px-space-md py-space-sm">
          <span className="font-label-md text-on-surface-variant uppercase tracking-wider">Total</span>
          <span className="font-display-mobile text-on-surface">{money(total)}</span>
        </div>
        {!receipt.hasImage ? (
          <div className="flex flex-col items-center gap-space-2xs py-space-md text-center bg-surface-container-lowest rounded-lg border border-dashed border-white/[0.12]">
            <Icon name="image_not_supported" className="text-on-surface-variant text-[28px]" />
            <span className="font-body-sm text-on-surface-variant">No image — manually tracked receipt</span>
          </div>
        ) : receipt.imageContentType === 'application/pdf' ? (
          <a href={receiptApi.imageUrl(receipt.id)} target="_blank" rel="noreferrer" className="font-label-md text-primary flex items-center gap-space-2xs">
            <Icon name="picture_as_pdf" className="text-[18px]" /> Open PDF
          </a>
        ) : (
          <img
            className="w-full rounded-lg border border-white/[0.06] max-h-64 lg:max-h-[28rem] object-contain bg-surface-container-lowest"
            src={receiptApi.imageUrl(receipt.id)}
            alt="Receipt"
          />
        )}
        {receipt.notes && <p className="font-body-sm text-on-surface-variant">{receipt.notes}</p>}
      </Card>

      {/* Right: split allocation engine */}
      <div className="flex flex-col gap-space-lg lg:col-span-3">
      {/* Items */}
      <div className="flex flex-col gap-space-sm">
        <div className="flex items-center justify-between">
          <h2 className="font-headline-md text-on-surface">Split Allocation Engine</h2>
          <span className="font-label-sm text-on-surface-variant">{items.length} item(s)</span>
        </div>

        {items.length === 0 ? (
          <Card>
            <Empty text="No items yet. Add some below." icon="list_alt" />
          </Card>
        ) : (
          items.map((item) => (
            <ItemCard
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
          ))
        )}

        {!receipt.finalized && (
          <Card>
            <form className="flex items-end gap-space-sm" onSubmit={addItem}>
              <div className="flex-1 min-w-0">
                <label className="field-label">Item</label>
                <input className="field-input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Milk" required />
              </div>
              <div className="w-16">
                <label className="field-label">Qty</label>
                <input className="field-input px-2" type="number" min={1} value={quantity} onChange={(e) => setQuantity(Number(e.target.value))} />
              </div>
              <div className="w-24">
                <label className="field-label">Price</label>
                <input className="field-input px-2" value={unitPrice} onChange={(e) => setUnitPrice(e.target.value)} placeholder="10.00" required />
              </div>
              <button className="btn-primary h-12 px-space-md" aria-label="Add item">
                <Icon name="add" className="text-[20px]" />
              </button>
            </form>
          </Card>
        )}
      </div>

      {/* Finalize / finalized state */}
      {!receipt.finalized && items.length > 0 && (
        <Card className="flex flex-col gap-space-md">
          <div className="flex items-start gap-space-xs">
            <Icon name="lock" className="text-secondary text-[18px] shrink-0 mt-0.5" />
            <p className="font-body-sm text-on-surface-variant">
              Finalizing generates debts from the assignments and locks this receipt. Set an optional
              due date; paying on time raises trust, missing it lowers it.
            </p>
          </div>
          <div>
            <label className="field-label" htmlFor="due">Payment due date (optional)</label>
            <input
              id="due"
              type="date"
              className="field-input"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
            />
          </div>
          <button className="btn-primary w-full py-3" onClick={finalize}>
            <Icon name="send" className="text-[18px]" />
            Finalize &amp; generate debts
          </button>
        </Card>
      )}

      {receipt.finalized && (
        <Card className="flex items-center gap-space-sm">
          <Icon name="check_circle" className="text-tertiary text-[24px]" />
          <div className="flex flex-col flex-1">
            <span className="font-headline-sm text-on-surface">Receipt finalized</span>
            <span className="font-body-sm text-on-surface-variant">Debts have been generated.</span>
          </div>
          <button className="btn-glass" onClick={() => navigate('/')}>
            Dashboard
          </button>
        </Card>
      )}
      </div>
      </div>
    </div>
  );
}

function ItemCard({
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
  onAssign: (itemId: number, splitType: SplitType, targets: AssignTarget[]) => void;
  onDelete: () => void;
}) {
  const [split, setSplit] = useState<SplitType>('INDIVIDUAL');
  const [pickedUsers, setPickedUsers] = useState<string[]>([]);
  const [labels, setLabels] = useState<string[]>([]);
  const [labelDraft, setLabelDraft] = useState('');
  const [open, setOpen] = useState(false);

  const reset = () => {
    setPickedUsers([]);
    setLabels([]);
    setLabelDraft('');
  };

  const toggleUser = (username: string) => {
    if (split === 'INDIVIDUAL') {
      setPickedUsers([username]);
      setLabels([]);
    } else {
      setPickedUsers((prev) =>
        prev.includes(username) ? prev.filter((u) => u !== username) : [...prev, username]
      );
    }
  };

  const addLabel = () => {
    const l = labelDraft.trim();
    if (!l) return;
    if (split === 'INDIVIDUAL') {
      setLabels([l]);
      setPickedUsers([]);
    } else if (!labels.includes(l)) {
      setLabels((prev) => [...prev, l]);
    }
    setLabelDraft('');
  };

  const targets: AssignTarget[] = [
    ...pickedUsers.map((username) => ({ username })),
    ...labels.map((label) => ({ label })),
  ];

  return (
    <Card className="flex flex-col gap-space-sm">
      <div className="flex items-center justify-between gap-space-sm">
        <div className="flex items-center gap-space-sm min-w-0">
          <span className="font-label-md text-on-surface-variant bg-surface-container-high w-8 h-8 rounded-lg flex items-center justify-center shrink-0">
            {item.quantity}×
          </span>
          <div className="flex flex-col min-w-0">
            <span className="font-headline-sm text-on-surface truncate">{item.name}</span>
            <span className="font-label-sm text-on-surface-variant">{money(item.unitPrice)} each</span>
          </div>
        </div>
        <div className="flex items-center gap-space-sm shrink-0">
          <span className="font-tabular-amount text-on-surface">{money(item.lineTotal)}</span>
          {!finalized && (
            <button
              onClick={onDelete}
              className="w-7 h-7 rounded-full bg-surface-container-high flex items-center justify-center text-on-surface-variant active:scale-95 transition-transform"
              aria-label="Delete item"
            >
              <Icon name="close" className="text-[16px]" />
            </button>
          )}
        </div>
      </div>

      {assignments.length > 0 && (
        <div className="flex flex-wrap gap-space-2xs">
          {assignments.map((a) => (
            <span
              key={a.id}
              className={`pill ${a.label ? 'bg-secondary/15 text-secondary' : 'bg-surface-container-high text-on-surface-variant'}`}
            >
              <Icon name={a.label ? 'label' : 'person'} className="text-[12px]" />
              {a.assigneeName}: {money(a.shareAmount)}
            </span>
          ))}
        </div>
      )}

      {!finalized && (
        <>
          {!open ? (
            <button
              onClick={() => setOpen(true)}
              className="font-label-md text-primary flex items-center gap-space-2xs self-start"
            >
              <Icon name="group_add" className="text-[16px]" /> Assign this item
            </button>
          ) : (
            <div className="flex flex-col gap-space-sm bg-surface-container-high/40 rounded-lg p-space-sm">
              <div className="flex items-center p-1 bg-surface-container-high rounded-full self-start">
                {(['INDIVIDUAL', 'EQUAL'] as SplitType[]).map((s) => (
                  <button
                    key={s}
                    onClick={() => {
                      setSplit(s);
                      reset();
                    }}
                    className={`px-space-md py-1 rounded-full font-label-md transition-all ${
                      split === s ? 'bg-primary-container text-on-primary' : 'text-on-surface-variant'
                    }`}
                  >
                    {s === 'INDIVIDUAL' ? 'Individual' : 'Equal split'}
                  </button>
                ))}
              </div>

              {/* Registered friends */}
              {friends.length > 0 && (
                <div className="flex flex-col gap-space-2xs">
                  <span className="field-label mb-0">Friends</span>
                  <div className="flex flex-wrap gap-space-2xs">
                    {friends.map((f) => {
                      const on = pickedUsers.includes(f.username);
                      return (
                        <button
                          key={f.id}
                          onClick={() => toggleUser(f.username)}
                          className={`pill px-space-sm py-1.5 border transition-colors ${
                            on
                              ? 'bg-primary-container text-on-primary border-transparent'
                              : 'bg-surface-container text-on-surface-variant border-white/[0.08]'
                          }`}
                        >
                          {on && <Icon name="check" className="text-[14px]" />}
                          {f.username}
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Non-registered labels */}
              <div className="flex flex-col gap-space-2xs">
                <span className="field-label mb-0">Someone not on the app</span>
                {labels.length > 0 && (
                  <div className="flex flex-wrap gap-space-2xs">
                    {labels.map((l) => (
                      <span key={l} className="pill bg-secondary/15 text-secondary px-space-sm py-1.5">
                        <Icon name="label" className="text-[12px]" />
                        {l}
                        <button onClick={() => setLabels((prev) => prev.filter((x) => x !== l))} aria-label="Remove label">
                          <Icon name="close" className="text-[12px]" />
                        </button>
                      </span>
                    ))}
                  </div>
                )}
                <div className="flex gap-space-2xs">
                  <input
                    className="field-input flex-1"
                    value={labelDraft}
                    onChange={(e) => setLabelDraft(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        addLabel();
                      }
                    }}
                    placeholder="e.g. Roommate Alex"
                  />
                  <button className="btn-glass" onClick={addLabel} type="button">
                    <Icon name="add" className="text-[16px]" /> Add
                  </button>
                </div>
              </div>

              <div className="flex gap-space-sm">
                <button className="btn-glass flex-1" onClick={() => { reset(); setOpen(false); }}>
                  Cancel
                </button>
                <button
                  className="btn-primary flex-1"
                  disabled={targets.length === 0}
                  onClick={() => {
                    onAssign(item.id, split, targets);
                    reset();
                    setOpen(false);
                  }}
                >
                  Assign
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </Card>
  );
}
