import { api, API_BASE } from './client';
import type {
  AssignTarget,
  Assignment,
  Dashboard,
  DebtExplanation,
  DebtHistory,
  DebtSummary,
  MyProfile,
  Notification,
  Payment,
  PaymentMethod,
  PublicProfile,
  Receipt,
  ReceiptDraft,
  ReceiptItem,
  RegisterResponse,
  SearchResult,
  SplitType,
  TokenResponse,
  FriendRequest,
  UserSummary,
} from './types';

export const authApi = {
  register: (fullName: string, username: string, email: string, password: string) =>
    api.post<RegisterResponse>('/auth/register', { fullName, username, email, password }),
  login: (username: string, password: string) =>
    api.post<TokenResponse>('/auth/login', { username, password }),
  logout: (refreshToken: string) => api.post('/auth/logout', { refreshToken }),
};

export const profileApi = {
  me: () => api.get<MyProfile>('/me'),
  publicProfile: (username: string) => api.get<PublicProfile>(`/users/${username}`),
  search: (query: string) => api.get<SearchResult[]>('/users/search', { params: { query } }),
  uploadImage: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api.post<MyProfile>('/me/profile-image', form);
  },
};

export const friendApi = {
  send: (addresseeUsername: string) =>
    api.post<FriendRequest>('/friends/requests', { addresseeUsername }),
  incoming: () => api.get<FriendRequest[]>('/friends/requests'),
  accept: (id: number) => api.post(`/friends/requests/${id}/accept`),
  reject: (id: number) => api.post(`/friends/requests/${id}/reject`),
  list: () => api.get<UserSummary[]>('/friends'),
  remove: (userId: number) => api.delete(`/friends/${userId}`),
};

export const receiptApi = {
  scan: (image: File) => {
    const form = new FormData();
    form.append('image', image);
    return api.post<ReceiptDraft>('/receipts/scan', form);
  },
  create: (storeName: string, purchaseDate: string, notes: string, image: File | null) => {
    const form = new FormData();
    form.append('storeName', storeName);
    form.append('purchaseDate', purchaseDate);
    if (notes) form.append('notes', notes);
    if (image) form.append('image', image);
    return api.post<Receipt>('/receipts', form);
  },
  get: (id: number) => api.get<Receipt>(`/receipts/${id}`),
  imageUrl: (id: number) => `${API_BASE}/receipts/${id}/image`,
  items: (id: number) => api.get<ReceiptItem[]>(`/receipts/${id}/items`),
  addItem: (id: number, name: string, quantity: number, unitPrice: string) =>
    api.post<ReceiptItem>(`/receipts/${id}/items`, { name, quantity, unitPrice }),
  deleteItem: (id: number, itemId: number) => api.delete(`/receipts/${id}/items/${itemId}`),
  assign: (id: number, itemId: number, splitType: SplitType, targets: AssignTarget[]) =>
    api.post<Assignment[]>(`/receipts/${id}/items/${itemId}/assignments`, {
      splitType,
      targets,
    }),
  listAssignments: (id: number, itemId: number) =>
    api.get<Assignment[]>(`/receipts/${id}/items/${itemId}/assignments`),
  finalize: (id: number, dueDate?: string) =>
    api.post<DebtSummary[]>(
      `/receipts/${id}/finalize`,
      null,
      dueDate ? { params: { dueDate } } : undefined
    ),
};

export const debtApi = {
  dashboard: () => api.get<Dashboard>('/debts/dashboard'),
  get: (id: number) => api.get<DebtSummary>(`/debts/${id}`),
  explanation: (id: number) => api.get<DebtExplanation>(`/debts/${id}/explanation`),
  history: (id: number) => api.get<DebtHistory>(`/debts/${id}/history`),
};

export const paymentApi = {
  submit: (debtId: number, amount: string, method: PaymentMethod, notes: string) =>
    api.post<Payment>(`/debts/${debtId}/payments`, { amount, method, notes }),
  list: (debtId: number) => api.get<Payment[]>(`/debts/${debtId}/payments`),
  approve: (paymentId: number) => api.post<Payment>(`/payments/${paymentId}/approve`),
  reject: (paymentId: number) => api.post<Payment>(`/payments/${paymentId}/reject`),
};

export const notificationApi = {
  list: (unread?: boolean) =>
    api.get<Notification[]>('/notifications', {
      params: unread === undefined ? {} : { unread },
    }),
  markRead: (id: number) => api.post(`/notifications/${id}/read`),
};
