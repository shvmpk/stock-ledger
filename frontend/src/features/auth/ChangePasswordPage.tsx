import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Group,
  Paper,
  PasswordInput,
  Stack,
  Text,
  ThemeIcon,
  Title,
} from '@mantine/core';
import { IconAlertTriangle, IconCheck, IconX } from '@tabler/icons-react';
import { useAuth } from '../../auth/useAuth';
import { announceAuthStateChanged } from '../../auth/crossTab';
import { changePassword } from '../../lib/api/endpoints/auth';
import { isApiError } from '../../lib/api/errors';
import { safeReturnTo } from '../../lib/navigation/returnTo';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';

const COMMON_PASSWORDS = new Set([
  '123456789012',
  'password',
  'password1234',
  'password123456',
  'qwerty123456',
  'qwertyuiop12',
  'letmein123456',
  'welcome123456',
  'admin123456',
  'iloveyou1234',
  'abc123456789',
]);

function normalize(value: string): string {
  return value
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase();
}

function contextTokens(user: { email: string; fullName: string }): string[] {
  const emailLocalPart = user.email.split('@')[0] ?? '';
  const nameParts = user.fullName.split(/\s+/);
  return [emailLocalPart, ...nameParts]
    .flatMap((part) => part.split(/[^a-z0-9]+/i))
    .map(normalize)
    .filter((part) => part.length >= 3);
}

interface PolicyCheck {
  label: string;
  met: boolean;
}

function policyChecks(password: string, user: { email: string; fullName: string }): PolicyCheck[] {
  const normalizedPassword = normalize(password);
  const tokens = contextTokens(user);
  return [
    { label: 'At least 12 characters', met: password.length >= 12 },
    {
      label: 'Not a commonly used password',
      met: password.length > 0 && !COMMON_PASSWORDS.has(password.trim().toLowerCase()),
    },
    {
      label: 'Not based on your name or email',
      met:
        password.length > 0 &&
        !tokens.some((token) => normalizedPassword.includes(token)),
    },
    {
      label: 'Maximum 72 characters',
      met: new TextEncoder().encode(password).length <= 72,
    },
  ];
}

export function ChangePasswordPage() {
  const { user, refresh } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const returnTo = safeReturnTo(searchParams.get('returnTo'));

  const forced = Boolean(user?.mustChangePassword);
  const [showCurrentPassword, setShowCurrentPassword] = useState(!forced);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [confirmTouched, setConfirmTouched] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [newPasswordMessages, setNewPasswordMessages] = useState<string[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  const currentRef = useRef<HTMLInputElement>(null);
  const newRef = useRef<HTMLInputElement>(null);
  const confirmRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (showCurrentPassword) {
      currentRef.current?.focus();
    }
  }, [showCurrentPassword]);

  if (!user) return null;

  const checks = policyChecks(newPassword, user);
  const mismatch = confirmTouched && confirmPassword.length > 0 && confirmPassword !== newPassword;

  const focusFirstInvalid = (errors: Record<string, string>) => {
    if (errors.currentPassword) currentRef.current?.focus();
    else if (errors.newPassword) newRef.current?.focus();
    else if (errors.confirmPassword) confirmRef.current?.focus();
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setFieldErrors({});
    setNewPasswordMessages([]);
    setFormError(null);
    setServerError(null);

    const localErrors: Record<string, string> = {};
    if (showCurrentPassword && !currentPassword) {
      localErrors.currentPassword = 'Enter your current password.';
    }
    if (!newPassword) {
      localErrors.newPassword = 'Enter a new password.';
    }
    if (!confirmPassword) {
      localErrors.confirmPassword = 'Confirm your new password.';
    } else if (newPassword !== confirmPassword) {
      localErrors.confirmPassword = 'Passwords do not match.';
    }

    if (Object.keys(localErrors).length > 0) {
      setFieldErrors(localErrors);
      setFormError('Check the highlighted fields and try again.');
      focusFirstInvalid(localErrors);
      return;
    }

    setSubmitting(true);
    try {
      await changePassword({
        ...(showCurrentPassword ? { currentPassword } : {}),
        newPassword,
      });

      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setConfirmTouched(false);
      announceAuthStateChanged();
      try {
        await refresh();
      } catch {
        // The change itself succeeded; AuthProvider has already cleared a
        // revoked session, and RequireAuth will send the user to login.
      }
      navigate(returnTo, { replace: true });
    } catch (caught) {
      if (!isApiError(caught)) {
        setServerError(caught);
        return;
      }

      if (caught.code === 'CURRENT_PASSWORD_REQUIRED') {
        setShowCurrentPassword(true);
        setFormError('For your security, enter your temporary password to continue.');
        setFieldErrors({});
        return;
      }
      if (caught.code === 'INVALID_CURRENT_PASSWORD') {
        setShowCurrentPassword(true);
        setFieldErrors({
          currentPassword: "That password doesn't match your current one.",
        });
        currentRef.current?.focus();
        return;
      }

      const messages = caught.invalidParams
        .filter((param) => param.field === 'newPassword' || param.field === 'password')
        .map((param) => param.message)
        .filter(Boolean);
      if (messages.length > 0) {
        setNewPasswordMessages(messages);
        newRef.current?.focus();
        return;
      }

      setServerError(caught);
    } finally {
      setSubmitting(false);
    }
  };

  const newPasswordError: ReactNode =
    newPasswordMessages.length > 0 ? (
      <>
        {newPasswordMessages.map((message) => (
          <Text component="span" display="block" size="xs" key={message}>
            {message}
          </Text>
        ))}
      </>
    ) : (
      fieldErrors.newPassword
    );

  return (
    <Box
      style={{
        minHeight: '100vh',
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1fr)',
        placeItems: 'center',
        padding: 16,
        background:
          'radial-gradient(circle at 20% 10%, var(--mantine-color-emerald-0) 0, transparent 38%), var(--mantine-color-body)',
      }}
    >
      <Paper withBorder shadow="sm" radius="md" p="xl" w="100%" maw={540}>
        <Stack gap="md">
          <Box>
            <Title order={1} size="h3">
              {forced ? 'Choose a new password' : 'Change password'}
            </Title>
            <Text size="sm" c="dimmed" mt={4}>
              {forced
                ? 'Your store owner set a temporary password. Choose your own to continue.'
                : 'Choose a new password for your StockLedger account.'}
            </Text>
          </Box>

          {serverError ? (
            <ProblemAlert error={serverError} title="Could not update password" compact />
          ) : null}

          {formError ? (
            <Alert
              color="red"
              variant="light"
              icon={<IconAlertTriangle size={16} />}
              role="alert"
            >
              {formError}
            </Alert>
          ) : null}

          <form onSubmit={(event) => void submit(event)} noValidate>
            <Stack gap="md">
              {showCurrentPassword ? (
                <PasswordInput
                  ref={currentRef}
                  label="Current password"
                  autoComplete="current-password"
                  autoCapitalize="none"
                  spellCheck={false}
                  value={currentPassword}
                  onChange={(event) => setCurrentPassword(event.currentTarget.value)}
                  error={fieldErrors.currentPassword}
                  required
                />
              ) : null}

              <Box>
                <PasswordInput
                  ref={newRef}
                  label="New password"
                  autoComplete="new-password"
                  autoCapitalize="none"
                  spellCheck={false}
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.currentTarget.value)}
                  error={newPasswordError}
                  required
                />
                <Stack gap={4} mt="xs" aria-label="Password policy">
                  {checks.map((check) => (
                    <Group key={check.label} gap={6} wrap="nowrap">
                      <ThemeIcon
                        size={16}
                        radius="xl"
                        variant="light"
                        color={check.met ? 'emerald' : 'gray'}
                      >
                        {check.met ? <IconCheck size={11} /> : <IconX size={11} />}
                      </ThemeIcon>
                      <Text size="xs" c={check.met ? undefined : 'dimmed'}>
                        {check.label}
                      </Text>
                      <Text size="10px" c="dimmed" ml="auto" tt="uppercase">
                        {check.met ? 'Met' : 'Not yet'}
                      </Text>
                    </Group>
                  ))}
                </Stack>
              </Box>

              <PasswordInput
                ref={confirmRef}
                label="Confirm new password"
                autoComplete="new-password"
                autoCapitalize="none"
                spellCheck={false}
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.currentTarget.value)}
                onBlur={() => setConfirmTouched(true)}
                error={mismatch ? 'Passwords do not match.' : fieldErrors.confirmPassword}
                required
              />

              <Group justify="space-between" gap="sm" wrap="wrap">
                <Button
                  variant="subtle"
                  color="gray"
                  onClick={() => navigate(returnTo, { replace: true })}
                  disabled={submitting}
                >
                  Cancel
                </Button>
                <Button type="submit" loading={submitting}>
                  Update password
                </Button>
              </Group>
            </Stack>
          </form>
        </Stack>
      </Paper>
    </Box>
  );
}
