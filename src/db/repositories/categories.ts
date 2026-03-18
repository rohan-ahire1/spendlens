import { getDatabase } from '../client';
import type { Category } from '../types';

export async function getAll(): Promise<Category[]> {
  const db = await getDatabase();
  return db.getAllAsync<Category>(
    'SELECT * FROM categories ORDER BY sort_order ASC'
  );
}

export async function getVisible(): Promise<Category[]> {
  const db = await getDatabase();
  return db.getAllAsync<Category>(
    'SELECT * FROM categories WHERE is_hidden = 0 ORDER BY sort_order ASC'
  );
}

export async function updateCategory(
  id: string,
  updates: Partial<Pick<Category, 'name' | 'icon' | 'color' | 'sort_order' | 'is_hidden'>>
): Promise<void> {
  const db = await getDatabase();
  const fields = Object.keys(updates);
  if (fields.length === 0) return;
  const setClause = fields.map((f) => `${f} = ?`).join(', ');
  const values = fields.map((f) => (updates as Record<string, unknown>)[f]) as (string | number | null)[];
  await db.runAsync(
    `UPDATE categories SET ${setClause} WHERE id = ?`,
    [...values, id]
  );
}

export async function toggleHidden(id: string): Promise<void> {
  const db = await getDatabase();
  await db.runAsync(
    'UPDATE categories SET is_hidden = CASE WHEN is_hidden = 0 THEN 1 ELSE 0 END WHERE id = ?',
    [id]
  );
}
