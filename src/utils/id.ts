export function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substring(2, 9);
}

export function generateUpiTransactionRef(): string {
  const random = Math.floor(Math.random() * 10_000)
    .toString()
    .padStart(4, '0');
  return `SL${Date.now()}${random}`;
}
