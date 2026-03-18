import { create } from 'zustand';

interface AppState {
  theme: 'light' | 'dark' | 'system';
  preferredUpiApp: string | null;
  onboardingComplete: boolean;
  setTheme: (t: 'light' | 'dark' | 'system') => void;
  setPreferredUpiApp: (pkg: string | null) => void;
  setOnboardingComplete: () => void;
}

export const useAppStore = create<AppState>((set) => ({
  theme: 'system',
  preferredUpiApp: null,
  onboardingComplete: false,
  setTheme: (t) => set({ theme: t }),
  setPreferredUpiApp: (pkg) => set({ preferredUpiApp: pkg }),
  setOnboardingComplete: () => set({ onboardingComplete: true }),
}));
