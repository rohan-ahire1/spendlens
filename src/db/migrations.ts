import type { SQLiteDatabase } from 'expo-sqlite';
import {
  CREATE_MIGRATIONS_TABLE,
  CREATE_CATEGORIES_TABLE,
  CREATE_MERCHANTS_TABLE,
  CREATE_MERCHANTS_INDEXES,
  CREATE_TRANSACTIONS_TABLE,
  CREATE_TRANSACTIONS_INDEXES,
  CREATE_USER_RULES_TABLE,
  CREATE_USER_RULES_INDEXES,
  CREATE_BUDGETS_TABLE,
  CREATE_BUDGETS_INDEXES,
} from './schema';
import { DEFAULT_CATEGORIES } from './seed';

async function getAppliedVersions(db: SQLiteDatabase): Promise<Set<number>> {
  const rows = await db.getAllAsync<{ version: number }>(
    'SELECT version FROM _migrations ORDER BY version'
  );
  return new Set(rows.map((r) => r.version));
}

async function markApplied(db: SQLiteDatabase, version: number): Promise<void> {
  await db.runAsync('INSERT INTO _migrations (version) VALUES (?)', [version]);
}

async function migration1(db: SQLiteDatabase): Promise<void> {
  // Create all tables
  await db.execAsync(CREATE_CATEGORIES_TABLE);
  await db.execAsync(CREATE_MERCHANTS_TABLE);
  for (const idx of CREATE_MERCHANTS_INDEXES) {
    await db.execAsync(idx);
  }
  await db.execAsync(CREATE_TRANSACTIONS_TABLE);
  for (const idx of CREATE_TRANSACTIONS_INDEXES) {
    await db.execAsync(idx);
  }
  await db.execAsync(CREATE_USER_RULES_TABLE);
  for (const idx of CREATE_USER_RULES_INDEXES) {
    await db.execAsync(idx);
  }
  await db.execAsync(CREATE_BUDGETS_TABLE);
  for (const idx of CREATE_BUDGETS_INDEXES) {
    await db.execAsync(idx);
  }

  // Seed default categories
  for (const cat of DEFAULT_CATEGORIES) {
    await db.runAsync(
      `INSERT OR IGNORE INTO categories (id, name, icon, color, sort_order, is_default, is_hidden)
       VALUES (?, ?, ?, ?, ?, 1, 0)`,
      [cat.id, cat.name, cat.icon, cat.color, cat.sort_order]
    );
  }
}

const MIGRATIONS: Record<number, (db: SQLiteDatabase) => Promise<void>> = {
  1: migration1,
};

export async function runMigrations(db: SQLiteDatabase): Promise<void> {
  // Ensure migrations tracking table exists first
  await db.execAsync(CREATE_MIGRATIONS_TABLE);

  const applied = await getAppliedVersions(db);

  const versions = Object.keys(MIGRATIONS)
    .map(Number)
    .sort((a, b) => a - b);

  for (const version of versions) {
    if (!applied.has(version)) {
      await MIGRATIONS[version](db);
      await markApplied(db, version);
    }
  }
}
