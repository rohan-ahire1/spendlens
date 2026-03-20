import { Platform } from 'react-native';

export const spacing = {
  xs: 6,
  sm: 10,
  md: 16,
  lg: 24,
  xl: 32,
  xxl: 40,
} as const;

export const typography = {
  sizes: {
    xs: 12,
    sm: 14,
    md: 16,
    lg: 18,
    xl: 22,
    xxl: 28,
    xxxl: 38,
  },
  weights: {
    regular: '400' as const,
    medium: '500' as const,
    semibold: '600' as const,
    bold: '700' as const,
  },
  families: {
    display: Platform.select({
      ios: 'Georgia',
      android: 'serif',
      default: 'serif',
    }),
    body: Platform.select({
      ios: 'System',
      android: 'sans-serif',
      default: 'sans-serif',
    }),
    accent: Platform.select({
      ios: 'Avenir Next',
      android: 'sans-serif-medium',
      default: 'sans-serif',
    }),
  },
  lineHeights: {
    tight: 1.2,
    normal: 1.5,
    relaxed: 1.75,
  },
} as const;

export const radii = {
  sm: 10,
  md: 16,
  lg: 24,
  xl: 32,
  full: 9999,
} as const;

export type Spacing = typeof spacing;
export type Typography = typeof typography;
export type Radii = typeof radii;

export type Tokens = {
  spacing: Spacing;
  typography: Typography;
  radii: Radii;
};

export const tokens: Tokens = {
  spacing,
  typography,
  radii,
};
