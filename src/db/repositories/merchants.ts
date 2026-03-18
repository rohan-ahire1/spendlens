import { getDatabase } from '../client';
import { generateId } from '../../utils/id';
import type { Merchant, NewMerchant } from '../types';

export async function findByUpiId(upiId: string): Promise<Merchant | null> {
  const db = await getDatabase();
  return db.getFirstAsync<Merchant>(
    'SELECT * FROM merchants WHERE upi_id = ?',
    [upiId]
  );
}

export async function findByName(name: string): Promise<Merchant | null> {
  const db = await getDatabase();
  return db.getFirstAsync<Merchant>(
    'SELECT * FROM merchants WHERE LOWER(name) = LOWER(?)',
    [name]
  );
}

export async function upsert(merchant: NewMerchant): Promise<string> {
  const db = await getDatabase();

  // Check if merchant already exists by upi_id or name
  let existing: Merchant | null = null;
  if (merchant.upi_id) {
    existing = await findByUpiId(merchant.upi_id);
  }
  if (!existing) {
    existing = await findByName(merchant.name);
  }

  if (existing) {
    // Merge raw_names
    const existingRawNames: string[] = JSON.parse(existing.raw_names);
    const newRawNames = merchant.raw_names ?? [];
    const merged = Array.from(new Set([...existingRawNames, ...newRawNames]));
    await db.runAsync(
      `UPDATE merchants SET
        name = ?,
        raw_names = ?,
        upi_id = COALESCE(?, upi_id),
        category_id = COALESCE(?, category_id),
        confidence = CASE WHEN ? > confidence THEN ? ELSE confidence END
       WHERE id = ?`,
      [
        merchant.name,
        JSON.stringify(merged),
        merchant.upi_id ?? null,
        merchant.category_id ?? null,
        merchant.confidence ?? 0,
        merchant.confidence ?? 0,
        existing.id,
      ]
    );
    return existing.id;
  }

  const id = merchant.id ?? generateId();
  await db.runAsync(
    `INSERT INTO merchants (id, name, raw_names, upi_id, category_id, confidence, tx_count)
     VALUES (?, ?, ?, ?, ?, ?, 0)`,
    [
      id,
      merchant.name,
      JSON.stringify(merchant.raw_names ?? [merchant.name]),
      merchant.upi_id ?? null,
      merchant.category_id ?? null,
      merchant.confidence ?? 0,
    ]
  );
  return id;
}

export async function incrementTxCount(id: string): Promise<void> {
  const db = await getDatabase();
  await db.runAsync(
    'UPDATE merchants SET tx_count = tx_count + 1 WHERE id = ?',
    [id]
  );
}
