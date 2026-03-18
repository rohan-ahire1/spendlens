export interface Category {
  id: string;
  name: string;
  icon: string;
  color: string;
  sort_order: number;
  is_default: number; // 1 | 0
  is_hidden: number; // 1 | 0
  created_at: string;
}

export interface Merchant {
  id: string;
  name: string;
  raw_names: string; // JSON array string
  upi_id: string | null;
  category_id: string | null;
  confidence: number;
  tx_count: number;
}

export interface NewMerchant {
  id?: string;
  name: string;
  raw_names: string[]; // will be JSON.stringify'd
  upi_id?: string | null;
  category_id?: string | null;
  confidence?: number;
}

export type TransactionStatus = 'pending' | 'completed' | 'failed' | 'cancelled';
export type PaymentMethod = 'qr' | 'upi_id' | 'phone' | 'contact';

export interface Transaction {
  id: string;
  amount_paise: number;
  merchant_id: string | null;
  category_id: string | null;
  recipient_upi_id: string | null;
  recipient_name: string | null;
  recipient_phone: string | null;
  payment_method: PaymentMethod;
  upi_app: string | null;
  status: TransactionStatus;
  note: string | null;
  auto_categorized: number; // 1 | 0
  categorization_confidence: number | null;
  created_at: string;
  completed_at: string | null;
}

export interface NewTransaction {
  id?: string;
  amount_paise: number;
  merchant_id?: string | null;
  category_id?: string | null;
  recipient_upi_id?: string | null;
  recipient_name?: string | null;
  recipient_phone?: string | null;
  payment_method: PaymentMethod;
  upi_app?: string | null;
  status?: TransactionStatus;
  note?: string | null;
  auto_categorized?: number;
  categorization_confidence?: number | null;
  completed_at?: string | null;
}

export interface UserRule {
  id: string;
  match_type: 'merchant_name' | 'upi_id' | 'recipient_name';
  match_value: string;
  category_id: string;
  hit_count: number;
  created_at: string;
  updated_at: string;
}

export interface Budget {
  id: string;
  category_id: string | null;
  month: string; // 'YYYY-MM'
  limit_paise: number;
  created_at: string;
}
