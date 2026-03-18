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
import { useTheme } from '../theme/provider';
import { parseUpiUri } from '../services/upi/parser';
import { useTransactionFlowStore } from '../stores/transaction-flow';

const SCAN_FRAME_SIZE = 260;
const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

export default function ScanScreen() {
  const { colors, tokens } = useTheme();
  const router = useRouter();
  const [permission, requestPermission] = useCameraPermissions();
  const [torch, setTorch] = useState(false);
  const scannedRef = useRef(false);

  const handleBarCodeScanned = useCallback(
    ({ data }: { data: string }) => {
      if (scannedRef.current) return;

      const parsed = parseUpiUri(data);
      if (!parsed) {
        Alert.alert('Invalid QR', 'Not a UPI QR code. Please scan a valid UPI payment QR.');
        return;
      }

      scannedRef.current = true;

      const { capture, launch } = useTransactionFlowStore.getState();
      capture(parsed, 'qr').then(() => {
        return launch();
      }).then(() => {
        router.dismiss();
      }).catch(() => {
        scannedRef.current = false;
      });
    },
    [router]
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
        <Text style={[styles.permissionTitle, { color: colors.text }]}>Camera Access Required</Text>
        <Text style={[styles.permissionBody, { color: colors.textSecondary, marginTop: tokens.spacing.sm }]}>
          SpendLens needs camera access to scan UPI QR codes.
        </Text>
        <TouchableOpacity
          style={[
            styles.permissionBtn,
            {
              backgroundColor: colors.primary,
              marginTop: tokens.spacing.lg,
              borderRadius: tokens.radii.md,
              paddingVertical: tokens.spacing.sm,
              paddingHorizontal: tokens.spacing.lg,
            },
          ]}
          onPress={requestPermission}
        >
          <Text style={{ color: '#fff', fontWeight: '600', fontSize: 15 }}>Grant Permission</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={{ marginTop: tokens.spacing.md }}
          onPress={() => router.dismiss()}
        >
          <Text style={{ color: colors.textSecondary, fontSize: 14 }}>Cancel</Text>
        </TouchableOpacity>
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

      {/* Hint text */}
      <View
        style={[
          styles.hintContainer,
          { top: (SCREEN_HEIGHT - SCAN_FRAME_SIZE) / 2 + SCAN_FRAME_SIZE + 24 },
        ]}
      >
        <Text style={styles.hintText}>Align QR code within the frame</Text>
      </View>

      {/* Close button */}
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
        <Text style={styles.closeBtnText}>✕</Text>
      </TouchableOpacity>

      {/* Torch toggle */}
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
        <Text style={styles.torchBtnText}>⚡</Text>
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
  hintText: {
    color: 'rgba(255,255,255,0.75)',
    fontSize: 14,
    fontWeight: '500',
  },
  closeBtn: {
    position: 'absolute',
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeBtnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  torchBtn: {
    position: 'absolute',
    width: 56,
    height: 56,
    alignItems: 'center',
    justifyContent: 'center',
  },
  torchBtnText: {
    fontSize: 24,
  },
  permissionTitle: {
    fontSize: 20,
    fontWeight: '700',
    textAlign: 'center',
  },
  permissionBody: {
    fontSize: 15,
    textAlign: 'center',
    lineHeight: 22,
  },
  permissionBtn: {
    alignItems: 'center',
  },
});
