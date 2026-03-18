import { UpiPaymentRequest } from './types';

/**
 * Parse a UPI URI like `upi://pay?pa=merchant@upi&pn=StoreName&am=500.00&cu=INR`
 * Returns null if URI doesn't start with `upi://` or has no `pa=` param.
 */
export function parseUpiUri(uri: string): UpiPaymentRequest | null {
  if (!uri.startsWith('upi://')) {
    return null;
  }

  // Replace upi:// with https:// so URL parser can handle it
  const normalized = uri.replace('upi://', 'https://upi/');
  let params: URLSearchParams;
  try {
    const url = new URL(normalized);
    params = url.searchParams;
  } catch {
    return null;
  }

  const pa = params.get('pa');
  if (!pa) {
    return null;
  }

  const request: UpiPaymentRequest = {
    payeeAddress: pa,
  };

  const pn = params.get('pn');
  if (pn) request.payeeName = pn;

  const am = params.get('am');
  if (am) {
    const parsed = parseFloat(am);
    if (!isNaN(parsed)) {
      request.amount = parsed;
    }
  }

  const cu = params.get('cu');
  if (cu) request.currencyCode = cu;

  const tn = params.get('tn');
  if (tn) request.transactionNote = tn;

  const mc = params.get('mc');
  if (mc) request.merchantCode = mc;

  const tr = params.get('tr');
  if (tr) request.transactionRef = tr;

  return request;
}

/**
 * Build a UPI URI from a UpiPaymentRequest.
 * Only includes non-undefined fields. URL-encodes values.
 */
export function buildUpiUri(request: UpiPaymentRequest): string {
  const params = new URLSearchParams();

  params.set('pa', request.payeeAddress);

  if (request.payeeName !== undefined) params.set('pn', request.payeeName);
  if (request.amount !== undefined) params.set('am', request.amount.toFixed(2));
  if (request.currencyCode !== undefined) params.set('cu', request.currencyCode);
  if (request.transactionNote !== undefined) params.set('tn', request.transactionNote);
  if (request.merchantCode !== undefined) params.set('mc', request.merchantCode);
  if (request.transactionRef !== undefined) params.set('tr', request.transactionRef);

  return `upi://pay?${params.toString()}`;
}
