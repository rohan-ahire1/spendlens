import * as SQLite from 'expo-sqlite';
import { runMigrations } from './migrations';

let _db: SQLite.SQLiteDatabase | null = null;
let _initPromise: Promise<SQLite.SQLiteDatabase> | null = null;

export async function getDatabase(): Promise<SQLite.SQLiteDatabase> {
  if (_db) return _db;

  if (_initPromise) return _initPromise;

  _initPromise = (async () => {
    const db = await SQLite.openDatabaseAsync('spendlens.db');

    // Enable WAL mode for better performance
    await db.execAsync('PRAGMA journal_mode = WAL');
    await db.execAsync('PRAGMA foreign_keys = ON');

    // Run migrations (idempotent)
    await runMigrations(db);

    _db = db;
    return db;
  })();

  return _initPromise;
}
