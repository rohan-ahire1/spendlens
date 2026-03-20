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
      <View style={styles.header}>
        <View style={styles.left}>
          <View
            style={[
              styles.iconWrap,
              {
                backgroundColor: color + '14',
                borderRadius: tokens.radii.md,
              },
            ]}
          >
            <Text style={[styles.icon, { fontSize: tokens.typography.sizes.lg }]}>{icon}</Text>
          </View>
          <View style={styles.labelBlock}>
            <Text
              style={[
                styles.name,
                {
                  color: colors.text,
                  fontSize: tokens.typography.sizes.sm,
                  fontFamily: tokens.typography.families.accent,
                },
              ]}
            >
              {name}
            </Text>
            <Text
              style={[
                styles.pct,
                {
                  color: colors.textSecondary,
                  fontSize: tokens.typography.sizes.xs,
                },
              ]}
            >
              {Math.round(percentage)}% of total
            </Text>
          </View>
        </View>
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
          {formatINR(amount)}
        </Text>
      </View>
      <View
        style={[
          styles.track,
          {
            backgroundColor: colors.border,
            borderRadius: tokens.radii.full,
            marginTop: tokens.spacing.sm,
          },
        ]}
      >
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
      <View style={styles.footRow}>
        <Text style={[styles.subtleText, { color: colors.textSecondary, fontSize: tokens.typography.sizes.xs }]}>
          Track share
        </Text>
        <Text style={[styles.subtleText, { color: color, fontSize: tokens.typography.sizes.xs }]}>
          {Math.round(percentage)}%
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 4,
    borderWidth: 1,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  left: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    flex: 1,
  },
  labelBlock: {
    flex: 1,
    gap: 2,
  },
  iconWrap: {
    width: 38,
    height: 38,
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: {},
  name: {
    fontWeight: '600',
    flex: 1,
  },
  amount: {
    fontWeight: '600',
  },
  track: {
    height: 8,
    overflow: 'hidden',
  },
  fill: {
    height: 8,
  },
  footRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 6,
  },
  subtleText: {},
  pct: {},
});
