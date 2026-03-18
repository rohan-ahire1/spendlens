export type ColorPalette = {
  background: string;
  surface: string;
  text: string;
  textSecondary: string;
  primary: string;
  primaryText: string;
  border: string;
  success: string;
  warning: string;
  error: string;
  tabBar: string;
  tabBarBorder: string;
  categoryColors: string[];
};

export const lightColors: ColorPalette = {
  background: '#F5F7FA',
  surface: '#FFFFFF',
  text: '#1A1D23',
  textSecondary: '#6B7280',
  primary: '#4F62E8',
  primaryText: '#FFFFFF',
  border: '#E5E7EB',
  success: '#10B981',
  warning: '#F59E0B',
  error: '#EF4444',
  tabBar: '#FFFFFF',
  tabBarBorder: '#E5E7EB',
  categoryColors: [
    '#4F62E8',
    '#10B981',
    '#F59E0B',
    '#EF4444',
    '#8B5CF6',
    '#EC4899',
    '#06B6D4',
    '#84CC16',
    '#F97316',
    '#6366F1',
    '#14B8A6',
    '#FB923C',
  ],
};

export const darkColors: ColorPalette = {
  background: '#0F1117',
  surface: '#1C1F2E',
  text: '#F3F4F6',
  textSecondary: '#9CA3AF',
  primary: '#6674F0',
  primaryText: '#FFFFFF',
  border: '#2D3348',
  success: '#34D399',
  warning: '#FBBF24',
  error: '#F87171',
  tabBar: '#1C1F2E',
  tabBarBorder: '#2D3348',
  categoryColors: [
    '#6674F0',
    '#34D399',
    '#FBBF24',
    '#F87171',
    '#A78BFA',
    '#F472B6',
    '#22D3EE',
    '#A3E635',
    '#FB923C',
    '#818CF8',
    '#2DD4BF',
    '#FDBA74',
  ],
};
