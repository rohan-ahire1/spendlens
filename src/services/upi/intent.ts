import { Linking } from 'react-native';
import * as IntentLauncher from 'expo-intent-launcher';
import { UpiPaymentRequest, UPI_APPS, UpiAppKey } from './types';
import { buildUpiUri } from './parser';

/**
 * Launch a UPI payment.
 * If targetApp (package name) is provided, tries IntentLauncher first, then falls back to Linking.
 */
export async function launchUpiPayment(
  request: UpiPaymentRequest,
  targetApp?: string,
): Promise<void> {
  const uri = buildUpiUri(request);

  if (targetApp) {
    try {
      await IntentLauncher.startActivityAsync('android.intent.action.VIEW', {
        data: uri,
        packageName: targetApp,
        flags: 1, // FLAG_ACTIVITY_NEW_TASK
      });
      return;
    } catch {
      // Fall through to Linking
    }
  }

  await Linking.openURL(uri);
}

// Deep link schemes used to check if an app is installed
const UPI_APP_SCHEMES: Record<UpiAppKey, string> = {
  phonepe: 'phonepe://',
  gpay: 'gpay://',
  paytm: 'paytm://',
  cred: 'credpay://',
  amazonpay: 'amazonpay://',
};

/**
 * Returns which UPI apps are installed by checking if their deep link schemes can be opened.
 */
export async function getInstalledUpiApps(): Promise<UpiAppKey[]> {
  const keys = Object.keys(UPI_APP_SCHEMES) as UpiAppKey[];
  const results = await Promise.all(
    keys.map(async (key) => {
      try {
        const canOpen = await Linking.canOpenURL(UPI_APP_SCHEMES[key]);
        return canOpen ? key : null;
      } catch {
        return null;
      }
    }),
  );
  return results.filter((k): k is UpiAppKey => k !== null);
}
