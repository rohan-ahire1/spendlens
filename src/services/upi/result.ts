import { UpiPaymentResult } from './types';

interface RawUpiLaunchResult {
  resultCode?: number;
  data?: string;
  extra?: Record<string, unknown>;
}

type FlatMap = Record<string, string>;

export function parseUpiPaymentResult(result: RawUpiLaunchResult | null): UpiPaymentResult | null {
  if (!result) return null;

  const flat = flattenResult(result);
  const statusValue = firstValue(
    flat.Status,
    flat.status,
    flat.txnStatus,
    flat.transactionStatus,
  );
  const normalizedStatus = statusValue?.trim().toLowerCase();

  let status: UpiPaymentResult['status'] = 'unknown';
  if (normalizedStatus) {
    if (normalizedStatus.includes('success') || normalizedStatus.includes('succeed')) {
      status = 'completed';
    } else if (normalizedStatus.includes('fail') || normalizedStatus.includes('declin')) {
      status = 'failed';
    } else if (normalizedStatus.includes('cancel')) {
      status = 'cancelled';
    }
  } else if (result.resultCode === 0) {
    status = 'cancelled';
  }

  const amount = parseAmount(
    firstValue(
      flat.amount,
      flat.am,
      flat.transactionAmount,
      flat.amountPaid,
    )
  );

  return {
    status,
    amount,
    responseCode: firstValue(flat.responseCode, flat.ResponseCode),
    transactionRef: firstValue(flat.txnRef, flat.tr, flat.ApprovalRefNo, flat.approvalRefNo),
    raw: firstValue(flat.tezResponse, flat.response, result.data),
  };
}

function flattenResult(result: RawUpiLaunchResult): FlatMap {
  const flat: FlatMap = {};

  if (result.data) {
    mergeMap(flat, parseKeyValuePayload(result.data));
    flat.data = result.data;
  }

  if (result.extra) {
    collectValues(flat, result.extra);
  }

  return flat;
}

function collectValues(target: FlatMap, value: unknown, keyHint?: string): void {
  if (value == null) return;

  if (typeof value === 'string') {
    if (keyHint) target[keyHint] = value;
    mergeMap(target, parseKeyValuePayload(value));
    return;
  }

  if (typeof value === 'number' || typeof value === 'boolean') {
    if (keyHint) target[keyHint] = String(value);
    return;
  }

  if (typeof value !== 'object' || Array.isArray(value)) {
    return;
  }

  const objectValue = value as Record<string, unknown>;

  if (keyHint === 'amountPaid') {
    const parsed = parseAmountObject(objectValue);
    if (parsed !== undefined) {
      target.amountPaid = parsed.toFixed(2);
    }
  }

  for (const [key, nestedValue] of Object.entries(objectValue)) {
    collectValues(target, nestedValue, key);
  }
}

function parseKeyValuePayload(value: string): FlatMap {
  const trimmed = value.trim();
  if (!trimmed.includes('=')) return {};

  const query = trimmed.includes('?') ? trimmed.split('?').slice(1).join('?') : trimmed;
  const normalized = query.replace(/^\?/, '');
  const params = normalized.split('&');
  const output: FlatMap = {};

  for (const part of params) {
    if (!part) continue;
    const [rawKey, ...rawRest] = part.split('=');
    if (!rawKey || rawRest.length === 0) continue;
    const rawValue = rawRest.join('=');
    output[safeDecode(rawKey)] = safeDecode(rawValue);
  }

  return output;
}

function safeDecode(value: string): string {
  try {
    return decodeURIComponent(value.replace(/\+/g, '%20'));
  } catch {
    return value;
  }
}

function parseAmount(value?: string): number | undefined {
  if (!value) return undefined;
  const parsed = parseFloat(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function parseAmountObject(value: Record<string, unknown>): number | undefined {
  const units = typeof value.units === 'string' ? parseFloat(value.units) : 0;
  const nanos = typeof value.nanos === 'number' ? value.nanos / 1_000_000_000 : 0;
  const total = units + nanos;
  return Number.isFinite(total) && total > 0 ? total : undefined;
}

function mergeMap(target: FlatMap, next: FlatMap): void {
  for (const [key, value] of Object.entries(next)) {
    if (!(key in target)) {
      target[key] = value;
    }
  }
}

function firstValue(...values: Array<string | undefined>): string | undefined {
  return values.find((value) => value !== undefined && value !== '');
}
