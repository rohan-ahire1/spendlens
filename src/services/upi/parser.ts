import { UpiPaymentRequest } from './types';

/**
 * Parse a UPI URI like `upi://pay?pa=merchant@upi&pn=StoreName&am=500.00&cu=INR`
 * Returns null if URI doesn't start with `upi://` or has no `pa=` param.
 */
export function parseUpiUri(uri: string): UpiPaymentRequest | null {
  const trimmedUri = uri.trim();
  if (!trimmedUri.toLowerCase().startsWith('upi://')) {
    return null;
  }

  // Replace upi:// with https:// so URL parser can handle it
  const normalized = trimmedUri.replace(/^upi:\/\//i, 'https://upi/');
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
 * Only includes non-undefined fields.
 *
 * We avoid URLSearchParams here because some UPI apps are picky about how
 * deeplink params are encoded, especially the VPA (`pa`) value. In practice
 * they are more reliable when `@` is preserved and spaces are encoded as `%20`
 * rather than `+`.
 */
export function buildUpiUri(request: UpiPaymentRequest): string {
  const parts: string[] = [`pa=${encodeUpiValue(request.payeeAddress, { preserveAt: true })}`];

  if (request.payeeName !== undefined) parts.push(`pn=${encodeUpiValue(request.payeeName)}`);
  if (request.amount !== undefined) parts.push(`am=${encodeUpiValue(request.amount.toFixed(2))}`);
  if (request.currencyCode !== undefined) parts.push(`cu=${encodeUpiValue(request.currencyCode)}`);
  if (request.transactionNote !== undefined) parts.push(`tn=${encodeUpiValue(request.transactionNote)}`);
  if (request.merchantCode !== undefined) parts.push(`mc=${encodeUpiValue(request.merchantCode)}`);
  if (request.transactionRef !== undefined) parts.push(`tr=${encodeUpiValue(request.transactionRef)}`);

  return `upi://pay?${parts.join('&')}`;
}

function encodeUpiValue(
  value: string,
  options?: { preserveAt?: boolean },
): string {
  let encoded = encodeURIComponent(value);

  if (options?.preserveAt) {
    encoded = encoded.replace(/%40/g, '@');
  }

  return encoded;
}
