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
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../../theme/provider';
import { useTransactionFlowStore } from '../../stores/transaction-flow';
import { getByMonth } from '../../db/repositories/transactions';
import { getAll } from '../../db/repositories/categories';
import { TransactionListItem } from '../../components/TransactionListItem';
import { Card } from '../../components/ui/Card';
import { formatINR } from '../../utils/currency';
import type { Transaction, Category } from '../../db/types';

type GroupedSection = {
  date: string;
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

  const listData: ListItem[] = [];
  for (const section of sections) {
    listData.push({ type: 'header', key: `header-${section.date}`, label: section.label });
    for (const tx of section.transactions) {
      listData.push({ type: 'tx', key: tx.id, transaction: tx });
    }
  }

  const monthLabel = now.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
  const completedTransactions = transactions.filter((tx) => tx.status === 'completed');
  const completedTotal = completedTransactions.reduce((sum, tx) => sum + tx.amount_paise, 0);
  const failedCount = transactions.filter((tx) => tx.status === 'failed').length;

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: colors.background }]}>
      <View style={[styles.orb, { backgroundColor: colors.primary + '10', top: -80, right: -50 }]} />
      <FlatList
        data={listData}
        keyExtractor={(item) => item.key}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />
        }
        ListHeaderComponent={
          <View
            style={{
              paddingHorizontal: tokens.spacing.lg,
              paddingTop: tokens.spacing.lg,
              paddingBottom: tokens.spacing.md,
            }}
          >
            <View
              style={[
                styles.eyebrowBadge,
                {
                  backgroundColor: colors.surface,
                  borderColor: colors.border,
                  borderRadius: tokens.radii.full,
                  paddingHorizontal: tokens.spacing.md,
                  paddingVertical: tokens.spacing.xs,
                },
              ]}
            >
              <Text
                style={{
                  color: colors.primary,
                  fontSize: tokens.typography.sizes.xs,
                  fontFamily: tokens.typography.families.accent,
                  letterSpacing: 1,
                  textTransform: 'uppercase',
                }}
              >
                History
              </Text>
            </View>

            <Text
              style={{
                color: colors.text,
                fontSize: tokens.typography.sizes.xxxl,
                fontFamily: tokens.typography.families.display,
                marginTop: tokens.spacing.md,
              }}
            >
              Every payment, neatly held.
            </Text>
            <Text
              style={{
                color: colors.textSecondary,
                fontSize: tokens.typography.sizes.md,
                lineHeight: 24,
                marginTop: tokens.spacing.sm,
              }}
            >
              {monthLabel}. Pull to refresh when you want the latest payment state.
            </Text>

            <Card
              style={{
                marginTop: tokens.spacing.lg,
                backgroundColor: colors.text,
                borderColor: colors.text,
                padding: tokens.spacing.lg,
              }}
              padding="none"
            >
              <Text
                style={{
                  color: colors.primaryText,
                  opacity: 0.72,
                  fontSize: tokens.typography.sizes.sm,
                  fontFamily: tokens.typography.families.accent,
                }}
              >
                This month
              </Text>
              <Text
                style={{
                  color: colors.primaryText,
                  fontSize: tokens.typography.sizes.xxxl,
                  fontFamily: tokens.typography.families.display,
                  marginTop: tokens.spacing.sm,
                }}
              >
                {formatINR(completedTotal)}
              </Text>
              <View style={[styles.summaryRow, { marginTop: tokens.spacing.lg }]}>
                <View style={[styles.summaryTile, { backgroundColor: 'rgba(255,255,255,0.08)', borderRadius: tokens.radii.md }]}>
                  <Text style={[styles.summaryLabel, { color: colors.primaryText }]}>Completed</Text>
                  <Text style={[styles.summaryValue, { color: colors.primaryText, fontFamily: tokens.typography.families.accent }]}>
                    {completedTransactions.length}
                  </Text>
                </View>
                <View style={[styles.summaryTile, { backgroundColor: 'rgba(255,255,255,0.08)', borderRadius: tokens.radii.md }]}>
                  <Text style={[styles.summaryLabel, { color: colors.primaryText }]}>Failed</Text>
                  <Text style={[styles.summaryValue, { color: colors.primaryText, fontFamily: tokens.typography.families.accent }]}>
                    {failedCount}
                  </Text>
                </View>
              </View>
            </Card>
          </View>
        }
        renderItem={({ item }) => {
          if (item.type === 'header') {
            return (
              <View
                style={{
                  paddingHorizontal: tokens.spacing.lg,
                  paddingTop: tokens.spacing.sm,
                  paddingBottom: tokens.spacing.sm,
                }}
              >
                <View
                  style={[
                    styles.sectionBadge,
                    {
                      backgroundColor: colors.surface,
                      borderColor: colors.border,
                      borderRadius: tokens.radii.full,
                    },
                  ]}
                >
                  <Text
                    style={{
                      color: colors.textSecondary,
                      fontSize: tokens.typography.sizes.xs,
                      fontFamily: tokens.typography.families.accent,
                      letterSpacing: 0.8,
                      textTransform: 'uppercase',
                    }}
                  >
                    {item.label}
                  </Text>
                </View>
              </View>
            );
          }

          const tx = item.transaction;
          const cat = tx.category_id ? categoryMap[tx.category_id] : undefined;

          return (
            <View
              style={{
                paddingHorizontal: tokens.spacing.lg,
                paddingBottom: tokens.spacing.sm,
              }}
            >
              <TransactionListItem
                transaction={tx}
                categoryName={cat?.name}
                categoryIcon={cat?.icon}
                categoryColor={cat?.color}
              />
            </View>
          );
        }}
        ListEmptyComponent={
          loading ? (
            <View style={[styles.centerFill, { paddingTop: tokens.spacing.xxl }]}>
              <ActivityIndicator color={colors.primary} />
            </View>
          ) : (
            <View
              style={{
                paddingHorizontal: tokens.spacing.lg,
                paddingBottom: tokens.spacing.xl,
              }}
            >
              <Card
                style={{
                  alignItems: 'center',
                  paddingVertical: tokens.spacing.xxl,
                }}
              >
                <View
                  style={[
                    styles.emptyIcon,
                    {
                      backgroundColor: colors.primary + '12',
                      borderRadius: tokens.radii.full,
                    },
                  ]}
                >
                  <Ionicons name="receipt-outline" size={26} color={colors.primary} />
                </View>
                <Text
                  style={{
                    color: colors.text,
                    fontSize: tokens.typography.sizes.xl,
                    fontFamily: tokens.typography.families.display,
                    marginTop: tokens.spacing.md,
                    textAlign: 'center',
                  }}
                >
                  Nothing here yet.
                </Text>
                <Text
                  style={{
                    color: colors.textSecondary,
                    fontSize: tokens.typography.sizes.sm,
                    lineHeight: 22,
                    marginTop: tokens.spacing.sm,
                    textAlign: 'center',
                    maxWidth: 260,
                  }}
                >
                  Completed and failed payments will land here as soon as you start using the app.
                </Text>
              </Card>
            </View>
          )
        }
        contentContainerStyle={{
          paddingBottom: tokens.spacing.xl + 88,
        }}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  orb: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 220,
  },
  eyebrowBadge: {
    alignSelf: 'flex-start',
    borderWidth: 1,
  },
  summaryRow: {
    flexDirection: 'row',
    gap: 10,
  },
  summaryTile: {
    flex: 1,
    padding: 14,
  },
  summaryLabel: {
    fontSize: 12,
    textTransform: 'uppercase',
    letterSpacing: 1,
    opacity: 0.72,
  },
  summaryValue: {
    fontSize: 18,
    marginTop: 6,
  },
  sectionBadge: {
    alignSelf: 'flex-start',
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 7,
  },
  centerFill: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyIcon: {
    width: 58,
    height: 58,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
