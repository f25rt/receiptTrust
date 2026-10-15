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

export type Currency = 'USD' | 'PHP';

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
  currency: Currency;
  mobile: string | null;
  gender: string | null;
  country: string | null;
  city: string | null;
  role: 'USER' | 'ADMIN';
  flagged: boolean;
  taxEnabled: boolean;
}

export interface AdminMetricPoint {
  label: string;
  value: number;
}

export interface AdminUser {
  id: number;
  fullName: string;
  username: string;
  email: string;
  trustScore: number;
  role: 'USER' | 'ADMIN';
  locked: boolean;
  flagged: boolean;
  lastActiveAt: string | null;
  createdAt: string;
}

export interface AdminMetrics {
  totalUsers: number;
  admins: number;
  lockedUsers: number;
  flaggedUsers: number;
  activeToday: number;
  activeThisMonth: number;
  onlineNow: number;
  totalReceipts: number;
  totalDebts: number;
  totalPayments: number;
  ocrScansThisMonth: number;
  ocrMonthlyLimit: number;
  registrationsByDay: AdminMetricPoint[];
  ocrUsageByMonth: AdminMetricPoint[];
  topTrust: AdminUser[];
  bottomTrust: AdminUser[];
}

/**
 * Per-country VAT/tax setting, managed by admins. `rate` is a percent (e.g. 12).
 * `inclusive` = tax is already baked into prices (not added to the total);
 * when false, the tax is added on top at checkout.
 */
export interface TaxSetting {
  id: number;
  countryCode: string;
  countryName: string;
  rate: number;
  inclusive: boolean;
}

/** Create/update payload for a country tax setting (upsert by countryCode). */
export interface TaxSettingRequest {
  countryCode: string;
  countryName: string;
  rate: number;
  inclusive: boolean;
}

/** Editable profile fields sent to PUT /me/profile. */
export interface UpdateProfileRequest {
  currency?: Currency;
  mobile?: string | null;
  gender?: string | null;
  country?: string | null;
  city?: string | null;
  taxEnabled?: boolean;
}

/** A country option for the registration dropdown (public). */
export interface CountryOption {
  countryCode: string;
  countryName: string;
}

/** The tax setting that applies to the current user, resolved from their country. */
export interface MyTaxSetting {
  countryCode: string | null;
  countryName: string | null;
  rate: number;
  inclusive: boolean;
  enabled: boolean;
  configured: boolean;
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
  flagged: boolean;
}

export type FriendshipStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

export interface UserSummary {
  id: number;
  fullName: string;
  username: string;
  trustScore: number;
  mobile: string | null;
  flagged: boolean;
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
  invoiceNumber: string | null;
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
  confirmed: boolean;
}

/** An assignment awaiting the current user's confirmation. */
export interface PendingAssignment {
  assignmentId: number;
  receiptId: number;
  storeName: string;
  ownerUsername: string;
  itemName: string;
  shareAmount: string;
}

export interface DirectMessage {
  id: number;
  senderUsername: string;
  mine: boolean;
  body: string;
  read: boolean;
  createdAt: string;
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
  invoiceNumber: string | null;
  items: ParsedItem[];
  serviceCharge: string | null;
  tax: string | null;
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
  invoiceNumber: string | null;
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

export interface DebtComment {
  id: number;
  authorUsername: string;
  mine: boolean;
  body: string;
  createdAt: string;
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
  | 'DEBT_SETTLED'
  | 'DEBT_MARKED_PAID'
  | 'DEBT_COMMENT'
  | 'ASSIGNMENT_CONFIRM_REQUEST'
  | 'ASSIGNMENT_CONFIRMED'
  | 'ASSIGNMENT_DECLINED'
  | 'DIRECT_MESSAGE'
  | 'ACCOUNT_FLAGGED'
  | 'ACCOUNT_LOCKED';

export interface Notification {
  id: number;
  type: NotificationType;
  message: string;
  read: boolean;
  createdAt: string;
}
