import { useEffect, useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  FlatList,
  ActivityIndicator,
  Alert,
  StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter, useLocalSearchParams } from 'expo-router';
import { useTheme } from '../theme/provider';
import { useTransactionFlowStore } from '../stores/transaction-flow';
import { getVisible } from '../db/repositories/categories';
import { getById } from '../db/repositories/transactions';
import { upsertRule } from '../db/repositories/user-rules';
import type { Category } from '../db/types';

export default function CategoryPromptScreen() {
  const { colors, tokens } = useTheme();
  const router = useRouter();
  const { suggested } = useLocalSearchParams<{ suggested?: string }>();
  const { setCategoryAndFinish, status, transactionId } = useTransactionFlowStore();

  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [selecting, setSelecting] = useState<string | null>(null);

  useEffect(() => {
    getVisible()
      .then(setCategories)
      .catch(() => Alert.alert('Error', 'Could not load categories'))
      .finally(() => setLoading(false));
  }, []);

  // If status moved away from 'categorizing', dismiss
  useEffect(() => {
    if (status === 'done' || status === 'idle') {
      router.back();
    }
  }, [status]);

  async function handleSelect(categoryId: string) {
    setSelecting(categoryId);
    try {
      // Learn from user's choice for future auto-categorization
      if (transactionId) {
        const tx = await getById(transactionId);
        if (tx?.recipient_name) {
          await upsertRule('merchant_name', tx.recipient_name.toLowerCase(), categoryId);
        }
        if (tx?.recipient_upi_id) {
          await upsertRule('upi_id', tx.recipient_upi_id.toLowerCase(), categoryId);
        }
      }
      await setCategoryAndFinish(categoryId);
      router.back();
    } catch {
      Alert.alert('Error', 'Could not save category');
      setSelecting(null);
    }
  }

  async function handleSkip() {
    const other = categories.find((c) => c.name.toLowerCase() === 'other');
    if (other) {
      await handleSelect(other.id);
    } else if (categories.length > 0) {
      await handleSelect(categories[categories.length - 1].id);
    } else {
      router.back();
    }
  }

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: colors.background }]} edges={['bottom']}>
      <View style={[styles.header, { paddingHorizontal: tokens.spacing.lg, paddingTop: tokens.spacing.lg, paddingBottom: tokens.spacing.md }]}>
        <Text style={[styles.title, { color: colors.text, fontSize: tokens.typography.sizes.xl, fontWeight: tokens.typography.weights.bold }]}>
          What was this for?
        </Text>
        <Text style={[styles.subtitle, { color: colors.textSecondary, fontSize: tokens.typography.sizes.sm, marginTop: tokens.spacing.xs }]}>
          {suggested ? 'We think this might be the right one — tap to confirm or pick another' : 'Pick a category to track your spending'}
        </Text>
      </View>

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.primary} />
        </View>
      ) : (
        <FlatList
          data={categories}
          keyExtractor={(item) => item.id}
          numColumns={3}
          contentContainerStyle={[styles.grid, { padding: tokens.spacing.md }]}
          columnWrapperStyle={{ gap: tokens.spacing.sm }}
          ItemSeparatorComponent={() => <View style={{ height: tokens.spacing.sm }} />}
          renderItem={({ item }) => {
            const isSelecting = selecting === item.id;
            const isSuggested = suggested === item.id;
            return (
              <TouchableOpacity
                style={[
                  styles.categoryItem,
                  {
                    flex: 1,
                    backgroundColor: isSuggested ? colors.primary + '15' : colors.surface,
                    borderColor: isSuggested ? colors.primary : colors.border,
                    borderRadius: tokens.radii.lg,
                    padding: tokens.spacing.md,
                    opacity: selecting && !isSelecting ? 0.5 : 1,
                  },
                ]}
                onPress={() => handleSelect(item.id)}
                disabled={!!selecting}
                activeOpacity={0.7}
              >
                <View
                  style={[
                    styles.iconWrap,
                    {
                      backgroundColor: (item.color || '#888') + '22',
                      borderRadius: tokens.radii.md,
                      width: 48,
                      height: 48,
                      marginBottom: tokens.spacing.sm,
                    },
                  ]}
                >
                  {isSelecting ? (
                    <ActivityIndicator color={item.color || colors.primary} size="small" />
                  ) : (
                    <Text style={styles.iconText}>{item.icon}</Text>
                  )}
                </View>
                <Text
                  style={[
                    styles.categoryName,
                    {
                      color: colors.text,
                      fontSize: tokens.typography.sizes.sm,
                      fontWeight: tokens.typography.weights.medium,
                    },
                  ]}
                  numberOfLines={1}
                >
                  {item.name}
                </Text>
              </TouchableOpacity>
            );
          }}
        />
      )}

      <View style={[styles.skipRow, { padding: tokens.spacing.lg, borderTopWidth: 1, borderTopColor: colors.border }]}>
        <TouchableOpacity onPress={handleSkip} disabled={!!selecting} activeOpacity={0.7}>
          <Text
            style={{
              color: colors.textSecondary,
              fontSize: tokens.typography.sizes.md,
              fontWeight: tokens.typography.weights.medium,
              textDecorationLine: 'underline',
            }}
          >
            Skip for now
          </Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: {},
  title: {},
  subtitle: {},
  centerFill: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  grid: { flexGrow: 1 },
  categoryItem: {
    alignItems: 'center',
    borderWidth: 1,
  },
  iconWrap: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconText: { fontSize: 22 },
  categoryName: { textAlign: 'center' },
  skipRow: { alignItems: 'center' },
});
