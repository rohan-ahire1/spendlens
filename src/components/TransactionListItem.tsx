import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../theme/provider';
import { Transaction } from '../db/types';
import { formatINR } from '../utils/currency';

interface TransactionListItemProps {
  transaction: Transaction;
  categoryName?: string;
  categoryIcon?: string;
  categoryColor?: string;
}

function StatusBadge({ status }: { status: Transaction['status'] }) {
  const { colors, tokens } = useTheme();

  const config = {
    completed: { label: 'Paid', bg: colors.success + '22', color: colors.success },
    failed: { label: 'Failed', bg: colors.error + '22', color: colors.error },
    pending: { label: 'Pending', bg: colors.warning + '22', color: colors.warning },
    cancelled: { label: 'Cancelled', bg: colors.textSecondary + '22', color: colors.textSecondary },
  }[status] ?? { label: status, bg: colors.border, color: colors.textSecondary };

  return (
    <View
      style={[
        styles.badge,
        {
          backgroundColor: config.bg,
          borderRadius: tokens.radii.full,
          paddingHorizontal: tokens.spacing.sm,
          paddingVertical: 2,
        },
      ]}
    >
      <Text style={[styles.badgeText, { color: config.color, fontSize: 10 }]}>
        {config.label}
      </Text>
    </View>
  );
}

export function TransactionListItem({
  transaction,
  categoryName = 'Other',
  categoryIcon = '📦',
  categoryColor = '#888',
}: TransactionListItemProps) {
  const { colors, tokens } = useTheme();

  const recipientLabel =
    transaction.recipient_name ||
    transaction.recipient_upi_id ||
    transaction.recipient_phone ||
    'Unknown';

  const date = new Date(transaction.created_at);
  const timeStr = date.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });

  return (
    <View
      style={[
        styles.container,
        {
          paddingVertical: tokens.spacing.md,
          paddingHorizontal: tokens.spacing.md,
          borderBottomWidth: 1,
          borderBottomColor: colors.border,
        },
      ]}
    >
      {/* Category Icon */}
      <View
        style={[
          styles.iconCircle,
          {
            backgroundColor: categoryColor + '22',
            width: 44,
            height: 44,
            borderRadius: tokens.radii.full,
          },
        ]}
      >
        <Text style={styles.iconText}>{categoryIcon}</Text>
      </View>

      {/* Middle info */}
      <View style={styles.middle}>
        <Text
          style={[styles.recipientText, { color: colors.text, fontSize: tokens.typography.sizes.md }]}
          numberOfLines={1}
        >
          {recipientLabel}
        </Text>
        <View style={styles.metaRow}>
          <Text style={[styles.metaText, { color: colors.textSecondary, fontSize: tokens.typography.sizes.xs }]}>
            {categoryName}
          </Text>
          <Text style={[styles.dot, { color: colors.textSecondary }]}>·</Text>
          <Text style={[styles.metaText, { color: colors.textSecondary, fontSize: tokens.typography.sizes.xs }]}>
            {timeStr}
          </Text>
        </View>
      </View>

      {/* Right side */}
      <View style={styles.right}>
        <Text
          style={[
            styles.amountText,
            {
              color: transaction.status === 'failed' ? colors.textSecondary : colors.text,
              fontSize: tokens.typography.sizes.md,
              fontWeight: tokens.typography.weights.semibold,
              textDecorationLine: transaction.status === 'failed' ? 'line-through' : 'none',
            },
          ]}
        >
          {formatINR(transaction.amount_paise)}
        </Text>
        <StatusBadge status={transaction.status} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  iconCircle: {
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  iconText: {
    fontSize: 20,
  },
  middle: {
    flex: 1,
    gap: 3,
  },
  recipientText: {
    fontWeight: '500',
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  metaText: {},
  dot: {
    fontSize: 10,
  },
  right: {
    alignItems: 'flex-end',
    gap: 4,
    flexShrink: 0,
  },
  amountText: {},
  badge: {},
  badgeText: {
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.3,
  },
});
