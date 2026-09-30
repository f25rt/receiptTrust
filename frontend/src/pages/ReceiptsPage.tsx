import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { receiptApi } from '../api/services';
import { apiErrorMessage } from '../api/client';
import { Card, ErrorBanner } from '../components/ui';

export default function ReceiptsPage() {
  const navigate = useNavigate();
  const [storeName, setStoreName] = useState('');
  const [purchaseDate, setPurchaseDate] = useState(new Date().toISOString().slice(0, 10));
  const [notes, setNotes] = useState('');
  const [image, setImage] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!image) {
      setError('Please choose a receipt image (JPG, PNG, or PDF).');
      return;
    }
    setBusy(true);
    try {
      const r = await receiptApi.create(storeName, purchaseDate, notes, image);
      navigate(`/receipts/${r.data.id}`);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <h1>New receipt</h1>
      <p className="muted">Upload a receipt, then add items and assign them to friends.</p>
      <ErrorBanner message={error} />

      <Card>
        <form className="stack-form" onSubmit={submit}>
          <label>Store name
            <input value={storeName} onChange={(e) => setStoreName(e.target.value)} required />
          </label>
          <label>Purchase date
            <input type="date" value={purchaseDate} onChange={(e) => setPurchaseDate(e.target.value)} required />
          </label>
          <label>Notes (optional)
            <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} />
          </label>
          <label>Receipt image (JPG, PNG, PDF)
            <input
              type="file"
              accept="image/jpeg,image/png,application/pdf"
              onChange={(e) => setImage(e.target.files?.[0] ?? null)}
              required
            />
          </label>
          <button className="btn-primary" disabled={busy}>{busy ? 'Uploading…' : 'Create receipt'}</button>
        </form>
      </Card>
    </div>
  );
}
