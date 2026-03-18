import { generateId } from '../utils/id';

export interface SeedCategory {
  id: string;
  name: string;
  icon: string;
  color: string;
  sort_order: number;
}

// Colors from lightColors.categoryColors
const COLORS = [
  '#4F62E8', // 0 - blue
  '#10B981', // 1 - green
  '#F59E0B', // 2 - amber
  '#EF4444', // 3 - red
  '#8B5CF6', // 4 - purple
  '#EC4899', // 5 - pink
  '#06B6D4', // 6 - cyan
  '#84CC16', // 7 - lime
  '#F97316', // 8 - orange
  '#6366F1', // 9 - indigo
  '#14B8A6', // 10 - teal
];

export const DEFAULT_CATEGORIES: SeedCategory[] = [
  { id: 'food',          name: 'Food & Dining',    icon: '🍕', color: COLORS[8],  sort_order: 0 },
  { id: 'groceries',     name: 'Groceries',        icon: '🛒', color: COLORS[1],  sort_order: 1 },
  { id: 'transport',     name: 'Transport',        icon: '🚗', color: COLORS[0],  sort_order: 2 },
  { id: 'shopping',      name: 'Shopping',         icon: '🛍️', color: COLORS[5],  sort_order: 3 },
  { id: 'entertainment', name: 'Entertainment',    icon: '🎬', color: COLORS[4],  sort_order: 4 },
  { id: 'bills',         name: 'Bills & Utilities',icon: '💡', color: COLORS[2],  sort_order: 5 },
  { id: 'health',        name: 'Health',           icon: '🏥', color: COLORS[3],  sort_order: 6 },
  { id: 'education',     name: 'Education',        icon: '📚', color: COLORS[6],  sort_order: 7 },
  { id: 'personal',      name: 'Personal',         icon: '👤', color: COLORS[9],  sort_order: 8 },
  { id: 'transfers',     name: 'Transfers',        icon: '💸', color: COLORS[10], sort_order: 9 },
  { id: 'other',         name: 'Other',            icon: '📦', color: COLORS[7],  sort_order: 10 },
];
