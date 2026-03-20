import { Linking, Platform } from 'react-native';
import * as IntentLauncher from 'expo-intent-launcher';
import { UpiPaymentRequest, UPI_APPS, UpiAppKey } from './types';
import { buildUpiUri } from './parser';

export interface RawUpiLaunchResult {
  resultCode?: number;
  data?: string;
  extra?: Record<string, unknown>;
}

/**
 * Launch a UPI payment by building a URI from a request object.
 * Used for manual pay (UPI ID / phone) where we construct the request ourselves.
 */
export async function launchUpiPayment(
  request: UpiPaymentRequest,
  targetApp?: string,
): Promise<RawUpiLaunchResult | null> {
  const uri = buildUpiUri(request);
  return launchUpiUri(uri, targetApp);
}

/**
 * Launch a UPI payment using a raw URI string (e.g. directly from a QR code).
 *
 * QR codes contain a `sign` param with base64 content (has + and = chars).
 * Android's intent URI parser decodes + as space, corrupting the signature.
 * We encode + as %2B in query param VALUES only so the receiving app gets
 * the original + back when it URL-decodes.
 */
export async function launchRawUpiUri(
  uri: string,
  targetApp?: string,
): Promise<RawUpiLaunchResult | null> {
  const safeUri = encodeUriPlusChars(uri);
  return launchUpiUri(safeUri, targetApp);
}

/**
 * Encode + as %2B in URI query parameter values only.
 * Splits on ? to get the query string, then encodes + in each param value.
 * Leaves param names and the base URI untouched.
 */
function encodeUriPlusChars(uri: string): string {
  const qIdx = uri.indexOf('?');
  if (qIdx === -1) return uri;

  const base = uri.substring(0, qIdx);
  const query = uri.substring(qIdx + 1);

  const safeQuery = query
    .split('&')
    .map((param) => {
      const eqIdx = param.indexOf('=');
      if (eqIdx === -1) return param;
      const key = param.substring(0, eqIdx);
      const value = param.substring(eqIdx + 1);
      // Encode + as %2B so Android doesn't decode it as space
      return `${key}=${value.replace(/\+/g, '%2B')}`;
    })
    .join('&');

  return `${base}?${safeQuery}`;
}

async function launchUpiUri(uri: string, targetApp?: string): Promise<RawUpiLaunchResult | null> {
  if (Platform.OS === 'android') {
    try {
      const result = await IntentLauncher.startActivityAsync('android.intent.action.VIEW', {
        data: uri,
        ...(targetApp ? { packageName: targetApp } : {}),
        flags: 1, // FLAG_ACTIVITY_NEW_TASK
      });
      return {
        resultCode: result.resultCode,
        data: result.data,
        extra: result.extra as Record<string, unknown> | undefined,
      };
    } catch {
      // Fall through to Linking
    }
  }

  await Linking.openURL(uri);
  return null;
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
