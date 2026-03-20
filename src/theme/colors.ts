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
  background: '#F6F1E8',
  surface: '#FFFCF7',
  text: '#171512',
  textSecondary: '#726A60',
  primary: '#165B5C',
  primaryText: '#F8F5EF',
  border: '#E5DBCC',
  success: '#2F8F6F',
  warning: '#C98B35',
  error: '#C15A49',
  tabBar: '#FFF9F0',
  tabBarBorder: '#E0D4C4',
  categoryColors: [
    '#165B5C',
    '#2F8F6F',
    '#C98B35',
    '#C15A49',
    '#7A6BE8',
    '#B86784',
    '#2A7EA0',
    '#6A8F42',
    '#D36F38',
    '#4E5BB0',
    '#338A83',
    '#A56A2A',
  ],
};

export const darkColors: ColorPalette = {
  background: '#111413',
  surface: '#181D1B',
  text: '#F5F0E6',
  textSecondary: '#A9A092',
  primary: '#86C7C0',
  primaryText: '#0D2324',
  border: '#2A312E',
  success: '#56B494',
  warning: '#E4B35C',
  error: '#E18170',
  tabBar: '#171B19',
  tabBarBorder: '#29302D',
  categoryColors: [
    '#86C7C0',
    '#56B494',
    '#E4B35C',
    '#E18170',
    '#B29BFF',
    '#E08FB0',
    '#59B7DA',
    '#9BC76A',
    '#EE9960',
    '#8694FF',
    '#5AC3BA',
    '#F0B77E',
  ],
};
