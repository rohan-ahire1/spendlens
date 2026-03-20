export interface UpiPaymentRequest {
  payeeAddress: string;     // pa= (UPI ID like merchant@upi)
  payeeName?: string;       // pn=
  amount?: number;          // am= (in rupees, decimal)
  currencyCode?: string;    // cu= (default INR)
  transactionNote?: string; // tn=
  merchantCode?: string;    // mc=
  transactionRef?: string;  // tr=
}

export type PaymentMethod = 'qr' | 'upi_id' | 'phone' | 'contact';

export type PaymentStatus = 'pending' | 'completed' | 'failed' | 'cancelled';

export type FlowStatus = 'idle' | 'captured' | 'launching' | 'awaiting_return' | 'confirming' | 'categorizing' | 'done';

export type UpiReturnStatus = 'completed' | 'failed' | 'cancelled' | 'unknown';

export interface UpiPaymentResult {
  status: UpiReturnStatus;
  amount?: number;
  responseCode?: string;
  transactionRef?: string;
  raw?: string;
}

// Known UPI app package names
export const UPI_APPS = {
  phonepe: 'com.phonepe.app',
  gpay: 'com.google.android.apps.nbu.paisa.user',
  paytm: 'net.one97.paytm',
  cred: 'com.dreamplug.androidapp',
  amazonpay: 'in.amazon.mShop.android.shopping',
} as const;

export type UpiAppKey = keyof typeof UPI_APPS;

export const UPI_APP_LABELS: Record<UpiAppKey, string> = {
  phonepe: 'PhonePe',
  gpay: 'GPay',
  paytm: 'Paytm',
  cred: 'CRED',
  amazonpay: 'Amazon Pay',
};
