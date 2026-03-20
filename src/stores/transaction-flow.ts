import { create } from 'zustand';
import { UpiPaymentRequest, PaymentMethod, PaymentStatus, FlowStatus, UpiPaymentResult } from '../services/upi/types';
import { launchRawUpiUri, launchUpiPayment } from '../services/upi/intent';
import { parseUpiPaymentResult } from '../services/upi/result';
import * as TransactionRepo from '../db/repositories/transactions';

interface CaptureOptions {
  rawUpiUri?: string;
  recipientPhone?: string;
  upiAppPackage?: string | null;
}

interface TransactionFlowState {
  status: FlowStatus;
  paymentRequest: UpiPaymentRequest | null;
  paymentMethod: PaymentMethod | null;
  transactionId: string | null;
  rawUpiUri: string | null;
  launchResult: UpiPaymentResult | null;

  // Actions
  capture: (
    request: UpiPaymentRequest,
    method: PaymentMethod,
    options?: CaptureOptions,
  ) => Promise<void>;
  launch: (upiApp?: string) => Promise<void>;
  onReturn: () => void;
  confirm: (status: PaymentStatus, completedAmount?: number) => Promise<void>;
  setCategoryAndFinish: (categoryId: string) => Promise<void>;
  reset: () => void;
}

const EMPTY_FLOW_STATE = {
  status: 'idle' as const,
  paymentRequest: null,
  paymentMethod: null,
  transactionId: null,
  rawUpiUri: null,
  launchResult: null,
};

export const useTransactionFlowStore = create<TransactionFlowState>((set, get) => ({
  ...EMPTY_FLOW_STATE,

  capture: async (
    request: UpiPaymentRequest,
    method: PaymentMethod,
    options?: CaptureOptions,
  ) => {
    const amountPaise = request.amount !== undefined
      ? Math.round(request.amount * 100)
      : 0;

    const id = await TransactionRepo.create({
      amount_paise: amountPaise,
      recipient_upi_id: options?.recipientPhone ? null : request.payeeAddress,
      recipient_name: request.payeeName ?? null,
      recipient_phone: options?.recipientPhone ?? null,
      payment_method: method,
      upi_app: options?.upiAppPackage ?? null,
      status: 'pending',
      note: request.transactionNote ?? null,
    });

    set({
      status: 'captured',
      paymentRequest: request,
      paymentMethod: method,
      transactionId: id,
      rawUpiUri: options?.rawUpiUri ?? null,
      launchResult: null,
    });
  },

  launch: async (upiApp?: string) => {
    const { paymentRequest, rawUpiUri, transactionId } = get();
    if (!paymentRequest) return;

    set({ status: 'launching' });
    try {
      const launchResult = rawUpiUri
        ? await launchRawUpiUri(rawUpiUri, upiApp)
        : await launchUpiPayment(paymentRequest, upiApp);
      const parsedResult = parseUpiPaymentResult(launchResult);

      if (rawUpiUri) {
        // already launched above
      }

      if (launchResult) {
        set({
          status: 'confirming',
          launchResult: parsedResult,
        });
        return;
      }

      set({ status: 'awaiting_return', launchResult: null });
    } catch (error) {
      if (transactionId) {
        try {
          await TransactionRepo.removePending(transactionId);
        } catch {
          // Best-effort cleanup so we don't block surfacing the launch error.
        }
      }
      set({ ...EMPTY_FLOW_STATE });
      throw error;
    }
  },

  onReturn: () => {
    const { status } = get();
    if (status === 'awaiting_return') {
      set({ status: 'confirming' });
    }
  },

  confirm: async (paymentStatus: PaymentStatus, completedAmount?: number) => {
    const { transactionId, paymentRequest, launchResult } = get();
    if (!transactionId) return;

    const resolvedAmount = completedAmount ?? launchResult?.amount;

    if (paymentStatus === 'completed' && resolvedAmount !== undefined) {
      await TransactionRepo.updateAmount(transactionId, Math.round(resolvedAmount * 100));
    }

    const completedAt = paymentStatus === 'completed' ? new Date().toISOString() : undefined;
    await TransactionRepo.updateStatus(transactionId, paymentStatus, completedAt);

    if (paymentStatus === 'completed') {
      set({
        status: 'categorizing',
        paymentRequest: paymentRequest && resolvedAmount !== undefined
          ? { ...paymentRequest, amount: resolvedAmount }
          : paymentRequest,
        launchResult: null,
      });
    } else {
      set({ status: 'done', launchResult: null });
    }
  },

  setCategoryAndFinish: async (categoryId: string) => {
    const { transactionId } = get();
    if (!transactionId) return;

    await TransactionRepo.updateCategory(transactionId, categoryId);
    set({ status: 'done' });
  },

  reset: () => {
    set({ ...EMPTY_FLOW_STATE });
  },
}));
