import { useState } from 'react';
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
import { useTheme } from '../theme/provider';
import { useTransactionFlowStore } from '../stores/transaction-flow';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';

function isValidUpiId(value: string): boolean {
  return /^[a-zA-Z0-9._-]+@[a-zA-Z0-9]+$/.test(value.trim());
}

function isValidPhone(value: string): boolean {
  return /^[6-9]\d{9}$/.test(value.trim());
}

export default function PayScreen() {
  const { colors, tokens } = useTheme();
  const router = useRouter();
  const { capture, launch, status } = useTransactionFlowStore();

  const [upiInput, setUpiInput] = useState('');
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [loading, setLoading] = useState(false);

  const isPhone = isValidPhone(upiInput.trim());
  const isUpi = isValidUpiId(upiInput.trim());
  const inputValid = isPhone || isUpi;

  async function handlePay() {
    if (!inputValid) {
      Alert.alert('Invalid Input', 'Please enter a valid UPI ID (e.g. name@upi) or 10-digit phone number.');
      return;
    }

    const amountVal = parseFloat(amount);
    if (isNaN(amountVal) || amountVal <= 0) {
      Alert.alert('Invalid Amount', 'Please enter a valid amount greater than ₹0.');
      return;
    }

    const payeeAddress = isPhone
      ? `${upiInput.trim()}@paytm`
      : upiInput.trim();

    setLoading(true);
    try {
      await capture(
        {
          payeeAddress,
          amount: amountVal,
          transactionNote: note.trim() || undefined,
        },
        isPhone ? 'phone' : 'upi_id'
      );
      await launch();
      router.back();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Something went wrong.';
      Alert.alert('Payment Error', msg);
      setLoading(false);
    }
  }

  const inputStyle = {
    backgroundColor: colors.background,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: tokens.radii.md,
    color: colors.text,
    paddingHorizontal: tokens.spacing.md,
    paddingVertical: tokens.spacing.sm + 4,
    fontSize: tokens.typography.sizes.md,
  };

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: colors.background }]} edges={['bottom']}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={88}
      >
        <ScrollView
          contentContainerStyle={[styles.scroll, { padding: tokens.spacing.lg }]}
          keyboardShouldPersistTaps="handled"
        >
          <Card style={{ marginBottom: tokens.spacing.lg }}>
            {/* UPI / Phone */}
            <View style={{ marginBottom: tokens.spacing.md }}>
              <Text style={[styles.label, { color: colors.textSecondary, marginBottom: tokens.spacing.xs, fontSize: tokens.typography.sizes.sm }]}>
                UPI ID or Phone Number
              </Text>
              <TextInput
                style={inputStyle}
                placeholder="name@upi or 9876543210"
                placeholderTextColor={colors.textSecondary}
                value={upiInput}
                onChangeText={setUpiInput}
                autoCapitalize="none"
                autoCorrect={false}
                keyboardType="email-address"
                returnKeyType="next"
              />
              {upiInput.length > 0 && (
                <Text
                  style={{
                    marginTop: tokens.spacing.xs,
                    fontSize: tokens.typography.sizes.xs,
                    color: inputValid ? colors.success : colors.error,
                  }}
                >
                  {inputValid
                    ? isPhone ? '✓ Valid phone number' : '✓ Valid UPI ID'
                    : 'Enter a valid UPI ID (name@upi) or phone number'}
                </Text>
              )}
            </View>

            {/* Amount */}
            <View style={{ marginBottom: tokens.spacing.md }}>
              <Text style={[styles.label, { color: colors.textSecondary, marginBottom: tokens.spacing.xs, fontSize: tokens.typography.sizes.sm }]}>
                Amount (₹)
              </Text>
              <View style={styles.amountRow}>
                <View style={[styles.currencyBadge, { backgroundColor: colors.primary + '22', borderRadius: tokens.radii.sm }]}>
                  <Text style={[styles.currencySymbol, { color: colors.primary, fontSize: tokens.typography.sizes.lg }]}>₹</Text>
                </View>
                <TextInput
                  style={[inputStyle, styles.amountInput]}
                  placeholder="0.00"
                  placeholderTextColor={colors.textSecondary}
                  value={amount}
                  onChangeText={setAmount}
                  keyboardType="decimal-pad"
                  returnKeyType="next"
                />
              </View>
            </View>

            {/* Note */}
            <View>
              <Text style={[styles.label, { color: colors.textSecondary, marginBottom: tokens.spacing.xs, fontSize: tokens.typography.sizes.sm }]}>
                Note (optional)
              </Text>
              <TextInput
                style={inputStyle}
                placeholder="What's this for?"
                placeholderTextColor={colors.textSecondary}
                value={note}
                onChangeText={setNote}
                maxLength={100}
                returnKeyType="done"
              />
            </View>
          </Card>

          {/* UPI app quick picks */}
          <Card style={{ marginBottom: tokens.spacing.lg }}>
            <Text style={[styles.label, { color: colors.textSecondary, fontSize: tokens.typography.sizes.sm, marginBottom: tokens.spacing.sm }]}>
              Pay via
            </Text>
            <View style={styles.appRow}>
              {(['Any UPI App', 'PhonePe', 'GPay', 'Paytm'] as const).map((appName) => (
                <TouchableOpacity
                  key={appName}
                  style={[
                    styles.appChip,
                    {
                      backgroundColor: colors.background,
                      borderColor: colors.border,
                      borderRadius: tokens.radii.full,
                      paddingHorizontal: tokens.spacing.md,
                      paddingVertical: tokens.spacing.xs + 2,
                    },
                  ]}
                  onPress={() => {}} // future: select UPI app
                >
                  <Text style={{ color: colors.text, fontSize: tokens.typography.sizes.sm }}>
                    {appName}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </Card>

          <Button
            label="Pay Now"
            onPress={handlePay}
            loading={loading}
            disabled={!inputValid || !amount}
            size="lg"
            style={{ marginBottom: tokens.spacing.md }}
          />

          <Button
            label="Cancel"
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
  label: { fontWeight: '500' },
  amountRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  currencyBadge: { padding: 10 },
  currencySymbol: { fontWeight: '700' },
  amountInput: { flex: 1 },
  appRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  appChip: { borderWidth: 1 },
});
