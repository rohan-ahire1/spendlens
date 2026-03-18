import { useEffect } from 'react';
import { View, Text, AppState, AppStateStatus, StyleSheet, ActivityIndicator } from 'react-native';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { ThemeProvider, useTheme } from '../theme/provider';
import { useDatabase } from '../hooks/useDatabase';
import { useTransactionFlowStore } from '../stores/transaction-flow';

function AppStateListener() {
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState: AppStateStatus) => {
      if (nextState === 'active') {
        useTransactionFlowStore.getState().onReturn();
      }
    });
    return () => subscription.remove();
  }, []);
  return null;
}

function LoadingScreen() {
  const { colors, tokens } = useTheme();
  return (
    <View style={[styles.loadingContainer, { backgroundColor: colors.background }]}>
      <ActivityIndicator size="large" color={colors.primary} />
      <Text style={[styles.loadingText, { color: colors.textSecondary, marginTop: tokens.spacing.md }]}>
        Loading…
      </Text>
    </View>
  );
}

function ErrorScreen({ message }: { message: string }) {
  const { colors, tokens } = useTheme();
  return (
    <View style={[styles.loadingContainer, { backgroundColor: colors.background }]}>
      <Text style={{ fontSize: 32 }}>⚠️</Text>
      <Text style={[styles.errorText, { color: colors.error, marginTop: tokens.spacing.sm }]}>
        Failed to initialize database
      </Text>
      <Text style={[styles.errorDetail, { color: colors.textSecondary, marginTop: tokens.spacing.xs }]}>
        {message}
      </Text>
    </View>
  );
}

function RootLayoutContent() {
  const { ready, error } = useDatabase();
  const { isDark } = useTheme();

  if (error) return <ErrorScreen message={error.message} />;
  if (!ready) return <LoadingScreen />;

  return (
    <>
      <StatusBar style={isDark ? 'light' : 'dark'} />
      <AppStateListener />
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="pay" options={{ presentation: 'modal', title: 'Pay', headerShown: true }} />
        <Stack.Screen
          name="category-prompt"
          options={{
            presentation: 'modal',
            title: 'Categorize',
            headerShown: true,
            gestureEnabled: false,
          }}
        />
      </Stack>
    </>
  );
}

export default function RootLayout() {
  return (
    <ThemeProvider>
      <RootLayoutContent />
    </ThemeProvider>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingText: {
    fontSize: 15,
  },
  errorText: {
    fontSize: 16,
    fontWeight: '600',
    textAlign: 'center',
  },
  errorDetail: {
    fontSize: 13,
    textAlign: 'center',
    paddingHorizontal: 32,
  },
});
