import { getDatabase } from '../client';
import { generateId } from '../../utils/id';
import type { UserRule } from '../types';

type MatchType = UserRule['match_type'];

export async function findRule(matchType: MatchType, matchValue: string): Promise<UserRule | null> {
  const db = await getDatabase();
  return db.getFirstAsync<UserRule>(
    'SELECT * FROM user_rules WHERE match_type = ? AND match_value = ?',
    [matchType, matchValue]
  );
}

export async function upsertRule(
  matchType: MatchType,
  matchValue: string,
  categoryId: string
): Promise<void> {
  const db = await getDatabase();
  const existing = await findRule(matchType, matchValue);

  if (existing) {
    await db.runAsync(
      `UPDATE user_rules
       SET category_id = ?, hit_count = hit_count + 1, updated_at = datetime('now')
       WHERE match_type = ? AND match_value = ?`,
      [categoryId, matchType, matchValue]
    );
  } else {
    const id = generateId();
    await db.runAsync(
      `INSERT INTO user_rules (id, match_type, match_value, category_id, hit_count)
       VALUES (?, ?, ?, ?, 1)`,
      [id, matchType, matchValue, categoryId]
    );
  }
}
