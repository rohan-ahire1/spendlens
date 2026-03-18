export const CREATE_MIGRATIONS_TABLE = `
  CREATE TABLE IF NOT EXISTS _migrations (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
  )
`;

export const CREATE_CATEGORIES_TABLE = `
  CREATE TABLE IF NOT EXISTS categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    icon TEXT NOT NULL,
    color TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    is_default INTEGER NOT NULL DEFAULT 1,
    is_hidden INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
  )
`;

export const CREATE_MERCHANTS_TABLE = `
  CREATE TABLE IF NOT EXISTS merchants (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    raw_names TEXT NOT NULL,
    upi_id TEXT,
    category_id TEXT REFERENCES categories(id),
    confidence REAL NOT NULL DEFAULT 0,
    tx_count INTEGER NOT NULL DEFAULT 0
  )
`;

export const CREATE_MERCHANTS_INDEXES = [
  `CREATE INDEX IF NOT EXISTS idx_merchants_upi_id ON merchants(upi_id)`,
  `CREATE INDEX IF NOT EXISTS idx_merchants_name ON merchants(name)`,
];

export const CREATE_TRANSACTIONS_TABLE = `
  CREATE TABLE IF NOT EXISTS transactions (
    id TEXT PRIMARY KEY,
    amount_paise INTEGER NOT NULL,
    merchant_id TEXT REFERENCES merchants(id),
    category_id TEXT REFERENCES categories(id),
    recipient_upi_id TEXT,
    recipient_name TEXT,
    recipient_phone TEXT,
    payment_method TEXT NOT NULL,
    upi_app TEXT,
    status TEXT NOT NULL DEFAULT 'pending',
    note TEXT,
    auto_categorized INTEGER NOT NULL DEFAULT 0,
    categorization_confidence REAL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    completed_at TEXT
  )
`;

export const CREATE_TRANSACTIONS_INDEXES = [
  `CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at)`,
  `CREATE INDEX IF NOT EXISTS idx_transactions_category_id ON transactions(category_id)`,
  `CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status)`,
];

export const CREATE_USER_RULES_TABLE = `
  CREATE TABLE IF NOT EXISTS user_rules (
    id TEXT PRIMARY KEY,
    match_type TEXT NOT NULL,
    match_value TEXT NOT NULL,
    category_id TEXT NOT NULL REFERENCES categories(id),
    hit_count INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
  )
`;

export const CREATE_USER_RULES_INDEXES = [
  `CREATE UNIQUE INDEX IF NOT EXISTS idx_user_rules_match ON user_rules(match_type, match_value)`,
];

export const CREATE_BUDGETS_TABLE = `
  CREATE TABLE IF NOT EXISTS budgets (
    id TEXT PRIMARY KEY,
    category_id TEXT REFERENCES categories(id),
    month TEXT NOT NULL,
    limit_paise INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
  )
`;

export const CREATE_BUDGETS_INDEXES = [
  `CREATE UNIQUE INDEX IF NOT EXISTS idx_budgets_category_month ON budgets(category_id, month)`,
];
