import { findRule } from '../../../db/repositories/user-rules';
import type { TransactionContext, CategorizationResult } from '../engine';

/**
 * Look up user-defined rules in priority order: UPI ID → merchant name → recipient name.
 * Confidence increases with hit count: 0.7 + (hit_count * 0.05), capped at 0.95.
 */
export async function userLearnedStrategy(
  ctx: TransactionContext
): Promise<CategorizationResult | null> {
  let rule = null;

  if (ctx.recipientUpiId) {
    rule = await findRule('upi_id', ctx.recipientUpiId);
  }

  if (!rule && ctx.recipientName) {
    rule = await findRule('merchant_name', ctx.recipientName);
  }

  if (!rule && ctx.recipientName) {
    rule = await findRule('recipient_name', ctx.recipientName);
  }

  if (!rule) {
    return null;
  }

  const confidence = Math.min(0.95, 0.7 + rule.hit_count * 0.05);

  return {
    categoryId: rule.category_id,
    confidence,
    source: 'user_rule',
  };
}
