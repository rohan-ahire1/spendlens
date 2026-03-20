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

function getPaymentMethodLabel(method: Transaction['payment_method']): string {
  switch (method) {
    case 'qr':
      return 'QR';
    case 'upi_id':
      return 'UPI ID';
    case 'phone':
      return 'Phone';
    case 'contact':
      return 'Contact';
    default:
      return 'UPI';
  }
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
          borderWidth: 1,
          borderColor: config.color + '18',
          borderRadius: tokens.radii.full,
          paddingHorizontal: tokens.spacing.sm,
          paddingVertical: 4,
        },
      ]}
    >
      <Text
        style={[
          styles.badgeText,
          {
            color: config.color,
            fontSize: 10,
            fontFamily: tokens.typography.families.accent,
          },
        ]}
      >
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
  const paymentMethodLabel = getPaymentMethodLabel(transaction.payment_method);

  const date = new Date(transaction.created_at);
  const timeStr = date.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });

  return (
    <View
      style={[
        styles.container,
        {
          backgroundColor: colors.surface,
          borderWidth: 1,
          borderColor: colors.border,
          borderRadius: tokens.radii.lg,
          paddingVertical: tokens.spacing.md,
          paddingHorizontal: tokens.spacing.md,
        },
      ]}
    >
      <View
        style={[
          styles.iconCircle,
          {
            backgroundColor: categoryColor + '16',
            width: 48,
            height: 48,
            borderRadius: tokens.radii.md,
          },
        ]}
      >
        <Text style={styles.iconText}>{categoryIcon}</Text>
      </View>

      <View style={styles.middle}>
        <Text
          style={[
            styles.recipientText,
            {
              color: colors.text,
              fontSize: tokens.typography.sizes.md,
              fontFamily: tokens.typography.families.accent,
            },
          ]}
          numberOfLines={1}
        >
          {recipientLabel}
        </Text>
        <View style={styles.metaRow}>
          <View
            style={[
              styles.metaPill,
              {
                backgroundColor: colors.background,
                borderColor: colors.border,
                borderRadius: tokens.radii.full,
              },
            ]}
          >
            <Text
              style={[
                styles.metaText,
                {
                  color: colors.textSecondary,
                  fontSize: tokens.typography.sizes.xs,
                  fontFamily: tokens.typography.families.accent,
                },
              ]}
            >
              {categoryName}
            </Text>
          </View>
          <Text
            style={[
              styles.metaText,
              {
                color: colors.textSecondary,
                fontSize: tokens.typography.sizes.xs,
                fontFamily: tokens.typography.families.body,
              },
            ]}
          >
            {paymentMethodLabel}
          </Text>
          <Text style={[styles.dot, { color: colors.textSecondary }]}>·</Text>
          <Text
            style={[
              styles.metaText,
              {
                color: colors.textSecondary,
                fontSize: tokens.typography.sizes.xs,
                fontFamily: tokens.typography.families.body,
              },
            ]}
          >
            {timeStr}
          </Text>
        </View>
      </View>

      <View style={styles.right}>
        <Text
          style={[
            styles.amountText,
            {
              color: transaction.status === 'failed' ? colors.textSecondary : colors.text,
              fontSize: tokens.typography.sizes.md,
              fontWeight: tokens.typography.weights.semibold,
              fontFamily: tokens.typography.families.accent,
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
    gap: 14,
  },
  iconCircle: {
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  iconText: {
    fontSize: 22,
  },
  middle: {
    flex: 1,
    gap: 6,
  },
  recipientText: {
    fontWeight: '600',
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 6,
  },
  metaPill: {
    borderWidth: 1,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  metaText: {
    lineHeight: 16,
  },
  dot: {
    fontSize: 10,
  },
  right: {
    alignItems: 'flex-end',
    gap: 8,
    flexShrink: 0,
  },
  amountText: {},
  badge: {},
  badgeText: {
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
});
