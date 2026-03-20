import { useState, useRef, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Alert,
  Dimensions,
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../theme/provider';
import { parseUpiUri } from '../services/upi/parser';
import type { UpiPaymentRequest } from '../services/upi/types';
import { useAppStore } from '../stores/app';
import { useTransactionFlowStore } from '../stores/transaction-flow';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';

const SCAN_FRAME_SIZE = 260;
const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

interface PendingScan {
  request: UpiPaymentRequest;
  rawUri: string;
}

export default function ScanScreen() {
  const { colors, tokens } = useTheme();
  const router = useRouter();
  const preferredUpiApp = useAppStore((s) => s.preferredUpiApp);
  const capture = useTransactionFlowStore((s) => s.capture);
  const launch = useTransactionFlowStore((s) => s.launch);
  const [permission, requestPermission] = useCameraPermissions();
  const [torch, setTorch] = useState(false);
  const scannedRef = useRef(false);

  const resetScanner = useCallback(() => {
    scannedRef.current = false;
  }, []);

  const startQrPayment = useCallback(
    async (pending: PendingScan) => {
      try {
        await capture(
          {
            ...pending.request,
            currencyCode: pending.request.currencyCode ?? 'INR',
          },
          'qr',
          { rawUpiUri: pending.rawUri, upiAppPackage: preferredUpiApp },
        );
        await launch(preferredUpiApp ?? undefined);
        router.dismiss();
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'Could not launch a UPI app.';
        Alert.alert('Payment Error', msg);
        resetScanner();
      }
    },
    [capture, launch, preferredUpiApp, resetScanner, router],
  );

  const handleBarCodeScanned = useCallback(
    ({ data }: { data: string }) => {
      if (scannedRef.current) return;

      const parsed = parseUpiUri(data);
      if (!parsed) {
        Alert.alert('Invalid QR', 'Not a UPI QR code. Please scan a valid UPI payment QR.');
        return;
      }

      scannedRef.current = true;
      const nextPending = { request: parsed, rawUri: data };
      void startQrPayment(nextPending);
    },
    [startQrPayment],
  );

  if (!permission) {
    return (
      <View style={[styles.centered, { backgroundColor: colors.background }]}>
        <Text style={{ color: colors.textSecondary }}>Requesting camera permission…</Text>
      </View>
    );
  }

  if (!permission.granted) {
    return (
      <View style={[styles.centered, { backgroundColor: colors.background, padding: tokens.spacing.xl }]}>
        <Card
          style={{
            width: '100%',
            maxWidth: 360,
            alignItems: 'center',
            padding: tokens.spacing.xl,
          }}
        >
          <View
            style={[
              styles.permissionIcon,
              {
                backgroundColor: colors.primary + '12',
                borderRadius: tokens.radii.full,
              },
            ]}
          >
            <Ionicons name="scan-outline" size={26} color={colors.primary} />
          </View>
          <Text
            style={[
              styles.permissionTitle,
              {
                color: colors.text,
                fontSize: tokens.typography.sizes.xl,
                fontFamily: tokens.typography.families.display,
                marginTop: tokens.spacing.md,
              },
            ]}
          >
            Camera access required
          </Text>
          <Text
            style={[
              styles.permissionBody,
              {
                color: colors.textSecondary,
                marginTop: tokens.spacing.sm,
                fontSize: tokens.typography.sizes.sm,
              },
            ]}
          >
            SpendLens needs camera access to scan merchant and personal UPI QR codes.
          </Text>
          <Button
            label="Allow camera"
            onPress={requestPermission}
            style={{ marginTop: tokens.spacing.lg, width: '100%' }}
          />
          <Button
            label="Back"
            variant="ghost"
            onPress={() => router.dismiss()}
            style={{ marginTop: tokens.spacing.sm }}
          />
        </Card>
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <CameraView
        style={StyleSheet.absoluteFill}
        facing="back"
        enableTorch={torch}
        barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
        onBarcodeScanned={handleBarCodeScanned}
      />
      {/* Dark overlay — four rectangles around the scan frame */}
      {/* Top */}
      <View
        style={[
          styles.overlayPiece,
          {
            top: 0,
            left: 0,
            right: 0,
            height: (SCREEN_HEIGHT - SCAN_FRAME_SIZE) / 2,
            backgroundColor: 'rgba(0,0,0,0.6)',
          },
        ]}
      />
      {/* Bottom */}
      <View
        style={[
          styles.overlayPiece,
          {
            bottom: 0,
            left: 0,
            right: 0,
            height: (SCREEN_HEIGHT - SCAN_FRAME_SIZE) / 2,
            backgroundColor: 'rgba(0,0,0,0.6)',
          },
        ]}
      />
      {/* Left */}
      <View
        style={[
          styles.overlayPiece,
          {
            top: (SCREEN_HEIGHT - SCAN_FRAME_SIZE) / 2,
            left: 0,
            width: (SCREEN_WIDTH - SCAN_FRAME_SIZE) / 2,
            height: SCAN_FRAME_SIZE,
            backgroundColor: 'rgba(0,0,0,0.6)',
          },
        ]}
      />
      {/* Right */}
      <View
        style={[
          styles.overlayPiece,
          {
            top: (SCREEN_HEIGHT - SCAN_FRAME_SIZE) / 2,
            right: 0,
            width: (SCREEN_WIDTH - SCAN_FRAME_SIZE) / 2,
            height: SCAN_FRAME_SIZE,
            backgroundColor: 'rgba(0,0,0,0.6)',
          },
        ]}
      />

      {/* Scan frame with corner brackets */}
      <View
        style={[
          styles.scanFrame,
          {
            top: (SCREEN_HEIGHT - SCAN_FRAME_SIZE) / 2,
            left: (SCREEN_WIDTH - SCAN_FRAME_SIZE) / 2,
            width: SCAN_FRAME_SIZE,
            height: SCAN_FRAME_SIZE,
          },
        ]}
      >
        {/* Corners */}
        <View style={[styles.corner, styles.cornerTL, { borderColor: colors.primary }]} />
        <View style={[styles.corner, styles.cornerTR, { borderColor: colors.primary }]} />
        <View style={[styles.corner, styles.cornerBL, { borderColor: colors.primary }]} />
        <View style={[styles.corner, styles.cornerBR, { borderColor: colors.primary }]} />
      </View>

      <View
        style={[
          styles.topInfoCard,
          {
            top: 64,
            left: tokens.spacing.lg,
            right: tokens.spacing.lg + 56,
            backgroundColor: 'rgba(11, 12, 12, 0.58)',
            borderRadius: tokens.radii.lg,
            padding: tokens.spacing.md,
          },
        ]}
      >
        <Text style={[styles.topInfoEyebrow, { color: 'rgba(255,255,255,0.75)' }]}>
          QR Scan
        </Text>
        <Text style={[styles.topInfoTitle, { color: '#fff' }]}>
          Align the code inside the frame.
        </Text>
        <Text style={[styles.topInfoCopy, { color: 'rgba(255,255,255,0.72)' }]}>
          We keep the QR intact for payment compatibility, then ask you to confirm the result back in SpendLens.
        </Text>
      </View>

      <TouchableOpacity
        style={[
          styles.closeBtn,
          {
            top: 56,
            right: tokens.spacing.lg,
            backgroundColor: 'rgba(0,0,0,0.45)',
            borderRadius: tokens.radii.full,
          },
        ]}
        onPress={() => router.dismiss()}
        hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}
      >
        <Ionicons name="close" size={18} color="#fff" />
      </TouchableOpacity>

      <View
        style={[
          styles.hintContainer,
          {
            top: (SCREEN_HEIGHT - SCAN_FRAME_SIZE) / 2 + SCAN_FRAME_SIZE + 28,
          },
        ]}
      >
        <View
          style={[
            styles.hintPill,
            {
              backgroundColor: 'rgba(11, 12, 12, 0.52)',
              borderRadius: tokens.radii.full,
              paddingHorizontal: tokens.spacing.md,
              paddingVertical: tokens.spacing.sm,
            },
          ]}
        >
          <Ionicons name="qr-code-outline" size={16} color="rgba(255,255,255,0.72)" />
          <Text style={styles.hintText}>Align QR code within the frame</Text>
        </View>
      </View>

      <TouchableOpacity
        style={[
          styles.torchBtn,
          {
            bottom: 64,
            left: SCREEN_WIDTH / 2 - 28,
            backgroundColor: torch ? colors.primary : 'rgba(0,0,0,0.45)',
            borderRadius: tokens.radii.full,
          },
        ]}
        onPress={() => setTorch((v) => !v)}
      >
        <Ionicons name={torch ? 'flash' : 'flash-off'} size={22} color="#fff" />
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#000',
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  overlayPiece: {
    position: 'absolute',
  },
  scanFrame: {
    position: 'absolute',
  },
  corner: {
    position: 'absolute',
    width: 28,
    height: 28,
    borderWidth: 3,
  },
  cornerTL: {
    top: 0,
    left: 0,
    borderRightWidth: 0,
    borderBottomWidth: 0,
    borderTopLeftRadius: 4,
  },
  cornerTR: {
    top: 0,
    right: 0,
    borderLeftWidth: 0,
    borderBottomWidth: 0,
    borderTopRightRadius: 4,
  },
  cornerBL: {
    bottom: 0,
    left: 0,
    borderRightWidth: 0,
    borderTopWidth: 0,
    borderBottomLeftRadius: 4,
  },
  cornerBR: {
    bottom: 0,
    right: 0,
    borderLeftWidth: 0,
    borderTopWidth: 0,
    borderBottomRightRadius: 4,
  },
  hintContainer: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  hintPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  hintText: {
    color: 'rgba(255,255,255,0.75)',
    fontSize: 14,
    fontWeight: '500',
  },
  topInfoCard: {
    position: 'absolute',
  },
  topInfoEyebrow: {
    fontSize: 11,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  topInfoTitle: {
    fontSize: 22,
    fontWeight: '700',
    marginTop: 6,
  },
  topInfoCopy: {
    fontSize: 13,
    lineHeight: 20,
    marginTop: 8,
  },
  closeBtn: {
    position: 'absolute',
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  torchBtn: {
    position: 'absolute',
    width: 56,
    height: 56,
    alignItems: 'center',
    justifyContent: 'center',
  },
  permissionTitle: {
    fontWeight: '700',
    textAlign: 'center',
  },
  permissionBody: {
    fontSize: 15,
    textAlign: 'center',
    lineHeight: 22,
  },
  permissionIcon: {
    width: 60,
    height: 60,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
