import { describe, expect, it } from 'vitest';
import { generateTemporaryPassword } from './password';

describe('generateTemporaryPassword', () => {
  it('generates 16 characters without the ambiguous glyphs 0, O, I, l, and 1', () => {
    for (let attempt = 0; attempt < 100; attempt += 1) {
      const password = generateTemporaryPassword();
      expect(password).toHaveLength(16);
      expect(password).toMatch(/^[A-HJ-NP-Za-km-z2-9!@#$%^&*]+$/);
      expect(password).not.toMatch(/[0OIl1]/);
    }
  });

  it('does not repeat the same value on consecutive calls', () => {
    const generated = new Set(
      Array.from({ length: 20 }, () => generateTemporaryPassword()),
    );
    expect(generated.size).toBeGreaterThan(1);
  });
});
