import { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  RefreshControl,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTheme } from '../../theme/provider';
import { useTransactionFlowStore } from '../../stores/transaction-flow';
import { getByMonth } from '../../db/repositories/transactions';
import { getAll } from '../../db/repositories/categories';
import { TransactionListItem } from '../../components/TransactionListItem';
import type { Transaction, Category } from '../../db/types';

type GroupedSection = {
  date: string; // 'YYYY-MM-DD'
  label: string;
  transactions: Transaction[];
};

function groupByDate(transactions: Transaction[]): GroupedSection[] {
  const map: Record<string, Transaction[]> = {};
  for (const tx of transactions) {
    const date = tx.created_at.split('T')[0];
    if (!map[date]) map[date] = [];
    map[date].push(tx);
  }
  return Object.entries(map)
    .sort(([a], [b]) => b.localeCompare(a))
    .map(([date, txs]) => ({
      date,
      label: formatDateLabel(date),
      transactions: txs,
    }));
}

function formatDateLabel(dateStr: string): string {
  const date = new Date(dateStr + 'T00:00:00');
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);

  const d = date.toDateString();
  if (d === today.toDateString()) return 'Today';
  if (d === yesterday.toDateString()) return 'Yesterday';

  return date.toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'long',
    year: date.getFullYear() !== today.getFullYear() ? 'numeric' : undefined,
  });
}

type ListItem =
  | { type: 'header'; key: string; label: string }
  | { type: 'tx'; key: string; transaction: Transaction };

export default function HistoryScreen() {
  const { colors, tokens } = useTheme();
  const flowStatus = useTransactionFlowStore((s) => s.status);

  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const now = new Date();

  async function loadData() {
    try {
      const [txs, cats] = await Promise.all([
        getByMonth(now.getFullYear(), now.getMonth() + 1),
        getAll(),
      ]);
      setTransactions(txs);
      setCategories(cats);
    } catch {
      // silently ignore
    }
  }

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await loadData();
    setRefreshing(false);
  }, []);

  useEffect(() => {
    loadData().finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (flowStatus === 'done') {
      loadData();
    }
  }, [flowStatus]);

  const categoryMap = Object.fromEntries(categories.map((c) => [c.id, c]));

  const sections = groupByDate(transactions);

  // Flatten into list items for FlatList
  const listData: ListItem[] = [];
  for (const section of sections) {
    listData.push({ type: 'header', key: `header-${section.date}`, label: section.label });
    for (const tx of section.transactions) {
      listData.push({ type: 'tx', key: tx.id, transaction: tx });
    }
  }

  const monthLabel = now.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: colors.background }]}>
      {/* Header */}
      <View
        style={[
          styles.screenHeader,
          {
            paddingHorizontal: tokens.spacing.lg,
            paddingTop: tokens.spacing.lg,
            paddingBottom: tokens.spacing.md,
            borderBottomWidth: 1,
            borderBottomColor: colors.border,
          },
        ]}
      >
        <Text style={[styles.screenTitle, { color: colors.text, fontSize: tokens.typography.sizes.xxl, fontWeight: '700' }]}>
          History
        </Text>
        <Text style={[styles.monthLabel, { color: colors.textSecondary, fontSize: tokens.typography.sizes.sm, marginTop: 2 }]}>
          {monthLabel}
        </Text>
      </View>

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.primary} />
        </View>
      ) : transactions.length === 0 ? (
        <View style={styles.centerFill}>
          <Text style={{ fontSize: 48 }}>📭</Text>
          <Text style={[styles.emptyTitle, { color: colors.text, fontSize: tokens.typography.sizes.lg, fontWeight: '600', marginTop: tokens.spacing.md }]}>
            No transactions yet
          </Text>
          <Text style={[styles.emptySubtitle, { color: colors.textSecondary, fontSize: tokens.typography.sizes.sm, marginTop: tokens.spacing.xs }]}>
            Your payments this month will appear here
          </Text>
        </View>
      ) : (
        <FlatList
          data={listData}
          keyExtractor={(item) => item.key}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />
          }
          renderItem={({ item }) => {
            if (item.type === 'header') {
              return (
                <View
                  style={{
                    paddingHorizontal: tokens.spacing.lg,
                    paddingVertical: tokens.spacing.sm,
                    backgroundColor: colors.background,
                  }}
                >
                  <Text
                    style={{
                      color: colors.textSecondary,
                      fontSize: tokens.typography.sizes.sm,
                      fontWeight: '600',
                      textTransform: 'uppercase',
                      letterSpacing: 0.5,
                    }}
                  >
                    {item.label}
                  </Text>
                </View>
              );
            }

            const tx = item.transaction;
            const cat = tx.category_id ? categoryMap[tx.category_id] : undefined;

            return (
              <View style={{ backgroundColor: colors.surface }}>
                <TransactionListItem
                  transaction={tx}
                  categoryName={cat?.name}
                  categoryIcon={cat?.icon}
                  categoryColor={cat?.color}
                />
              </View>
            );
          }}
          contentContainerStyle={{ paddingBottom: tokens.spacing.xl }}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  screenHeader: {},
  screenTitle: {},
  monthLabel: {},
  centerFill: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyTitle: { textAlign: 'center' },
  emptySubtitle: { textAlign: 'center' },
});
