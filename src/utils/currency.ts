/**
 * Formats a paise value into an INR currency string.
 * @param paise - Amount in paise (1 INR = 100 paise)
 * @returns Formatted string like "₹1,234.56"
 */
export function formatINR(paise: number): string {
  if (!Number.isFinite(paise)) {
    return '₹0.00';
  }

  const isNegative = paise < 0;
  const absPaise = Math.abs(paise);
  const rupees = absPaise / 100;

  const formatted = rupees.toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

  return isNegative ? `-₹${formatted}` : `₹${formatted}`;
}
