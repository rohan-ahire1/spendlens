import { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  RefreshControl,
  Alert,
  TextInput,
  StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../../theme/provider';
import { useTransactionFlowStore } from '../../stores/transaction-flow';
import { getByMonth } from '../../db/repositories/transactions';
import { formatINR } from '../../utils/currency';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
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
  const paymentMethod = useTransactionFlowStore((s) => s.paymentMethod);
  const paymentRequest = useTransactionFlowStore((s) => s.paymentRequest);
  const launchResult = useTransactionFlowStore((s) => s.launchResult);
  const confirmPayment = useTransactionFlowStore((s) => s.confirm);
  const { suggestion, isAutoApplied } = useCategorization(
    flowStatus === 'categorizing' ? transactionId : null
  );

  const [todayTotal, setTodayTotal] = useState(0);
  const [monthTotal, setMonthTotal] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const [showAmountSheet, setShowAmountSheet] = useState(false);
  const [completedAmountInput, setCompletedAmountInput] = useState('');
  const [savingCompletedAmount, setSavingCompletedAmount] = useState(false);

  const today = new Date();
  const todayStr = today.toISOString().split('T')[0];
  const requiresAmountOnCompletion =
    paymentMethod === 'qr' &&
    (!paymentRequest?.amount || paymentRequest.amount <= 0);

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

  useEffect(() => {
    if (flowStatus !== 'confirming') {
      setShowAmountSheet(false);
      setCompletedAmountInput('');
      setSavingCompletedAmount(false);
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

  const presentPaymentStatusPrompt = useCallback(() => {
    Alert.alert(
      'Payment Status',
      'Did your payment go through?',
      [
        {
          text: 'Yes, Paid',
          onPress: () => {
            if (requiresAmountOnCompletion) {
              setCompletedAmountInput('');
              setShowAmountSheet(true);
              return;
            }
            void confirmPayment('completed');
          },
        },
        {
          text: 'No, Failed',
          style: 'destructive',
          onPress: () => {
            void confirmPayment('failed');
          },
        },
        {
          text: 'Cancel',
          style: 'cancel',
          onPress: () => {
            void confirmPayment('cancelled');
          },
        },
      ],
      { cancelable: false }
    );
  }, [confirmPayment, requiresAmountOnCompletion]);

  useEffect(() => {
    if (flowStatus !== 'confirming') return;

    if (launchResult?.status === 'completed') {
      if (requiresAmountOnCompletion && launchResult.amount === undefined) {
        if (!showAmountSheet) {
          setCompletedAmountInput('');
          setShowAmountSheet(true);
        }
        return;
      }

      void confirmPayment('completed', launchResult.amount);
      return;
    }

    if (launchResult?.status === 'failed') {
      void confirmPayment('failed');
      return;
    }

    if (launchResult?.status === 'cancelled') {
      void confirmPayment('cancelled');
      return;
    }

    if (!showAmountSheet) {
      presentPaymentStatusPrompt();
    }
  }, [
    confirmPayment,
    flowStatus,
    launchResult,
    presentPaymentStatusPrompt,
    requiresAmountOnCompletion,
    showAmountSheet,
  ]);

  async function handleCompletedAmountSave() {
    const enteredAmount = parseFloat(completedAmountInput);
    if (isNaN(enteredAmount) || enteredAmount <= 0) {
      Alert.alert('Invalid Amount', 'Please enter the amount you actually paid.');
      return;
    }

    setSavingCompletedAmount(true);
    try {
      await confirmPayment('completed', enteredAmount);
      setShowAmountSheet(false);
      setCompletedAmountInput('');
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Could not save the payment amount.';
      Alert.alert('Save Error', message);
      setSavingCompletedAmount(false);
    }
  }

  const monthName = today.toLocaleDateString('en-IN', { month: 'long' });

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: colors.background }]}>
      <View style={[styles.orb, { backgroundColor: colors.primary + '12', top: -80, right: -40 }]} />
      <View style={[styles.orb, { backgroundColor: colors.warning + '14', width: 160, height: 160, top: 140, left: -60 }]} />
      <ScrollView
        contentContainerStyle={[
          styles.scroll,
          {
            paddingHorizontal: tokens.spacing.lg,
            paddingTop: tokens.spacing.lg,
            paddingBottom: tokens.spacing.xxl + 84,
          },
        ]}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={colors.primary}
          />
        }
      >
        <View style={{ marginBottom: tokens.spacing.lg }}>
          <View
            style={[
              styles.brandBadge,
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
                fontWeight: tokens.typography.weights.semibold,
                fontFamily: tokens.typography.families.accent,
                letterSpacing: 1.1,
                textTransform: 'uppercase',
              }}
            >
              SpendLens
            </Text>
          </View>
          <Text
            style={[
              styles.greeting,
              {
                color: colors.textSecondary,
                fontSize: tokens.typography.sizes.sm,
                marginTop: tokens.spacing.md,
                fontFamily: tokens.typography.families.accent,
              },
            ]}
          >
            {getGreeting()}
          </Text>
          <Text
            style={[
              styles.dateText,
              {
                color: colors.text,
                fontSize: tokens.typography.sizes.xxxl,
                fontWeight: tokens.typography.weights.bold,
                marginTop: 4,
                fontFamily: tokens.typography.families.display,
              },
            ]}
          >
            Your money, quieter.
          </Text>
          <Text
            style={{
              color: colors.textSecondary,
              fontSize: tokens.typography.sizes.md,
              marginTop: tokens.spacing.sm,
              lineHeight: 24,
              maxWidth: 320,
            }}
          >
            {formatDate(today)}. Keep payments fast and tracking calm.
          </Text>
        </View>

        <Card
          style={{
            marginBottom: tokens.spacing.lg,
            padding: tokens.spacing.lg,
            backgroundColor: colors.text,
            borderColor: colors.text,
          }}
          padding="none"
        >
          <Text
            style={{
              color: colors.primaryText,
              opacity: 0.7,
              fontSize: tokens.typography.sizes.sm,
              fontFamily: tokens.typography.families.accent,
            }}
          >
            {monthName} overview
          </Text>
          <Text
            style={{
              color: colors.primaryText,
              fontSize: tokens.typography.sizes.xxxl,
              fontWeight: tokens.typography.weights.bold,
              fontFamily: tokens.typography.families.display,
              marginTop: tokens.spacing.sm,
            }}
          >
            {formatINR(monthTotal)}
          </Text>
          <Text
            style={{
              color: colors.primaryText,
              opacity: 0.7,
              fontSize: tokens.typography.sizes.sm,
              marginTop: tokens.spacing.xs,
            }}
          >
            Tracked spend this month
          </Text>

          <View style={[styles.heroMetaRow, { marginTop: tokens.spacing.lg, gap: tokens.spacing.sm }]}>
            <View
              style={[
                styles.heroMetaCard,
                {
                  backgroundColor: 'rgba(255,255,255,0.08)',
                  borderRadius: tokens.radii.md,
                  padding: tokens.spacing.md,
                },
              ]}
            >
              <Text style={[styles.heroMetaLabel, { color: colors.primaryText }]}>Today</Text>
              <Text
                style={{
                  color: colors.primaryText,
                  fontSize: tokens.typography.sizes.lg,
                  fontWeight: tokens.typography.weights.semibold,
                  marginTop: 6,
                  fontFamily: tokens.typography.families.accent,
                }}
              >
                {formatINR(todayTotal)}
              </Text>
            </View>
            <View
              style={[
                styles.heroMetaCard,
                {
                  backgroundColor: 'rgba(255,255,255,0.08)',
                  borderRadius: tokens.radii.md,
                  padding: tokens.spacing.md,
                },
              ]}
            >
              <Text style={[styles.heroMetaLabel, { color: colors.primaryText }]}>Flow</Text>
              <Text
                style={{
                  color: colors.primaryText,
                  fontSize: tokens.typography.sizes.lg,
                  fontWeight: tokens.typography.weights.semibold,
                  marginTop: 6,
                  fontFamily: tokens.typography.families.accent,
                }}
              >
                Instant UPI
              </Text>
            </View>
          </View>
        </Card>

        <View style={[styles.sectionHeader, { marginBottom: tokens.spacing.md }]}>
          <View>
            <Text
              style={{
                color: colors.text,
                fontSize: tokens.typography.sizes.xl,
                fontFamily: tokens.typography.families.display,
                fontWeight: tokens.typography.weights.bold,
              }}
            >
              Quick actions
            </Text>
            <Text
              style={{
                color: colors.textSecondary,
                fontSize: tokens.typography.sizes.sm,
                marginTop: 4,
              }}
            >
              Open the most common flows with one tap.
            </Text>
          </View>
        </View>

        <View style={[styles.quickPayGrid, { gap: tokens.spacing.sm, marginBottom: tokens.spacing.xl }]}>
          <QuickPayButton
            icon="qr-code-outline"
            label="Scan QR"
            description="Merchant and storefront codes"
            color={colors.primary}
            onPress={() => router.push('/scan')}
            tone="solid"
          />
          <QuickPayButton
            icon="paper-plane-outline"
            label="Pay UPI"
            description="Manual UPI ID payments"
            color={colors.success}
            onPress={() => router.push('/pay')}
          />
          <QuickPayButton
            icon="people-outline"
            label="Pay Contact"
            description="Coming soon"
            color={colors.warning}
            onPress={() => Alert.alert('Coming Soon', 'Pay by contact will be available soon.')}
          />
        </View>

        <TouchableOpacity
          style={[
            styles.historyHint,
            {
              backgroundColor: colors.surface,
              borderColor: colors.border,
              borderRadius: tokens.radii.lg,
              padding: tokens.spacing.lg,
              borderWidth: 1,
            },
          ]}
          onPress={() => router.push('/(tabs)/history')}
          activeOpacity={0.8}
        >
          <View style={styles.historyHintRow}>
            <View
              style={[
                styles.historyHintIcon,
                {
                  backgroundColor: colors.primary + '14',
                  borderRadius: tokens.radii.md,
                },
              ]}
            >
              <Ionicons name="receipt-outline" size={20} color={colors.primary} />
            </View>
            <View style={{ flex: 1 }}>
              <Text
                style={{
                  color: colors.text,
                  fontSize: tokens.typography.sizes.lg,
                  fontWeight: tokens.typography.weights.semibold,
                  fontFamily: tokens.typography.families.accent,
                }}
              >
                View transaction history
              </Text>
              <Text
                style={{
                  color: colors.textSecondary,
                  fontSize: tokens.typography.sizes.sm,
                  marginTop: 4,
                }}
              >
                Review completed, failed, and cancelled payments in one place.
              </Text>
            </View>
            <Ionicons name="arrow-forward" size={20} color={colors.textSecondary} />
          </View>
        </TouchableOpacity>
      </ScrollView>

      {showAmountSheet && (
        <View style={styles.sheetOverlay}>
          <Card
            style={[
              styles.sheetCard,
              {
                backgroundColor: colors.surface,
                borderColor: colors.border,
                borderRadius: tokens.radii.lg,
                padding: tokens.spacing.lg,
              },
            ]}
            padding="none"
          >
            <View style={styles.sheetHeader}>
              <View
                style={[
                  styles.sheetIcon,
                  {
                    backgroundColor: colors.primary + '12',
                    borderRadius: tokens.radii.md,
                  },
                ]}
              >
                <Ionicons name="wallet-outline" size={18} color={colors.primary} />
              </View>
              <View style={{ flex: 1 }}>
                <Text
                  style={{
                    color: colors.text,
                    fontSize: tokens.typography.sizes.lg,
                    fontFamily: tokens.typography.families.accent,
                  }}
                >
                  What amount was paid?
                </Text>
                <Text
                  style={{
                    color: colors.textSecondary,
                    fontSize: tokens.typography.sizes.sm,
                    marginTop: 2,
                  }}
                >
                  The QR did not include an amount, so SpendLens needs it now for tracking.
                </Text>
              </View>
            </View>

            <Text
              style={{
                color: colors.text,
                fontSize: tokens.typography.sizes.sm,
                marginTop: tokens.spacing.md,
                fontFamily: tokens.typography.families.accent,
              }}
            >
              {paymentRequest?.payeeName ?? paymentRequest?.payeeAddress ?? 'Scanned QR payment'}
            </Text>

            <View
              style={[
                styles.completedAmountField,
                {
                  backgroundColor: colors.background,
                  borderColor: colors.border,
                  borderRadius: tokens.radii.lg,
                  marginTop: tokens.spacing.md,
                  paddingHorizontal: tokens.spacing.md,
                  paddingVertical: tokens.spacing.md,
                },
              ]}
            >
              <Text
                style={{
                  color: colors.primary,
                  fontSize: tokens.typography.sizes.xxl,
                  fontFamily: tokens.typography.families.display,
                }}
              >
                Rs
              </Text>
              <TextInput
                style={[
                  styles.completedAmountInput,
                  {
                    color: colors.text,
                    fontSize: tokens.typography.sizes.xxxl,
                    fontFamily: tokens.typography.families.display,
                  },
                ]}
                value={completedAmountInput}
                onChangeText={setCompletedAmountInput}
                keyboardType="decimal-pad"
                placeholder="0.00"
                placeholderTextColor={colors.textSecondary}
                autoFocus
              />
            </View>

            <View style={[styles.sheetActions, { gap: tokens.spacing.sm, marginTop: tokens.spacing.md }]}>
              <Button
                label="Back"
                variant="ghost"
                onPress={() => {
                  setShowAmountSheet(false);
                  setCompletedAmountInput('');
                }}
                disabled={savingCompletedAmount}
                style={styles.sheetButton}
              />
              <Button
                label="Save amount"
                onPress={handleCompletedAmountSave}
                loading={savingCompletedAmount}
                disabled={!completedAmountInput.trim()}
                style={styles.sheetButton}
              />
            </View>
          </Card>
        </View>
      )}
    </SafeAreaView>
  );
}

function QuickPayButton({
  icon,
  label,
  description,
  color,
  onPress,
  tone = 'default',
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  description: string;
  color: string;
  onPress: () => void;
  tone?: 'default' | 'solid';
}) {
  const { colors, tokens } = useTheme();
  return (
    <TouchableOpacity
      style={[
        styles.quickPayBtn,
        {
          backgroundColor: tone === 'solid' ? colors.text : colors.surface,
          borderColor: tone === 'solid' ? colors.text : colors.border,
          borderRadius: tokens.radii.lg,
          paddingVertical: tokens.spacing.lg,
          paddingHorizontal: tokens.spacing.md,
          gap: tokens.spacing.sm,
          borderWidth: 1,
        },
      ]}
      onPress={onPress}
      activeOpacity={0.85}
    >
      <View
        style={[
          styles.quickPayIcon,
          {
            backgroundColor: tone === 'solid' ? 'rgba(255,255,255,0.08)' : color + '16',
            borderRadius: tokens.radii.md,
            width: 50,
            height: 50,
          },
        ]}
      >
        <Ionicons
          name={icon}
          size={22}
          color={tone === 'solid' ? colors.primaryText : color}
        />
      </View>
      <View style={{ gap: 2 }}>
        <Text
          style={{
            color: tone === 'solid' ? colors.primaryText : colors.text,
            fontSize: tokens.typography.sizes.md,
            fontWeight: tokens.typography.weights.semibold,
            fontFamily: tokens.typography.families.accent,
          }}
        >
          {label}
        </Text>
        <Text
          style={{
            color: tone === 'solid' ? colors.primaryText : colors.textSecondary,
            opacity: tone === 'solid' ? 0.72 : 1,
            fontSize: tokens.typography.sizes.sm,
            lineHeight: 20,
          }}
        >
          {description}
        </Text>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  scroll: { flexGrow: 1 },
  orb: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 220,
  },
  brandBadge: {
    alignSelf: 'flex-start',
    borderWidth: 1,
  },
  greeting: { fontWeight: '500' },
  dateText: {},
  heroMetaRow: { flexDirection: 'row' },
  heroMetaCard: { flex: 1 },
  heroMetaLabel: {
    fontSize: 12,
    opacity: 0.72,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  sectionHeader: {},
  quickPayGrid: { gap: 12 },
  quickPayBtn: { gap: 12 },
  quickPayIcon: { alignItems: 'center', justifyContent: 'center' },
  historyHint: {},
  historyHintRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  historyHintIcon: {
    width: 48,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sheetOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 20,
    backgroundColor: 'rgba(17,20,19,0.2)',
  },
  sheetCard: {
    width: '100%',
    borderWidth: 1,
  },
  sheetHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  sheetIcon: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  completedAmountField: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 12,
    borderWidth: 1,
  },
  completedAmountInput: {
    flex: 1,
    paddingVertical: 0,
  },
  sheetActions: {
    flexDirection: 'row',
  },
  sheetButton: {
    flex: 1,
  },
});
