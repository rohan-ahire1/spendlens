import { getDatabase } from '../client';
import { generateId } from '../../utils/id';
import type { Transaction, NewTransaction, TransactionStatus } from '../types';

export async function create(tx: NewTransaction): Promise<string> {
  const db = await getDatabase();
  const id = tx.id ?? generateId();
  await db.runAsync(
    `INSERT INTO transactions (
      id, amount_paise, merchant_id, category_id,
      recipient_upi_id, recipient_name, recipient_phone,
      payment_method, upi_app, status, note,
      auto_categorized, categorization_confidence, completed_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      id,
      tx.amount_paise,
      tx.merchant_id ?? null,
      tx.category_id ?? null,
      tx.recipient_upi_id ?? null,
      tx.recipient_name ?? null,
      tx.recipient_phone ?? null,
      tx.payment_method,
      tx.upi_app ?? null,
      tx.status ?? 'pending',
      tx.note ?? null,
      tx.auto_categorized ?? 0,
      tx.categorization_confidence ?? null,
      tx.completed_at ?? null,
    ]
  );
  return id;
}

export async function getById(id: string): Promise<Transaction | null> {
  const db = await getDatabase();
  return db.getFirstAsync<Transaction>(
    'SELECT * FROM transactions WHERE id = ?',
    [id]
  );
}

export async function getByMonth(year: number, month: number): Promise<Transaction[]> {
  const db = await getDatabase();
  const monthStr = `${year}-${String(month).padStart(2, '0')}`;
  return db.getAllAsync<Transaction>(
    `SELECT * FROM transactions
     WHERE strftime('%Y-%m', created_at) = ?
     ORDER BY created_at DESC`,
    [monthStr]
  );
}

export async function getByDateRange(start: string, end: string): Promise<Transaction[]> {
  const db = await getDatabase();
  return db.getAllAsync<Transaction>(
    `SELECT * FROM transactions
     WHERE created_at >= ? AND created_at <= ?
     ORDER BY created_at DESC`,
    [start, end]
  );
}

export async function updateStatus(
  id: string,
  status: TransactionStatus,
  completedAt?: string
): Promise<void> {
  const db = await getDatabase();
  await db.runAsync(
    'UPDATE transactions SET status = ?, completed_at = ? WHERE id = ?',
    [status, completedAt ?? null, id]
  );
}

export async function updateCategory(id: string, categoryId: string): Promise<void> {
  const db = await getDatabase();
  await db.runAsync(
    'UPDATE transactions SET category_id = ? WHERE id = ?',
    [categoryId, id]
  );
}

export async function getMonthlyTotal(year: number, month: number): Promise<number> {
  const db = await getDatabase();
  const monthStr = `${year}-${String(month).padStart(2, '0')}`;
  const result = await db.getFirstAsync<{ total: number | null }>(
    `SELECT SUM(amount_paise) as total FROM transactions
     WHERE strftime('%Y-%m', created_at) = ? AND status = 'completed'`,
    [monthStr]
  );
  return result?.total ?? 0;
}

export async function getCategoryTotals(
  year: number,
  month: number
): Promise<Array<{ categoryId: string | null; total: number }>> {
  const db = await getDatabase();
  const monthStr = `${year}-${String(month).padStart(2, '0')}`;
  const rows = await db.getAllAsync<{ category_id: string | null; total: number }>(
    `SELECT category_id, SUM(amount_paise) as total
     FROM transactions
     WHERE strftime('%Y-%m', created_at) = ? AND status = 'completed'
     GROUP BY category_id`,
    [monthStr]
  );
  return rows.map((r) => ({ categoryId: r.category_id, total: r.total }));
}
