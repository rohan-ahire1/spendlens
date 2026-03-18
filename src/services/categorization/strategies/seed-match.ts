import { MERCHANT_SEED_DATA } from '../seed-data';
import type { TransactionContext, CategorizationResult } from '../engine';

function normalize(s: string): string {
  return s.toLowerCase().replace(/[^a-z0-9\s&.'-]/g, '').trim();
}

/**
 * Match recipient name / UPI ID against known merchant seed data via substring matching.
 * Returns confidence 0.8 on match.
 */
export async function seedMatchStrategy(
  ctx: TransactionContext
): Promise<CategorizationResult | null> {
  const candidates: string[] = [];

  if (ctx.recipientName) {
    candidates.push(normalize(ctx.recipientName));
  }
  if (ctx.recipientUpiId) {
    // UPI IDs look like "merchant@bank" — include the handle portion too
    candidates.push(normalize(ctx.recipientUpiId));
    const atIdx = ctx.recipientUpiId.indexOf('@');
    if (atIdx > 0) {
      candidates.push(normalize(ctx.recipientUpiId.slice(0, atIdx)));
    }
  }

  for (const candidate of candidates) {
    for (const [key, categoryId] of Object.entries(MERCHANT_SEED_DATA)) {
      if (candidate.includes(key)) {
        return {
          categoryId,
          confidence: 0.8,
          source: 'seed_data',
        };
      }
    }
  }

  return null;
}
