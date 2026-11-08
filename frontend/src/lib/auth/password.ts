const ALPHABET =
  'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*';
const PASSWORD_LENGTH = 16;

function randomIndex(maxExclusive: number): number {
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const values = new Uint32Array(1);
    crypto.getRandomValues(values);
    return values[0] % maxExclusive;
  }
  return Math.floor(Math.random() * maxExclusive);
}

/**
 * One-time owner-issued credentials. The alphabet deliberately omits 0, O, I,
 * l, and 1 so a password read aloud or copied off a phone survives the trip.
 */
export function generateTemporaryPassword(): string {
  let password = '';
  for (let index = 0; index < PASSWORD_LENGTH; index += 1) {
    password += ALPHABET[randomIndex(ALPHABET.length)];
  }
  return password;
}
