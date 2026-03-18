import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  TextInput,
  Modal,
  StyleSheet,
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTheme } from '../theme/provider';
import { Card } from '../components/ui/Card';
import { BudgetProgressBar } from '../components/charts/BudgetProgressBar';
import { formatINR } from '../utils/currency';
import * as budgetsRepo from '../db/repositories/budgets';
import * as categoriesRepo from '../db/repositories/categories';
import * as txRepo from '../db/repositories/transactions';
import type { Category, Budget } from '../db/types';

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

interface CategoryWithBudget {
  category: Category;
  budget: Budget | null;
  spent: number;
}

export default function BudgetScreen() {
  const { colors, tokens } = useTheme();
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [items, setItems] = useState<CategoryWithBudget[]>([]);
  const [loading, setLoading] = useState(true);

  const [editItem, setEditItem] = useState<CategoryWithBudget | null>(null);
  const [inputValue, setInputValue] = useState('');
  const [saving, setSaving] = useState(false);

  const monthStr = `${year}-${String(month).padStart(2, '0')}`;

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [cats, budgets, catTotals] = await Promise.all([
        categoriesRepo.getVisible(),
        budgetsRepo.getForMonth(monthStr),
        txRepo.getCategoryTotals(year, month),
      ]);

      const budgetMap = new Map<string, Budget>();
      for (const b of budgets) {
        if (b.category_id) budgetMap.set(b.category_id, b);
      }

      const spentMap = new Map<string, number>();
      for (const ct of catTotals) {
        if (ct.categoryId) spentMap.set(ct.categoryId, ct.total);
      }

      const result: CategoryWithBudget[] = cats.map((cat) => ({
        category: cat,
        budget: budgetMap.get(cat.id) ?? null,
        spent: spentMap.get(cat.id) ?? 0,
      }));

      setItems(result);
    } finally {
      setLoading(false);
    }
  }, [monthStr, year, month]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  function navigateMonth(direction: -1 | 1) {
    if (direction === -1) {
      if (month === 1) { setYear(y => y - 1); setMonth(12); }
      else setMonth(m => m - 1);
    } else {
      if (month === 12) { setYear(y => y + 1); setMonth(1); }
      else setMonth(m => m + 1);
    }
  }

  function openEdit(item: CategoryWithBudget) {
    setEditItem(item);
    const existing = item.budget ? (item.budget.limit_paise / 100).toFixed(0) : '';
    setInputValue(existing);
  }

  async function saveBudget() {
    if (!editItem) return;
    const rupees = parseFloat(inputValue);
    if (isNaN(rupees) || rupees < 0) {
      Alert.alert('Invalid amount', 'Please enter a valid amount.');
      return;
    }
    setSaving(true);
    try {
      const paise = Math.round(rupees * 100);
      await budgetsRepo.upsert(editItem.category.id, monthStr, paise);
      setEditItem(null);
      await loadData();
    } finally {
      setSaving(false);
    }
  }

  async function deleteBudget() {
    if (!editItem?.budget) return;
    setSaving(true);
    try {
      await budgetsRepo.deleteBudget(editItem.budget.id);
      setEditItem(null);
      await loadData();
    } finally {
      setSaving(false);
    }
  }

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
      <ScrollView contentContainerStyle={{ padding: tokens.spacing.md, gap: tokens.spacing.md }} showsVerticalScrollIndicator={false}>

        {/* Month Selector */}
        <View style={[styles.monthSelector, { backgroundColor: colors.surface, borderColor: colors.border, borderRadius: tokens.radii.md }]}>
          <TouchableOpacity onPress={() => navigateMonth(-1)} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
            <Text style={[styles.arrow, { color: colors.primary }]}>‹</Text>
          </TouchableOpacity>
          <Text style={[styles.monthLabel, { color: colors.text, fontSize: tokens.typography.sizes.lg }]}>
            {MONTH_NAMES[month - 1]} {year}
          </Text>
          <TouchableOpacity onPress={() => navigateMonth(1)} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
            <Text style={[styles.arrow, { color: colors.primary }]}>›</Text>
          </TouchableOpacity>
        </View>

        {loading ? (
          <ActivityIndicator size="large" color={colors.primary} />
        ) : (
          <Card padding="none">
            {items.map((item, idx) => (
              <TouchableOpacity
                key={item.category.id}
                onPress={() => openEdit(item)}
                style={[
                  styles.row,
                  {
                    borderBottomColor: colors.border,
                    borderBottomWidth: idx < items.length - 1 ? 1 : 0,
                    padding: tokens.spacing.md,
                  },
                ]}
              >
                <View style={styles.rowHeader}>
                  <View style={styles.rowLeft}>
                    <Text style={{ fontSize: tokens.typography.sizes.lg }}>{item.category.icon}</Text>
                    <Text style={[{ color: colors.text, fontSize: tokens.typography.sizes.md, fontWeight: '500' }]}>
                      {item.category.name}
                    </Text>
                  </View>
                  <View style={styles.rowRight}>
                    {item.budget ? (
                      <Text style={[{ color: colors.textSecondary, fontSize: tokens.typography.sizes.sm }]}>
                        {formatINR(item.budget.limit_paise)} limit
                      </Text>
                    ) : (
                      <Text style={[{ color: colors.primary, fontSize: tokens.typography.sizes.sm }]}>
                        Set budget
                      </Text>
                    )}
                  </View>
                </View>
                {item.budget ? (
                  <View style={{ marginTop: tokens.spacing.sm }}>
                    <BudgetProgressBar spent={item.spent} budget={item.budget.limit_paise} />
                  </View>
                ) : item.spent > 0 ? (
                  <Text style={[{ color: colors.textSecondary, fontSize: tokens.typography.sizes.sm, marginTop: 4 }]}>
                    Spent: {formatINR(item.spent)} (no budget set)
                  </Text>
                ) : null}
              </TouchableOpacity>
            ))}
          </Card>
        )}
      </ScrollView>

      {/* Edit Modal */}
      <Modal visible={editItem !== null} transparent animationType="slide" onRequestClose={() => setEditItem(null)}>
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.modalOverlay}>
          <TouchableOpacity style={styles.modalBackdrop} activeOpacity={1} onPress={() => setEditItem(null)} />
          <View style={[styles.modalSheet, { backgroundColor: colors.surface, borderColor: colors.border }]}>
            <View style={styles.modalHeader}>
              <Text style={{ fontSize: 20, marginRight: 8 }}>{editItem?.category.icon}</Text>
              <Text style={[styles.modalTitle, { color: colors.text, fontSize: tokens.typography.sizes.lg }]}>
                {editItem?.category.name} Budget
              </Text>
            </View>
            <Text style={[{ color: colors.textSecondary, fontSize: tokens.typography.sizes.sm, marginBottom: tokens.spacing.md }]}>
              Set a monthly spending limit for {MONTH_NAMES[month - 1]} {year}
            </Text>
            <View style={[styles.inputRow, { borderColor: colors.border, borderRadius: tokens.radii.md, backgroundColor: colors.background }]}>
              <Text style={[{ color: colors.text, fontSize: tokens.typography.sizes.xl, fontWeight: '500' }]}>₹</Text>
              <TextInput
                style={[styles.input, { color: colors.text, fontSize: tokens.typography.sizes.xl }]}
                keyboardType="decimal-pad"
                value={inputValue}
                onChangeText={setInputValue}
                placeholder="0"
                placeholderTextColor={colors.textSecondary}
                autoFocus
              />
            </View>
            <View style={styles.modalActions}>
              {editItem?.budget ? (
                <TouchableOpacity
                  onPress={deleteBudget}
                  disabled={saving}
                  style={[styles.deleteBtn, { borderColor: colors.error, borderRadius: tokens.radii.md }]}
                >
                  <Text style={[{ color: colors.error, fontSize: tokens.typography.sizes.md, fontWeight: '600' }]}>
                    Remove
                  </Text>
                </TouchableOpacity>
              ) : <View style={{ flex: 1 }} />}
              <TouchableOpacity
                onPress={saveBudget}
                disabled={saving}
                style={[styles.saveBtn, { backgroundColor: colors.primary, borderRadius: tokens.radii.md }]}
              >
                {saving ? (
                  <ActivityIndicator color={colors.primaryText} />
                ) : (
                  <Text style={[{ color: colors.primaryText, fontSize: tokens.typography.sizes.md, fontWeight: '600' }]}>
                    Save
                  </Text>
                )}
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  monthSelector: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 8,
    paddingVertical: 10,
    borderWidth: 1,
  },
  arrow: { fontSize: 28, fontWeight: '300', lineHeight: 32 },
  monthLabel: { fontWeight: '600' },
  row: {},
  rowHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  rowLeft: { flexDirection: 'row', alignItems: 'center', gap: 8, flex: 1 },
  rowRight: { alignItems: 'flex-end' },
  modalOverlay: { flex: 1, justifyContent: 'flex-end' },
  modalBackdrop: { flex: 1 },
  modalSheet: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    borderTopWidth: 1,
    borderLeftWidth: 1,
    borderRightWidth: 1,
    padding: 24,
    paddingBottom: 40,
  },
  modalHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  modalTitle: { fontWeight: '700' },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 4,
    marginBottom: 20,
  },
  input: { flex: 1, fontWeight: '500' },
  modalActions: { flexDirection: 'row', gap: 12 },
  deleteBtn: { flex: 1, borderWidth: 1.5, padding: 14, alignItems: 'center', justifyContent: 'center' },
  saveBtn: { flex: 1, padding: 14, alignItems: 'center', justifyContent: 'center' },
});
