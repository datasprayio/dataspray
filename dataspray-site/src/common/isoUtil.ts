
export const isSsr = (): boolean => typeof window === 'undefined';
export const isCsr = (): boolean => !isSsr();
