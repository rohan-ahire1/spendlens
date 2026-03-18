import { create } from 'zustand';
import { UpiPaymentRequest, PaymentMethod, PaymentStatus, FlowStatus } from '../services/upi/types';
import { launchUpiPayment } from '../services/upi/intent';
import * as TransactionRepo from '../db/repositories/transactions';

interface TransactionFlowState {
  status: FlowStatus;
  paymentRequest: UpiPaymentRequest | null;
  paymentMethod: PaymentMethod | null;
  transactionId: string | null;

  // Actions
  capture: (request: UpiPaymentRequest, method: PaymentMethod) => Promise<void>;
  launch: (upiApp?: string) => Promise<void>;
  onReturn: () => void;
  confirm: (status: PaymentStatus) => Promise<void>;
  setCategoryAndFinish: (categoryId: string) => Promise<void>;
  reset: () => void;
}

export const useTransactionFlowStore = create<TransactionFlowState>((set, get) => ({
  status: 'idle',
  paymentRequest: null,
  paymentMethod: null,
  transactionId: null,

  capture: async (request: UpiPaymentRequest, method: PaymentMethod) => {
    const amountPaise = request.amount !== undefined
      ? Math.round(request.amount * 100)
      : 0;

    const id = await TransactionRepo.create({
      amount_paise: amountPaise,
      recipient_upi_id: request.payeeAddress,
      recipient_name: request.payeeName ?? null,
      payment_method: method,
      status: 'pending',
      note: request.transactionNote ?? null,
    });

    set({
      status: 'captured',
      paymentRequest: request,
      paymentMethod: method,
      transactionId: id,
    });
  },

  launch: async (upiApp?: string) => {
    const { paymentRequest } = get();
    if (!paymentRequest) return;

    set({ status: 'launching' });
    await launchUpiPayment(paymentRequest, upiApp);
    set({ status: 'awaiting_return' });
  },

  onReturn: () => {
    const { status } = get();
    if (status === 'awaiting_return') {
      set({ status: 'confirming' });
    }
  },

  confirm: async (paymentStatus: PaymentStatus) => {
    const { transactionId } = get();
    if (!transactionId) return;

    const completedAt = paymentStatus === 'completed' ? new Date().toISOString() : undefined;
    await TransactionRepo.updateStatus(transactionId, paymentStatus, completedAt);

    if (paymentStatus === 'completed') {
      set({ status: 'categorizing' });
    } else {
      set({ status: 'done' });
    }
  },

  setCategoryAndFinish: async (categoryId: string) => {
    const { transactionId } = get();
    if (!transactionId) return;

    await TransactionRepo.updateCategory(transactionId, categoryId);
    set({ status: 'done' });
  },

  reset: () => {
    set({
      status: 'idle',
      paymentRequest: null,
      paymentMethod: null,
      transactionId: null,
    });
  },
}));
