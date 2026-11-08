export const AUTH_STATE_CHANGED_KEY = 'stockledger.auth.stateChanged';

/**
 * localStorage is the cross-tab channel because every browser fires a storage
 * event in the *other* tabs. The current tab updates its own query cache
 * directly, so it does not need the event.
 */
export function announceAuthStateChanged(): void {
  try {
    localStorage.setItem(AUTH_STATE_CHANGED_KEY, `${Date.now()}-${Math.random()}`);
  } catch {
    // Private-mode storage failures must never break a successful password change.
  }
}

export function subscribeToAuthStateChanged(onChange: () => void): () => void {
  const handleStorage = (event: StorageEvent) => {
    if (event.key === AUTH_STATE_CHANGED_KEY) {
      onChange();
    }
  };
  window.addEventListener('storage', handleStorage);
  return () => window.removeEventListener('storage', handleStorage);
}
