import React from 'react';
import {
  TouchableOpacity,
  Text,
  ActivityIndicator,
  StyleSheet,
  ViewStyle,
  TextStyle,
} from 'react-native';
import { useTheme } from '../../theme/provider';

interface ButtonProps {
  onPress: () => void;
  label: string;
  variant?: 'primary' | 'secondary' | 'ghost';
  loading?: boolean;
  disabled?: boolean;
  style?: ViewStyle;
  textStyle?: TextStyle;
  size?: 'sm' | 'md' | 'lg';
}

export function Button({
  onPress,
  label,
  variant = 'primary',
  loading = false,
  disabled = false,
  style,
  textStyle,
  size = 'md',
}: ButtonProps) {
  const { colors, tokens } = useTheme();

  const isDisabled = disabled || loading;

  const bgColor =
    variant === 'primary' ? colors.primary :
    variant === 'secondary' ? colors.surface :
    'transparent';

  const txtColor =
    variant === 'primary' ? colors.primaryText :
    variant === 'secondary' ? colors.text : colors.textSecondary;

  const borderColor =
    variant === 'ghost' ? 'transparent' : colors.border;

  const paddingVertical =
    size === 'sm' ? tokens.spacing.sm :
    size === 'lg' ? tokens.spacing.md + 4 :
    tokens.spacing.md;

  const shadowStyle = variant === 'primary'
    ? {
        shadowColor: colors.text,
        shadowOffset: { width: 0, height: 10 },
        shadowOpacity: 0.12,
        shadowRadius: 18,
        elevation: 5,
      }
    : null;

  return (
    <TouchableOpacity
      onPress={onPress}
      disabled={isDisabled}
      activeOpacity={0.75}
      style={[
        styles.button,
        {
          backgroundColor: bgColor,
          borderColor,
          borderRadius: tokens.radii.full,
          paddingVertical,
          paddingHorizontal: tokens.spacing.lg,
          opacity: isDisabled ? 0.5 : 1,
        },
        shadowStyle,
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={txtColor} size="small" />
      ) : (
        <Text
          style={[
            styles.label,
            {
              color: txtColor,
              fontSize: size === 'sm' ? tokens.typography.sizes.sm : tokens.typography.sizes.md,
              fontWeight: tokens.typography.weights.semibold,
              fontFamily: tokens.typography.families.accent,
              letterSpacing: 0.2,
            },
            textStyle,
          ]}
        >
          {label}
        </Text>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  button: {
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    flexDirection: 'row',
  },
  label: {
    textAlign: 'center',
  },
});
