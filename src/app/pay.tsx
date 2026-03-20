import { ReactNode, useEffect, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  Alert,
  StyleSheet,
  TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../theme/provider';
import { useTransactionFlowStore } from '../stores/transaction-flow';
import { useAppStore } from '../stores/app';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { getInstalledUpiApps } from '../services/upi/intent';
import { UPI_APPS, UPI_APP_LABELS, UpiAppKey } from '../services/upi/types';
import { generateUpiTransactionRef } from '../utils/id';

function isValidUpiId(value: string): boolean {
  return /^[a-zA-Z0-9._-]{2,256}@[a-zA-Z0-9.-]{2,64}$/.test(value.trim());
}

function isValidPhone(value: string): boolean {
  return /^[6-9]\d{9}$/.test(value.trim());
}

function getAppKeyFromPackage(pkg: string | null): UpiAppKey | null {
  if (!pkg) return null;

  const match = (Object.entries(UPI_APPS) as Array<[UpiAppKey, string]>)
    .find(([, packageName]) => packageName === pkg);

  return match?.[0] ?? null;
}

function Field({
  label,
  hint,
  icon,
  children,
}: {
  label: string;
  hint?: string;
  icon: keyof typeof Ionicons.glyphMap;
  children: ReactNode;
}) {
  const { colors, tokens } = useTheme();

  return (
    <View style={{ gap: tokens.spacing.xs }}>
      <View style={styles.fieldHeader}>
        <Ionicons name={icon} size={15} color={colors.textSecondary} />
        <Text
          style={{
            color: colors.text,
            fontSize: tokens.typography.sizes.sm,
            fontFamily: tokens.typography.families.accent,
          }}
        >
          {label}
        </Text>
      </View>
      {children}
      {hint ? (
        <Text
          style={{
            color: colors.textSecondary,
            fontSize: tokens.typography.sizes.xs,
            lineHeight: 18,
          }}
        >
          {hint}
        </Text>
      ) : null}
    </View>
  );
}

export default function PayScreen() {
  const { colors, tokens } = useTheme();
  const router = useRouter();
  const { capture, launch } = useTransactionFlowStore();
  const preferredUpiApp = useAppStore((s) => s.preferredUpiApp);

  const [upiInput, setUpiInput] = useState('');
  const [amount, setAmount] = useState('');
  const [payeeName, setPayeeName] = useState('');
  const [note, setNote] = useState('');
  const [loading, setLoading] = useState(false);
  const [installedApps, setInstalledApps] = useState<UpiAppKey[]>([]);
  const [selectedApp, setSelectedApp] = useState<UpiAppKey | null>(getAppKeyFromPackage(preferredUpiApp));

  const trimmedInput = upiInput.trim();
  const trimmedAmount = amount.trim();
  const isPhone = isValidPhone(trimmedInput);
  const isUpi = isValidUpiId(trimmedInput);
  const inputValid = isUpi;
  const selectedPackage = selectedApp ? UPI_APPS[selectedApp] : null;
  const availableApps = installedApps.length > 0
    ? installedApps
    : (Object.keys(UPI_APPS) as UpiAppKey[]);

  useEffect(() => {
    getInstalledUpiApps().then(setInstalledApps).catch(() => setInstalledApps([]));
  }, []);

  useEffect(() => {
    const preferredKey = getAppKeyFromPackage(preferredUpiApp);
    setSelectedApp((current) => current ?? preferredKey);
  }, [preferredUpiApp]);

  useEffect(() => {
    if (selectedApp && installedApps.length > 0 && !installedApps.includes(selectedApp)) {
      setSelectedApp(null);
    }
  }, [installedApps, selectedApp]);

  async function handlePay() {
    if (isPhone) {
      Alert.alert(
        'Phone Number Not Supported',
        'Direct phone-number pay is not reliable through UPI deep links yet. Please enter a UPI ID or scan the merchant QR.',
      );
      return;
    }

    if (!isUpi) {
      Alert.alert('Invalid UPI ID', 'Please enter a valid UPI ID such as name@bank.');
      return;
    }

    const amountVal = parseFloat(amount);
    if (isNaN(amountVal) || amountVal <= 0) {
      Alert.alert('Invalid Amount', 'Please enter a valid amount greater than Rs 0.');
      return;
    }

    setLoading(true);
    try {
      await capture(
        {
          payeeAddress: trimmedInput,
          payeeName: payeeName.trim() || undefined,
          amount: amountVal,
          currencyCode: 'INR',
          transactionNote: note.trim() || undefined,
          transactionRef: generateUpiTransactionRef(),
        },
        'upi_id',
        { upiAppPackage: selectedPackage }
      );
      await launch(selectedPackage ?? undefined);
      router.back();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Something went wrong.';
      Alert.alert('Payment Error', msg);
      setLoading(false);
    }
  }

  const inputShellStyle = {
    backgroundColor: colors.background,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: tokens.radii.md,
    paddingHorizontal: tokens.spacing.md,
    paddingVertical: tokens.spacing.sm + 2,
  } as const;

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: colors.background }]} edges={['bottom']}>
      <View style={[styles.orb, { backgroundColor: colors.primary + '10', top: -64, right: -56 }]} />
      <View style={[styles.orb, { backgroundColor: colors.warning + '10', width: 170, height: 170, bottom: 120, left: -70 }]} />
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={88}
      >
        <ScrollView
          contentContainerStyle={[
            styles.scroll,
            {
              paddingHorizontal: tokens.spacing.lg,
              paddingTop: tokens.spacing.lg,
              paddingBottom: tokens.spacing.xxl,
            },
          ]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <View style={{ marginBottom: tokens.spacing.lg }}>
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
                Manual payment
              </Text>
            </View>
            <Text
              style={[
                styles.title,
                {
                  color: colors.text,
                  fontSize: tokens.typography.sizes.xxxl,
                  marginTop: tokens.spacing.md,
                  fontFamily: tokens.typography.families.display,
                },
              ]}
            >
              Pay a UPI ID.
            </Text>
            <Text
              style={{
                color: colors.textSecondary,
                fontSize: tokens.typography.sizes.md,
                lineHeight: 24,
                marginTop: tokens.spacing.sm,
                maxWidth: 340,
              }}
            >
              Keep it direct. Add a UPI ID, choose the app you trust most, and jump straight into payment.
            </Text>
          </View>

          <Card
            style={{
              marginBottom: tokens.spacing.lg,
              backgroundColor: colors.text,
              borderColor: colors.text,
              padding: tokens.spacing.lg,
            }}
            padding="none"
          >
            <View style={styles.heroHeader}>
              <View
                style={[
                  styles.heroIcon,
                  {
                    backgroundColor: 'rgba(255,255,255,0.08)',
                    borderRadius: tokens.radii.md,
                  },
                ]}
              >
                <Ionicons name="paper-plane-outline" size={22} color={colors.primaryText} />
              </View>
              <View style={{ flex: 1 }}>
                <Text
                  style={{
                    color: colors.primaryText,
                    fontSize: tokens.typography.sizes.sm,
                    opacity: 0.72,
                    fontFamily: tokens.typography.families.accent,
                  }}
                >
                  Launch preference
                </Text>
                <Text
                  style={{
                    color: colors.primaryText,
                    fontSize: tokens.typography.sizes.lg,
                    fontFamily: tokens.typography.families.accent,
                    marginTop: 4,
                  }}
                >
                  {selectedApp ? UPI_APP_LABELS[selectedApp] : 'Any installed UPI app'}
                </Text>
              </View>
            </View>

            <View style={[styles.heroMetaRow, { marginTop: tokens.spacing.lg }]}>
              <View style={[styles.heroMetaCard, { backgroundColor: 'rgba(255,255,255,0.08)', borderRadius: tokens.radii.md }]}>
                <Text style={[styles.heroMetaLabel, { color: colors.primaryText }]}>Reliability</Text>
                <Text style={[styles.heroMetaValue, { color: colors.primaryText, fontFamily: tokens.typography.families.accent }]}>
                  Best with CRED
                </Text>
              </View>
              <View style={[styles.heroMetaCard, { backgroundColor: 'rgba(255,255,255,0.08)', borderRadius: tokens.radii.md }]}>
                <Text style={[styles.heroMetaLabel, { color: colors.primaryText }]}>Format</Text>
                <Text style={[styles.heroMetaValue, { color: colors.primaryText, fontFamily: tokens.typography.families.accent }]}>
                  name@bank
                </Text>
              </View>
            </View>
          </Card>

          <Card style={{ marginBottom: tokens.spacing.lg, gap: tokens.spacing.lg }}>
            <Field
              label="UPI ID"
              hint={
                trimmedInput.length > 0
                  ? isUpi
                    ? 'Looks valid and ready to use.'
                    : isPhone
                      ? 'Phone numbers are not supported in this screen. Use a UPI ID or scan a QR.'
                      : 'Use a valid UPI ID such as name@bank.'
                  : 'Enter the exact UPI address you want to pay.'
              }
              icon={isUpi ? 'checkmark-circle-outline' : 'at-outline'}
            >
              <View style={[inputShellStyle, styles.fieldShell]}>
                <TextInput
                  style={[
                    styles.input,
                    {
                      color: colors.text,
                      fontSize: tokens.typography.sizes.md,
                      fontFamily: tokens.typography.families.body,
                    },
                  ]}
                  placeholder="name@bank"
                  placeholderTextColor={colors.textSecondary}
                  value={upiInput}
                  onChangeText={setUpiInput}
                  autoCapitalize="none"
                  autoCorrect={false}
                  keyboardType="email-address"
                  returnKeyType="next"
                />
              </View>
            </Field>

            {isPhone ? (
              <View
                style={[
                  styles.inlineNote,
                  {
                    backgroundColor: colors.warning + '12',
                    borderColor: colors.warning + '26',
                    borderRadius: tokens.radii.md,
                    padding: tokens.spacing.md,
                  },
                ]}
              >
                <Ionicons name="alert-circle-outline" size={18} color={colors.warning} />
                <Text
                  style={{
                    color: colors.text,
                    flex: 1,
                    lineHeight: 20,
                    fontSize: tokens.typography.sizes.sm,
                  }}
                >
                  This looks like a phone number. Manual phone-number UPI pay is still unreliable here, so QR is the safer route.
                </Text>
              </View>
            ) : null}

            <Field
              label="Payee name"
              hint="Optional, but helpful for cleaner history later."
              icon="person-outline"
            >
              <View style={inputShellStyle}>
                <TextInput
                  style={[
                    styles.input,
                    {
                      color: colors.text,
                      fontSize: tokens.typography.sizes.md,
                    },
                  ]}
                  placeholder="Store or person name"
                  placeholderTextColor={colors.textSecondary}
                  value={payeeName}
                  onChangeText={setPayeeName}
                  autoCapitalize="words"
                  returnKeyType="next"
                />
              </View>
            </Field>

            <Field
              label="Note"
              hint="Optional. A short note helps with recall when you look back later."
              icon="create-outline"
            >
              <View style={inputShellStyle}>
                <TextInput
                  style={[
                    styles.input,
                    {
                      color: colors.text,
                      fontSize: tokens.typography.sizes.md,
                    },
                  ]}
                  placeholder="What's this for?"
                  placeholderTextColor={colors.textSecondary}
                  value={note}
                  onChangeText={setNote}
                  maxLength={100}
                  returnKeyType="done"
                />
              </View>
            </Field>
          </Card>

          <Card style={{ marginBottom: tokens.spacing.lg, gap: tokens.spacing.lg }}>
            <Field
              label="Amount"
              hint="Enter the amount you want the UPI app to open with."
              icon="cash-outline"
            >
              <View
                style={[
                  styles.amountShell,
                  {
                    backgroundColor: colors.background,
                    borderColor: colors.border,
                    borderRadius: tokens.radii.lg,
                    paddingHorizontal: tokens.spacing.md,
                    paddingVertical: tokens.spacing.md,
                  },
                ]}
              >
                <Text
                  style={{
                    color: colors.primary,
                    fontSize: tokens.typography.sizes.xxl,
                    fontFamily: tokens.typography.families.display,
                  }}
                >
                  Rs
                </Text>
                <TextInput
                  style={[
                    styles.amountInput,
                    {
                      color: colors.text,
                      fontSize: tokens.typography.sizes.xxxl,
                      fontFamily: tokens.typography.families.display,
                    },
                  ]}
                  placeholder="0.00"
                  placeholderTextColor={colors.textSecondary}
                  value={amount}
                  onChangeText={setAmount}
                  keyboardType="decimal-pad"
                  returnKeyType="done"
                />
              </View>
            </Field>

            <Field
              label="Pay via"
              hint="CRED and PhonePe tend to behave more consistently with manual deep links."
              icon="apps-outline"
            >
              <View style={[styles.appRow, { gap: tokens.spacing.sm }]}>
                <TouchableOpacity
                  style={[
                    styles.appChip,
                    {
                      backgroundColor: selectedApp === null ? colors.primary + '16' : colors.background,
                      borderColor: selectedApp === null ? colors.primary : colors.border,
                      borderRadius: tokens.radii.full,
                      paddingHorizontal: tokens.spacing.md,
                      paddingVertical: tokens.spacing.sm,
                    },
                  ]}
                  onPress={() => setSelectedApp(null)}
                  activeOpacity={0.8}
                >
                  <Text
                    style={{
                      color: selectedApp === null ? colors.primary : colors.text,
                      fontSize: tokens.typography.sizes.sm,
                      fontFamily: tokens.typography.families.accent,
                    }}
                  >
                    Any UPI App
                  </Text>
                </TouchableOpacity>
                {availableApps.map((appKey) => (
                  <TouchableOpacity
                    key={appKey}
                    style={[
                      styles.appChip,
                      {
                        backgroundColor: selectedApp === appKey ? colors.primary + '16' : colors.background,
                        borderColor: selectedApp === appKey ? colors.primary : colors.border,
                        borderRadius: tokens.radii.full,
                        paddingHorizontal: tokens.spacing.md,
                        paddingVertical: tokens.spacing.sm,
                      },
                    ]}
                    onPress={() => setSelectedApp(appKey)}
                    activeOpacity={0.8}
                  >
                    <Text
                      style={{
                        color: selectedApp === appKey ? colors.primary : colors.text,
                        fontSize: tokens.typography.sizes.sm,
                        fontFamily: tokens.typography.families.accent,
                      }}
                    >
                      {UPI_APP_LABELS[appKey]}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </Field>
          </Card>

          <Button
            label={trimmedAmount ? 'Continue to pay' : 'Enter amount to continue'}
            onPress={handlePay}
            loading={loading}
            disabled={!inputValid || !trimmedAmount}
            size="lg"
            style={{ marginBottom: tokens.spacing.md }}
          />

          <Button
            label="Back"
            variant="ghost"
            onPress={() => router.back()}
            disabled={loading}
          />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  flex: { flex: 1 },
  scroll: { flexGrow: 1 },
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
  title: {
    fontWeight: '700',
  },
  heroHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  heroIcon: {
    width: 52,
    height: 52,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroMetaRow: {
    flexDirection: 'row',
    gap: 10,
  },
  heroMetaCard: {
    flex: 1,
    padding: 14,
  },
  heroMetaLabel: {
    fontSize: 12,
    opacity: 0.72,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  heroMetaValue: {
    fontSize: 16,
    marginTop: 6,
  },
  fieldHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  fieldShell: {
    justifyContent: 'center',
  },
  input: {
    paddingVertical: 0,
  },
  inlineNote: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
  },
  amountShell: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 12,
    borderWidth: 1,
  },
  amountInput: {
    flex: 1,
    paddingVertical: 0,
  },
  appRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  appChip: {
    borderWidth: 1,
  },
});
