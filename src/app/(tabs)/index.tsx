import { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  RefreshControl,
  Alert,
  StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { useTheme } from '../../theme/provider';
import { useTransactionFlowStore } from '../../stores/transaction-flow';
import { getByMonth } from '../../db/repositories/transactions';
import { formatINR } from '../../utils/currency';
import { Card } from '../../components/ui/Card';
import { useCategorization } from '../../hooks/useCategorization';

function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

function formatDate(date: Date): string {
  return date.toLocaleDateString('en-IN', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });
}

export default function HomeScreen() {
  const { colors, tokens } = useTheme();
  const router = useRouter();
  const flowStatus = useTransactionFlowStore((s) => s.status);
  const transactionId = useTransactionFlowStore((s) => s.transactionId);
  const { suggestion, isAutoApplied } = useCategorization(
    flowStatus === 'categorizing' ? transactionId : null
  );

  const [todayTotal, setTodayTotal] = useState(0);
  const [monthTotal, setMonthTotal] = useState(0);
  const [refreshing, setRefreshing] = useState(false);

  const today = new Date();
  const todayStr = today.toISOString().split('T')[0];

  async function loadData() {
    try {
      const txs = await getByMonth(today.getFullYear(), today.getMonth() + 1);
      const todayTxs = txs.filter(
        (t) => t.status === 'completed' && t.created_at.startsWith(todayStr)
      );
      const monthCompleted = txs.filter((t) => t.status === 'completed');
      setTodayTotal(todayTxs.reduce((s, t) => s + t.amount_paise, 0));
      setMonthTotal(monthCompleted.reduce((s, t) => s + t.amount_paise, 0));
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
    loadData();
  }, []);

  // Refresh when payment flow completes
  useEffect(() => {
    if (flowStatus === 'done') {
      loadData();
    }
  }, [flowStatus]);

  // Handle categorization: auto-apply or prompt user
  useEffect(() => {
    if (flowStatus === 'categorizing') {
      if (isAutoApplied) {
        // High confidence — auto-categorized, finish flow
        useTransactionFlowStore.getState().setCategoryAndFinish(suggestion!.categoryId);
      } else if (suggestion) {
        // Low confidence — show prompt with pre-selected suggestion
        router.push(`/category-prompt?suggested=${suggestion.categoryId}`);
      } else {
        // No suggestion at all — show prompt without pre-selection
        router.push('/category-prompt');
      }
    }
  }, [flowStatus, isAutoApplied, suggestion]);

  // Show confirmation dialog when returning from UPI app
  useEffect(() => {
    if (flowStatus === 'confirming') {
      Alert.alert(
        'Payment Status',
        'Did your payment go through?',
        [
          {
            text: 'Yes, Paid',
            onPress: () => useTransactionFlowStore.getState().confirm('completed'),
          },
          {
            text: 'No, Failed',
            style: 'destructive',
            onPress: () => useTransactionFlowStore.getState().confirm('failed'),
          },
          {
            text: 'Cancel',
            style: 'cancel',
            onPress: () => useTransactionFlowStore.getState().confirm('cancelled'),
          },
        ],
        { cancelable: false }
      );
    }
  }, [flowStatus]);

  const monthName = today.toLocaleDateString('en-IN', { month: 'long' });

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: colors.background }]}>
      <ScrollView
        contentContainerStyle={[styles.scroll, { padding: tokens.spacing.lg }]}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={colors.primary}
          />
        }
      >
        {/* Header */}
        <View style={{ marginBottom: tokens.spacing.lg }}>
          <Text style={[styles.greeting, { color: colors.textSecondary, fontSize: tokens.typography.sizes.sm }]}>
            {getGreeting()}
          </Text>
          <Text style={[styles.dateText, { color: colors.text, fontSize: tokens.typography.sizes.xl, fontWeight: '700', marginTop: 2 }]}>
            {formatDate(today)}
          </Text>
        </View>

        {/* Spend summary cards */}
        <View style={[styles.summaryRow, { marginBottom: tokens.spacing.lg, gap: tokens.spacing.sm }]}>
          <Card style={styles.summaryCard} padding="md">
            <Text style={[styles.summaryLabel, { color: colors.textSecondary, fontSize: tokens.typography.sizes.xs, marginBottom: tokens.spacing.xs }]}>
              Today
            </Text>
            <Text style={[styles.summaryAmount, { color: colors.text, fontSize: tokens.typography.sizes.xxl, fontWeight: '700' }]}>
              {formatINR(todayTotal)}
            </Text>
          </Card>

          <Card style={styles.summaryCard} padding="md">
            <Text style={[styles.summaryLabel, { color: colors.textSecondary, fontSize: tokens.typography.sizes.xs, marginBottom: tokens.spacing.xs }]}>
              {monthName}
            </Text>
            <Text style={[styles.summaryAmount, { color: colors.text, fontSize: tokens.typography.sizes.xxl, fontWeight: '700' }]}>
              {formatINR(monthTotal)}
            </Text>
          </Card>
        </View>

        {/* Quick pay */}
        <Text style={[styles.sectionTitle, { color: colors.text, fontSize: tokens.typography.sizes.md, fontWeight: '600', marginBottom: tokens.spacing.md }]}>
          Quick Pay
        </Text>

        <View style={[styles.quickPayRow, { gap: tokens.spacing.sm, marginBottom: tokens.spacing.xl }]}>
          <QuickPayButton
            icon="⬡"
            label="Scan QR"
            color={colors.primary}
            onPress={() => router.push('/scan')}
          />
          <QuickPayButton
            icon="↗"
            label="Pay UPI"
            color={colors.success}
            onPress={() => router.push('/pay')}
          />
          <QuickPayButton
            icon="☎"
            label="Pay Contact"
            color={colors.warning}
            onPress={() => Alert.alert('Coming Soon', 'Pay by contact will be available soon.')}
          />
        </View>

        {/* View history hint */}
        <TouchableOpacity
          style={[
            styles.historyHint,
            {
              backgroundColor: colors.surface,
              borderColor: colors.border,
              borderRadius: tokens.radii.md,
              padding: tokens.spacing.md,
              borderWidth: 1,
            },
          ]}
          onPress={() => router.push('/(tabs)/history')}
          activeOpacity={0.7}
        >
          <Text style={[styles.historyHintText, { color: colors.text, fontSize: tokens.typography.sizes.md }]}>
            View transaction history →
          </Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

function QuickPayButton({
  icon,
  label,
  color,
  onPress,
}: {
  icon: string;
  label: string;
  color: string;
  onPress: () => void;
}) {
  const { colors, tokens } = useTheme();
  return (
    <TouchableOpacity
      style={[
        styles.quickPayBtn,
        {
          backgroundColor: colors.surface,
          borderColor: colors.border,
          borderRadius: tokens.radii.lg,
          paddingVertical: tokens.spacing.md,
          paddingHorizontal: tokens.spacing.sm,
          gap: tokens.spacing.sm,
          borderWidth: 1,
          flex: 1,
        },
      ]}
      onPress={onPress}
      activeOpacity={0.75}
    >
      <View
        style={[
          styles.quickPayIcon,
          {
            backgroundColor: color + '22',
            borderRadius: tokens.radii.md,
            width: 44,
            height: 44,
          },
        ]}
      >
        <Text style={[styles.quickPayIconText, { color }]}>{icon}</Text>
      </View>
      <Text
        style={{
          color: colors.text,
          fontSize: tokens.typography.sizes.sm,
          fontWeight: '500',
          textAlign: 'center',
        }}
      >
        {label}
      </Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  scroll: { flexGrow: 1 },
  greeting: { fontWeight: '500' },
  dateText: {},
  summaryRow: { flexDirection: 'row' },
  summaryCard: { flex: 1 },
  summaryLabel: { fontWeight: '500', textTransform: 'uppercase', letterSpacing: 0.5 },
  summaryAmount: {},
  sectionTitle: {},
  quickPayRow: { flexDirection: 'row' },
  quickPayBtn: { alignItems: 'center' },
  quickPayIcon: { alignItems: 'center', justifyContent: 'center' },
  quickPayIconText: { fontSize: 22, fontWeight: '700' },
  historyHint: {},
  historyHintText: { fontWeight: '500' },
});
