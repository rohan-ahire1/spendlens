import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../theme/provider';
import { formatINR } from '../../utils/currency';

interface BudgetProgressBarProps {
  spent: number;
  budget: number;
  label?: string;
}

export function BudgetProgressBar({ spent, budget, label }: BudgetProgressBarProps) {
  const { colors, tokens } = useTheme();

  const ratio = budget > 0 ? Math.min(spent / budget, 1) : 0;
  const percentage = Math.round(ratio * 100);

  const barColor =
    ratio >= 1
      ? colors.error
      : ratio >= 0.8
      ? colors.warning
      : colors.success;

  return (
    <View
      style={[
        styles.container,
        {
          backgroundColor: colors.background,
          borderColor: colors.border,
          borderRadius: tokens.radii.md,
          padding: tokens.spacing.md,
        },
      ]}
    >
      {label ? (
        <Text
          style={[
            styles.label,
            {
              color: colors.textSecondary,
              fontSize: tokens.typography.sizes.sm,
              fontFamily: tokens.typography.families.accent,
            },
          ]}
        >
          {label}
        </Text>
      ) : null}
      <View style={[styles.track, { backgroundColor: colors.border, borderRadius: tokens.radii.full }]}>
        <View
          style={[
            styles.fill,
            {
              width: `${percentage}%` as `${number}%`,
              backgroundColor: barColor,
              borderRadius: tokens.radii.full,
            },
          ]}
        />
      </View>
      <View style={styles.row}>
        <Text
          style={[
            styles.amount,
            {
              color: colors.text,
              fontSize: tokens.typography.sizes.sm,
              fontFamily: tokens.typography.families.accent,
            },
          ]}
        >
          {formatINR(spent)}
        </Text>
        <Text style={[styles.budget, { color: colors.textSecondary, fontSize: tokens.typography.sizes.sm }]}>
          {formatINR(budget)}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 8,
    borderWidth: 1,
  },
  label: {
    marginBottom: 2,
  },
  track: {
    height: 8,
    overflow: 'hidden',
  },
  fill: {
    height: 8,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  amount: {
    fontWeight: '500',
  },
  budget: {},
});
