import React, { createContext, useContext } from 'react';
import { useColorScheme } from 'react-native';
import { lightColors, darkColors, ColorPalette } from './colors';
import { tokens, Tokens } from './tokens';

type ThemeContextValue = {
  colors: ColorPalette;
  tokens: Tokens;
  isDark: boolean;
};

const ThemeContext = createContext<ThemeContextValue>({
  colors: lightColors,
  tokens,
  isDark: false,
});

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const colorScheme = useColorScheme();
  const isDark = colorScheme === 'dark';
  const colors = isDark ? darkColors : lightColors;

  return (
    <ThemeContext.Provider value={{ colors, tokens, isDark }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext);
}
