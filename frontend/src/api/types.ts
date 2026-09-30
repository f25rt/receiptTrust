export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

export interface RegisterResponse {
  id: number;
  username: string;
  email: string;
}

export type ReputationLevel = 'POOR' | 'FAIR' | 'GOOD' | 'VERY_GOOD' | 'EXCELLENT';

export type AuthProvider = 'LOCAL' | 'GOOGLE' | 'FACEBOOK';

export interface MyProfile {
  id: number;
  fullName: string;
  username: string;
  email: string;
  profileImagePath: string | null;
  trustScore: number;
  reputationLevel: ReputationLevel;
  debtsSettled: number;
  currentDebts: number;
  averageRepaymentDays: number | null;
  provider: AuthProvider;
  hasPassword: boolean;
}

export interface PublicProfile {
  fullName: string;
  username: string;
  trustScore: number;
  reputationLevel: ReputationLevel;
  debtsSettled: number;
  averageRepaymentDays: number | null;
}

export interface SearchResult {
  id: number;
  fullName: string;
  username: string;
}

export type FriendshipStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

export interface UserSummary {
  id: number;
  fullName: string;
  username: string;
  trustScore: number;
}

export interface FriendRequest {
  requestId: number;
  requester: UserSummary;
  status: FriendshipStatus;
}

export interface Receipt {
  id: number;
  ownerId: number;
  storeName: string;
  purchaseDate: string;
  notes: string | null;
  imageContentType: string | null;
  hasImage: boolean;
  finalized: boolean;
}

export interface ReceiptItem {
  id: number;
  name: string;
  quantity: number;
  unitPrice: string;
  lineTotal: string;
}

export type SplitType = 'INDIVIDUAL' | 'EQUAL';

/** One assignment target: a registered user (username) OR a free-text label. */
export interface AssignTarget {
  username?: string;
  label?: string;
}

export interface Assignment {
  id: number;
  receiptItemId: number;
  assigneeName: string;
  label: boolean;
  splitType: SplitType;
  shareAmount: string;
}

export type DebtStatus = 'ACTIVE' | 'SETTLED';

export interface DebtSummary {
  debtId: number;
  counterpartyUsername: string;
  counterpartyIsLabel: boolean;
  outstandingAmount: string;
  originalAmount: string;
  status: DebtStatus;
  dueDate: string;
}

/** OCR-parsed draft returned by POST /receipts/scan. */
export interface ParsedItem {
  name: string;
  quantity: number;
  unitPrice: string;
}

export interface ReceiptDraft {
  storeName: string | null;
  items: ParsedItem[];
  serviceCharge: string | null;
  total: string | null;
  rawText: string;
}

export interface Dashboard {
  owedToMe: DebtSummary[];
  iOwe: DebtSummary[];
  totalOwedToMe: string;
  totalIOwe: string;
  activeDebts: number;
  settledDebts: number;
}

export interface ExplanationItem {
  itemName: string;
  amount: string;
}

export interface DebtExplanation {
  debtId: number;
  paidByUsername: string;
  storeName: string;
  purchaseDate: string;
  items: ExplanationItem[];
  receiptId: number;
  receiptImageUrl: string;
  totalDebt: string;
}

export interface TimelineEvent {
  at: string;
  type: string;
  detail: string;
  amount: string | null;
  remainingBalance: string | null;
}

export interface DebtHistory {
  debtId: number;
  events: TimelineEvent[];
}

export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'GCASH' | 'MAYA' | 'OTHER';
export type PaymentStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface Payment {
  id: number;
  debtId: number;
  submitterUsername: string;
  amount: string;
  method: PaymentMethod;
  notes: string | null;
  status: PaymentStatus;
  balanceAfter: string | null;
  createdAt: string;
  decidedAt: string | null;
}

export type NotificationType =
  | 'FRIEND_REQUEST_RECEIVED'
  | 'FRIEND_REQUEST_ACCEPTED'
  | 'DEBT_CREATED'
  | 'PAYMENT_SUBMITTED'
  | 'PAYMENT_APPROVED'
  | 'PAYMENT_REJECTED'
  | 'DEBT_SETTLED';

export interface Notification {
  id: number;
  type: NotificationType;
  message: string;
  read: boolean;
  createdAt: string;
}
