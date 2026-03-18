import { userLearnedStrategy } from './strategies/user-learned';
import { merchantLookupStrategy } from './strategies/merchant-lookup';
import { seedMatchStrategy } from './strategies/seed-match';
import { timeHeuristicStrategy } from './strategies/time-heuristic';

export interface TransactionContext {
  recipientName?: string | null;
  recipientUpiId?: string | null;
  amountPaise: number;
  timestamp: Date;
}

export interface CategorizationResult {
  categoryId: string;
  confidence: number;
  source: 'user_rule' | 'merchant_db' | 'seed_data' | 'time_heuristic';
}

const AUTO_APPLY_THRESHOLD = 0.7;

/**
 * Run categorization strategies in priority order.
 *
 * - First result with confidence >= 0.7 → auto-categorize
 * - Otherwise return the best low-confidence result for UI pre-selection
 * - Returns null if no strategy matches at all
 */
export async function categorize(
  ctx: TransactionContext
): Promise<CategorizationResult | null> {
  const strategies = [
    userLearnedStrategy,
    merchantLookupStrategy,
    seedMatchStrategy,
    timeHeuristicStrategy,
  ];

  let best: CategorizationResult | null = null;

  for (const strategy of strategies) {
    const result = await strategy(ctx);
    if (!result) continue;

    if (result.confidence >= AUTO_APPLY_THRESHOLD) {
      return result;
    }

    if (!best || result.confidence > best.confidence) {
      best = result;
    }
  }

  return best;
}
