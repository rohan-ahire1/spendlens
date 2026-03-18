import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { useTheme } from '../../theme/provider';
import { Card } from '../../components/ui/Card';

export default function SettingsScreen() {
  const { colors, tokens } = useTheme();
  const router = useRouter();

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={[styles.content, { padding: tokens.spacing.md, gap: tokens.spacing.md }]}>
        <Text style={[styles.title, { color: colors.text, fontSize: tokens.typography.sizes.xxl }]}>
          Settings
        </Text>

        <Card padding="none">
          <TouchableOpacity
            onPress={() => router.push('/budget')}
            style={[styles.row, { padding: tokens.spacing.md, borderBottomWidth: 0 }]}
          >
            <View style={styles.rowLeft}>
              <Text style={{ fontSize: 20 }}>💰</Text>
              <View>
                <Text style={[{ color: colors.text, fontSize: tokens.typography.sizes.md, fontWeight: '500' }]}>
                  Budgets
                </Text>
                <Text style={[{ color: colors.textSecondary, fontSize: tokens.typography.sizes.sm }]}>
                  Set monthly spending limits
                </Text>
              </View>
            </View>
            <Text style={[{ color: colors.textSecondary, fontSize: 18 }]}>›</Text>
          </TouchableOpacity>
        </Card>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { flex: 1 },
  title: { fontWeight: '700' },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  rowLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    flex: 1,
  },
});
