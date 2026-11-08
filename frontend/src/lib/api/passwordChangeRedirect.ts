type PasswordChangeRedirectHandler = () => void;

const DUPLICATE_REDIRECT_WINDOW_MS = 1_500;

let handler: PasswordChangeRedirectHandler | null = null;
let redirectInFlight = false;
let resetTimer: ReturnType<typeof setTimeout> | null = null;

export function setPasswordChangeRedirectHandler(next: PasswordChangeRedirectHandler | null) {
  handler = next;
}

export function resetPasswordChangeRedirectGuard() {
  redirectInFlight = false;
  if (resetTimer !== null) {
    clearTimeout(resetTimer);
    resetTimer = null;
  }
}

/**
 * A blocked request must move the user to the change screen exactly once, even
 * when a page fires several requests at the same time. The short guard window
 * absorbs the burst; the route handler also refuses to navigate when it is
 * already on the change screen.
 */
export function handlePasswordChangeRequired() {
  if (!handler || redirectInFlight) return;
  redirectInFlight = true;
  try {
    handler();
  } finally {
    resetTimer = setTimeout(() => {
      redirectInFlight = false;
      resetTimer = null;
    }, DUPLICATE_REDIRECT_WINDOW_MS);
  }
}
