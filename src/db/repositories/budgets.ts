import { getDatabase } from '../client';
import { generateId } from '../../utils/id';
import type { Budget } from '../types';

export async function getForMonth(month: string): Promise<Budget[]> {
  const db = await getDatabase();
  return db.getAllAsync<Budget>(
    'SELECT * FROM budgets WHERE month = ? ORDER BY category_id ASC NULLS FIRST',
    [month]
  );
}

export async function upsert(
  categoryId: string | null,
  month: string,
  limitPaise: number
): Promise<void> {
  const db = await getDatabase();
  const id = generateId();
  await db.runAsync(
    `INSERT INTO budgets (id, category_id, month, limit_paise)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(category_id, month) DO UPDATE SET limit_paise = excluded.limit_paise`,
    [id, categoryId, month, limitPaise]
  );
}

export async function deleteBudget(id: string): Promise<void> {
  const db = await getDatabase();
  await db.runAsync('DELETE FROM budgets WHERE id = ?', [id]);
}
