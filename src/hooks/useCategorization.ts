import { useState, useEffect } from 'react';
import { getById, updateCategory } from '../db/repositories/transactions';
import { categorize } from '../services/categorization/engine';
import type { CategorizationResult } from '../services/categorization/engine';

const AUTO_APPLY_THRESHOLD = 0.7;

interface UseCategorizationResult {
  suggestion: CategorizationResult | null;
  isAutoApplied: boolean;
  isLoading: boolean;
}

/**
 * Fetches a transaction by ID, runs the categorization engine,
 * and either auto-applies the category (confidence >= 0.7) or
 * surfaces a suggestion for the user to confirm.
 */
export function useCategorization(
  transactionId: string | null
): UseCategorizationResult {
  const [suggestion, setSuggestion] = useState<CategorizationResult | null>(null);
  const [isAutoApplied, setIsAutoApplied] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!transactionId) {
      setSuggestion(null);
      setIsAutoApplied(false);
      return;
    }

    let cancelled = false;

    async function run() {
      setIsLoading(true);
      setSuggestion(null);
      setIsAutoApplied(false);

      try {
        const tx = await getById(transactionId!);
        if (!tx || cancelled) return;

        const result = await categorize({
          recipientName: tx.recipient_name,
          recipientUpiId: tx.recipient_upi_id,
          amountPaise: tx.amount_paise,
          timestamp: new Date(tx.created_at),
        });

        if (cancelled) return;

        if (!result) {
          return;
        }

        if (result.confidence >= AUTO_APPLY_THRESHOLD) {
          await updateCategory(transactionId!, result.categoryId);
          if (!cancelled) {
            setIsAutoApplied(true);
            setSuggestion(result);
          }
        } else {
          setSuggestion(result);
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    run();

    return () => {
      cancelled = true;
    };
  }, [transactionId]);

  return { suggestion, isAutoApplied, isLoading };
}
