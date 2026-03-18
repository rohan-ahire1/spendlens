import { findByUpiId, findByName } from '../../../db/repositories/merchants';
import type { TransactionContext, CategorizationResult } from '../engine';

/**
 * Look up merchant by UPI ID first, then by name.
 * Returns the merchant's stored category and confidence if found.
 */
export async function merchantLookupStrategy(
  ctx: TransactionContext
): Promise<CategorizationResult | null> {
  let merchant = null;

  if (ctx.recipientUpiId) {
    merchant = await findByUpiId(ctx.recipientUpiId);
  }

  if (!merchant && ctx.recipientName) {
    merchant = await findByName(ctx.recipientName);
  }

  if (!merchant || !merchant.category_id) {
    return null;
  }

  return {
    categoryId: merchant.category_id,
    confidence: merchant.confidence,
    source: 'merchant_db',
  };
}
