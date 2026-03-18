import React from 'react';
import { View, ViewStyle, StyleSheet } from 'react-native';
import { useTheme } from '../../theme/provider';

interface CardProps {
  children: React.ReactNode;
  style?: ViewStyle | ViewStyle[];
  padding?: 'sm' | 'md' | 'lg' | 'none';
}

export function Card({ children, style, padding = 'md' }: CardProps) {
  const { colors, tokens } = useTheme();

  const paddingValue =
    padding === 'none' ? 0 :
    padding === 'sm' ? tokens.spacing.sm :
    padding === 'lg' ? tokens.spacing.lg :
    tokens.spacing.md;

  return (
    <View
      style={[
        styles.card,
        {
          backgroundColor: colors.surface,
          borderColor: colors.border,
          borderRadius: tokens.radii.lg,
          padding: paddingValue,
        },
        style,
      ]}
    >
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderWidth: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 4,
    elevation: 2,
  },
});
