import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../theme/provider';
import { formatINR } from '../../utils/currency';

interface CategoryBarProps {
  icon: string;
  name: string;
  amount: number;
  percentage: number;
  color: string;
}

export function CategoryBar({ icon, name, amount, percentage, color }: CategoryBarProps) {
  const { colors, tokens } = useTheme();

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.left}>
          <Text style={[styles.icon, { fontSize: tokens.typography.sizes.lg }]}>{icon}</Text>
          <Text style={[styles.name, { color: colors.text, fontSize: tokens.typography.sizes.sm }]}>
            {name}
          </Text>
        </View>
        <Text style={[styles.amount, { color: colors.text, fontSize: tokens.typography.sizes.sm }]}>
          {formatINR(amount)}
        </Text>
      </View>
      <View style={[styles.track, { backgroundColor: colors.border, borderRadius: tokens.radii.full }]}>
        <View
          style={[
            styles.fill,
            {
              width: `${Math.round(percentage)}%` as `${number}%`,
              backgroundColor: color,
              borderRadius: tokens.radii.full,
            },
          ]}
        />
      </View>
      <Text style={[styles.pct, { color: colors.textSecondary, fontSize: tokens.typography.sizes.xs }]}>
        {Math.round(percentage)}%
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 4,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  left: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    flex: 1,
  },
  icon: {},
  name: {
    fontWeight: '500',
    flex: 1,
  },
  amount: {
    fontWeight: '600',
  },
  track: {
    height: 6,
    overflow: 'hidden',
  },
  fill: {
    height: 6,
  },
  pct: {},
});
