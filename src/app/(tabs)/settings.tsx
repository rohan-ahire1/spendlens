import { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../../theme/provider';
import { Card } from '../../components/ui/Card';
import { useAppStore } from '../../stores/app';
import { getInstalledUpiApps } from '../../services/upi/intent';
import { UPI_APPS, UPI_APP_LABELS, UpiAppKey } from '../../services/upi/types';

export default function SettingsScreen() {
  const { colors, tokens } = useTheme();
  const router = useRouter();
  const preferredUpiApp = useAppStore((s) => s.preferredUpiApp);
  const setPreferredUpiApp = useAppStore((s) => s.setPreferredUpiApp);
  const [installedApps, setInstalledApps] = useState<UpiAppKey[]>([]);
  const availableApps = installedApps.length > 0
    ? installedApps
    : (Object.keys(UPI_APPS) as UpiAppKey[]);

  useEffect(() => {
    getInstalledUpiApps().then(setInstalledApps).catch(() => setInstalledApps([]));
  }, []);

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={[styles.orb, { backgroundColor: colors.primary + '10', top: -90, right: -60 }]} />
      <ScrollView
        contentContainerStyle={{
          paddingHorizontal: tokens.spacing.lg,
          paddingTop: tokens.spacing.lg,
          paddingBottom: tokens.spacing.xxl + 88,
          gap: tokens.spacing.lg,
        }}
        showsVerticalScrollIndicator={false}
      >
        <View>
          <View
            style={[
              styles.eyebrowBadge,
              {
                backgroundColor: colors.surface,
                borderColor: colors.border,
                borderRadius: tokens.radii.full,
                paddingHorizontal: tokens.spacing.md,
                paddingVertical: tokens.spacing.xs,
              },
            ]}
          >
            <Text
              style={{
                color: colors.primary,
                fontSize: tokens.typography.sizes.xs,
                fontFamily: tokens.typography.families.accent,
                letterSpacing: 1,
                textTransform: 'uppercase',
              }}
            >
              Preferences
            </Text>
          </View>
          <Text
            style={{
              color: colors.text,
              fontSize: tokens.typography.sizes.xxxl,
              fontFamily: tokens.typography.families.display,
              marginTop: tokens.spacing.md,
            }}
          >
            Tune the flow.
          </Text>
          <Text
            style={{
              color: colors.textSecondary,
              fontSize: tokens.typography.sizes.md,
              lineHeight: 24,
              marginTop: tokens.spacing.sm,
              maxWidth: 330,
            }}
          >
            Keep launch behavior predictable, set your preferred UPI app, and shape how the app feels day to day.
          </Text>
        </View>

        <Card
          style={{
            backgroundColor: colors.text,
            borderColor: colors.text,
            padding: tokens.spacing.lg,
          }}
          padding="none"
        >
          <TouchableOpacity
            onPress={() => router.push('/budget')}
            style={styles.primaryCard}
            activeOpacity={0.85}
          >
            <View
              style={[
                styles.primaryIcon,
                {
                  backgroundColor: 'rgba(255,255,255,0.08)',
                  borderRadius: tokens.radii.md,
                },
              ]}
            >
              <Ionicons name="wallet-outline" size={22} color={colors.primaryText} />
            </View>
            <View style={{ flex: 1 }}>
              <Text
                style={{
                  color: colors.primaryText,
                  fontSize: tokens.typography.sizes.xs,
                  fontFamily: tokens.typography.families.accent,
                  opacity: 0.72,
                  letterSpacing: 0.9,
                  textTransform: 'uppercase',
                }}
              >
                Budgeting
              </Text>
              <Text
                style={{
                  color: colors.primaryText,
                  fontSize: tokens.typography.sizes.xl,
                  fontFamily: tokens.typography.families.display,
                  marginTop: 6,
                }}
              >
                Set monthly limits.
              </Text>
              <Text
                style={{
                  color: colors.primaryText,
                  opacity: 0.72,
                  fontSize: tokens.typography.sizes.sm,
                  marginTop: tokens.spacing.sm,
                  lineHeight: 20,
                }}
              >
                Add spending guardrails for categories that matter most.
              </Text>
            </View>
            <Ionicons name="arrow-forward" size={20} color={colors.primaryText} />
          </TouchableOpacity>
        </Card>

        <Card style={{ gap: tokens.spacing.md }}>
          <View style={styles.sectionHeader}>
            <View>
              <Text
                style={{
                  color: colors.text,
                  fontSize: tokens.typography.sizes.xl,
                  fontFamily: tokens.typography.families.display,
                }}
              >
                Preferred UPI app
              </Text>
              <Text
                style={{
                  color: colors.textSecondary,
                  fontSize: tokens.typography.sizes.sm,
                  lineHeight: 20,
                  marginTop: 4,
                }}
              >
                SpendLens will try this app first for manual UPI payments and scanned QR launches.
              </Text>
            </View>
          </View>

          <TouchableOpacity
            style={[
              styles.choiceRow,
              {
                borderColor: preferredUpiApp === null ? colors.primary : colors.border,
                backgroundColor: preferredUpiApp === null ? colors.primary + '12' : colors.background,
                borderRadius: tokens.radii.lg,
                padding: tokens.spacing.md,
              },
            ]}
            onPress={() => setPreferredUpiApp(null)}
            activeOpacity={0.8}
          >
            <View style={styles.choiceLabelRow}>
              <View
                style={[
                  styles.choiceIcon,
                  {
                    backgroundColor: preferredUpiApp === null ? colors.primary + '16' : colors.surface,
                    borderRadius: tokens.radii.md,
                  },
                ]}
              >
                <Ionicons
                  name="apps-outline"
                  size={18}
                  color={preferredUpiApp === null ? colors.primary : colors.textSecondary}
                />
              </View>
              <View style={{ flex: 1 }}>
                <Text
                  style={{
                    color: colors.text,
                    fontSize: tokens.typography.sizes.md,
                    fontFamily: tokens.typography.families.accent,
                  }}
                >
                  Any UPI App
                </Text>
                <Text
                  style={{
                    color: colors.textSecondary,
                    fontSize: tokens.typography.sizes.sm,
                    marginTop: 3,
                  }}
                >
                  Use the system default or app chooser each time.
                </Text>
              </View>
            </View>
            <Ionicons
              name={preferredUpiApp === null ? 'radio-button-on' : 'radio-button-off'}
              size={20}
              color={preferredUpiApp === null ? colors.primary : colors.textSecondary}
            />
          </TouchableOpacity>

          {availableApps.map((appKey) => {
            const pkg = UPI_APPS[appKey];
            const selected = preferredUpiApp === pkg;

            return (
              <TouchableOpacity
                key={appKey}
                style={[
                  styles.choiceRow,
                  {
                    borderColor: selected ? colors.primary : colors.border,
                    backgroundColor: selected ? colors.primary + '12' : colors.background,
                    borderRadius: tokens.radii.lg,
                    padding: tokens.spacing.md,
                  },
                ]}
                onPress={() => setPreferredUpiApp(selected ? null : pkg)}
                activeOpacity={0.8}
              >
                <View style={styles.choiceLabelRow}>
                  <View
                    style={[
                      styles.choiceIcon,
                      {
                        backgroundColor: selected ? colors.primary + '16' : colors.surface,
                        borderRadius: tokens.radii.md,
                      },
                    ]}
                  >
                    <Ionicons
                      name="phone-portrait-outline"
                      size={18}
                      color={selected ? colors.primary : colors.textSecondary}
                    />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text
                      style={{
                        color: colors.text,
                        fontSize: tokens.typography.sizes.md,
                        fontFamily: tokens.typography.families.accent,
                      }}
                    >
                      {UPI_APP_LABELS[appKey]}
                    </Text>
                    <Text
                      style={{
                        color: colors.textSecondary,
                        fontSize: tokens.typography.sizes.sm,
                        marginTop: 3,
                      }}
                    >
                      {selected ? 'Used first when launching payments.' : 'Tap to prefer this app.'}
                    </Text>
                  </View>
                </View>
                <Ionicons
                  name={selected ? 'radio-button-on' : 'radio-button-off'}
                  size={20}
                  color={selected ? colors.primary : colors.textSecondary}
                />
              </TouchableOpacity>
            );
          })}

          <View
            style={[
              styles.noteRow,
              {
                backgroundColor: colors.background,
                borderColor: colors.border,
                borderRadius: tokens.radii.md,
                padding: tokens.spacing.md,
              },
            ]}
          >
            <Ionicons name="information-circle-outline" size={18} color={colors.textSecondary} />
            <Text
              style={{
                color: colors.textSecondary,
                flex: 1,
                fontSize: tokens.typography.sizes.sm,
                lineHeight: 20,
              }}
            >
              {installedApps.length === 0
                ? 'Installed-app detection was inconclusive, so the full supported list is shown.'
                : 'Only apps detected on this phone are shown above.'}
            </Text>
          </View>
        </Card>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  orb: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 220,
  },
  eyebrowBadge: {
    alignSelf: 'flex-start',
    borderWidth: 1,
  },
  primaryCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  primaryIcon: {
    width: 52,
    height: 52,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sectionHeader: {},
  choiceRow: {
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  choiceLabelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    flex: 1,
  },
  choiceIcon: {
    width: 42,
    height: 42,
    alignItems: 'center',
    justifyContent: 'center',
  },
  noteRow: {
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
  },
});
