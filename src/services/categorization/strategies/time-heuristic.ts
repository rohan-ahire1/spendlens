import type { TransactionContext, CategorizationResult } from '../engine';

const FOOD_CATEGORY_ID = 'food';

/**
 * Very weak time-of-day + amount signal used only as a tiebreaker.
 *
 * Lunch/dinner window (11:00–14:00, 19:00–22:00) + ₹50–₹1000 → Food @ 0.15
 * Breakfast window (07:00–10:00) + ₹10–₹50 → Food @ 0.10
 */
export async function timeHeuristicStrategy(
  ctx: TransactionContext
): Promise<CategorizationResult | null> {
  const hour = ctx.timestamp.getHours();
  const amount = ctx.amountPaise;

  const isLunchOrDinner =
    (hour >= 11 && hour < 14) || (hour >= 19 && hour < 22);
  const isMealAmount = amount >= 5000 && amount <= 100_000;

  if (isLunchOrDinner && isMealAmount) {
    return {
      categoryId: FOOD_CATEGORY_ID,
      confidence: 0.15,
      source: 'time_heuristic',
    };
  }

  const isBreakfast = hour >= 7 && hour < 10;
  const isBreakfastAmount = amount >= 1000 && amount <= 5000;

  if (isBreakfast && isBreakfastAmount) {
    return {
      categoryId: FOOD_CATEGORY_ID,
      confidence: 0.1,
      source: 'time_heuristic',
    };
  }

  return null;
}
