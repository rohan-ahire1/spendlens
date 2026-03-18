export const spacing = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
} as const;

export const typography = {
  sizes: {
    xs: 11,
    sm: 13,
    md: 15,
    lg: 17,
    xl: 20,
    xxl: 24,
    xxxl: 32,
  },
  weights: {
    regular: '400' as const,
    medium: '500' as const,
    semibold: '600' as const,
    bold: '700' as const,
  },
  lineHeights: {
    tight: 1.2,
    normal: 1.5,
    relaxed: 1.75,
  },
} as const;

export const radii = {
  sm: 8,
  md: 12,
  lg: 16,
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
