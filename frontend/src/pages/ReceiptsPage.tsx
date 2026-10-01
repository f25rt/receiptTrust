import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { receiptApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import type { ParsedItem } from '../api/types';
import { Card, ErrorBanner, Icon } from '../components/ui';
import { money } from '../lib/format';

interface DraftItem {
  name: string;
  quantity: number;
  unitPrice: string;
}

export default function ReceiptsPage() {
  const navigate = useNavigate();
  const [storeName, setStoreName] = useState('');
  const [purchaseDate, setPurchaseDate] = useState(new Date().toISOString().slice(0, 10));
  const [notes, setNotes] = useState('');
  const [image, setImage] = useState<File | null>(null);
  const [items, setItems] = useState<DraftItem[]>([]);
  const [serviceCharge, setServiceCharge] = useState<string | null>(null);
  const [scanned, setScanned] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onFile = async (file: File | null) => {
    setImage(file);
    setScanned(false);
    setItems([]);
    setServiceCharge(null);
    if (!file || !file.type.startsWith('image/')) return;
    setScanning(true);
    setError(null);
    try {
      const { data } = await receiptApi.scan(file);
      if (data.storeName) setStoreName(data.storeName);
      setItems(
        (data.items ?? []).map((p: ParsedItem) => ({
          name: p.name,
          quantity: p.quantity,
          unitPrice: p.unitPrice,
        }))
      );
      setServiceCharge(data.serviceCharge);
      setScanned(true);
    } catch (err) {
      // OCR failing is non-fatal; user can still enter details manually.
      setError('Could not auto-read the receipt. You can enter details manually.');
    } finally {
      setScanning(false);
    }
  };

  const updateItem = (i: number, patch: Partial<DraftItem>) => {
    setItems((prev) => prev.map((it, idx) => (idx === i ? { ...it, ...patch } : it)));
  };
  const removeItem = (i: number) => setItems((prev) => prev.filter((_, idx) => idx !== i));
  const addItem = () => setItems((prev) => [...prev, { name: '', quantity: 1, unitPrice: '' }]);

  const parsedTotal = items.reduce(
    (sum, it) => sum + (Number(it.unitPrice) || 0) * (it.quantity || 0),
    0
  );

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    // Image is optional — a receipt can be created with fully manual details.
    setBusy(true);
    try {
      const r = await receiptApi.create(storeName, purchaseDate, notes, image);
      // Seed the parsed/edited items onto the new receipt.
      for (const it of items) {
        if (it.name.trim() && Number(it.unitPrice) > 0) {
          await receiptApi.addItem(r.data.id, it.name.trim(), it.quantity || 1, it.unitPrice);
        }
      }
      navigate(`/receipts/${r.data.id}`);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-col w-full px-margin lg:px-0 lg:max-w-2xl lg:mx-auto pb-space-xl gap-space-lg">
      <div className="flex flex-col pt-space-xs">
        <span className="font-label-md text-secondary bg-secondary/15 px-space-sm py-0.5 rounded-full self-start">
          Scan &amp; Split
        </span>
        <h1 className="font-display-mobile lg:text-display text-on-surface mt-space-2xs">New Receipt</h1>
        <p className="font-body-md text-on-surface-variant mt-space-2xs">
          Optionally scan a photo to auto-read the store, items and total — or skip it and enter
          everything manually. Then assign items to friends.
        </p>
      </div>

      <ErrorBanner message={error} />

      {/* Upload / scan (optional) */}
      <Card className="flex flex-col items-center gap-space-md text-center">
        <label className="w-full flex flex-col items-center gap-space-sm cursor-pointer">
          <div className="w-20 h-20 rounded-full bg-primary-container/20 flex items-center justify-center text-primary">
            <Icon name={scanning ? 'sync' : 'photo_camera'} className={`text-[36px] ${scanning ? 'animate-spin' : ''}`} />
          </div>
          <span className="font-headline-sm text-on-surface">
            {scanning ? 'Reading receipt…' : image ? image.name : 'Scan a receipt (optional)'}
          </span>
          <span className="font-body-sm text-on-surface-variant">
            Upload to auto-read merchant, items &amp; total (JPG or PNG). No image? Just enter
            details below.
          </span>
          <span className="btn-glass mt-space-2xs">
            <Icon name="upload_file" className="text-[18px]" />
            {image ? 'Change file' : 'Upload photo / PDF'}
          </span>
          <input
            type="file"
            accept="image/jpeg,image/png,application/pdf"
            className="hidden"
            onChange={(e) => onFile(e.target.files?.[0] ?? null)}
          />
        </label>
        {scanned && (
          <span className="font-label-sm text-tertiary flex items-center gap-1">
            <Icon name="verified" className="text-[14px]" />
            {items.length > 0 ? `Auto-read ${items.length} item(s)` : 'Scanned — no items detected, add them below'}
          </span>
        )}
      </Card>

      {/* Details */}
      <Card className="flex flex-col gap-space-md">
        <form className="flex flex-col gap-space-md" onSubmit={submit}>
          <div>
            <label className="field-label" htmlFor="store">Store name</label>
            <input
              id="store"
              className="field-input"
              value={storeName}
              onChange={(e) => setStoreName(e.target.value)}
              placeholder="e.g. Blue Bottle Coffee"
              required
            />
          </div>
          <div>
            <label className="field-label" htmlFor="date">Purchase date</label>
            <input
              id="date"
              type="date"
              className="field-input"
              value={purchaseDate}
              onChange={(e) => setPurchaseDate(e.target.value)}
              required
            />
          </div>

          {/* Parsed items (editable) */}
          <div className="flex flex-col gap-space-sm">
            <div className="flex items-center justify-between">
              <span className="field-label mb-0">Items {items.length > 0 && `(${items.length})`}</span>
              <button type="button" className="font-label-md text-primary flex items-center gap-space-2xs" onClick={addItem}>
                <Icon name="add" className="text-[16px]" /> Add item
              </button>
            </div>
            {items.length === 0 && (
              <span className="font-body-sm text-on-surface-variant">
                No items yet. Scan a receipt or add them manually.
              </span>
            )}
            {items.map((it, i) => (
              <div key={i} className="flex items-end gap-space-2xs">
                <div className="flex-1 min-w-0">
                  <input
                    className="field-input"
                    value={it.name}
                    onChange={(e) => updateItem(i, { name: e.target.value })}
                    placeholder="Item name"
                  />
                </div>
                <input
                  className="field-input w-14 px-2"
                  type="number"
                  min={1}
                  value={it.quantity}
                  onChange={(e) => updateItem(i, { quantity: Number(e.target.value) })}
                />
                <input
                  className="field-input w-20 px-2"
                  value={it.unitPrice}
                  onChange={(e) => updateItem(i, { unitPrice: e.target.value })}
                  placeholder="0.00"
                />
                <button
                  type="button"
                  className="w-11 h-11 rounded-full bg-surface-container-high flex items-center justify-center text-on-surface-variant shrink-0"
                  onClick={() => removeItem(i)}
                  aria-label="Remove"
                >
                  <Icon name="close" className="text-[16px]" />
                </button>
              </div>
            ))}
            {(items.length > 0 || serviceCharge) && (
              <div className="flex items-center justify-between bg-surface-container-high/60 rounded-lg px-space-md py-space-sm">
                <div className="flex flex-col">
                  {serviceCharge && (
                    <span className="font-label-sm text-on-surface-variant">
                      Service charge {money(serviceCharge)}
                    </span>
                  )}
                  <span className="font-label-md text-on-surface-variant uppercase tracking-wider">Items total</span>
                </div>
                <span className="font-headline-md text-on-surface">{money(parsedTotal)}</span>
              </div>
            )}
          </div>

          <div>
            <label className="field-label" htmlFor="notes">Notes (optional)</label>
            <textarea id="notes" className="field-input" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </div>

          <button className="btn-primary w-full py-3" disabled={busy || scanning}>
            <Icon name="arrow_forward" className="text-[18px]" />
            {busy ? 'Creating…' : 'Create receipt & assign'}
          </button>
        </form>
      </Card>

      <div className="flex items-center gap-space-xs justify-center text-on-surface-variant">
        <Icon name="lock" className="text-[16px]" />
        <span className="font-label-sm">The image is read to auto-fill items — your debt is backed by the itemized list</span>
      </div>
    </div>
  );
}
