import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Divider,
  Group,
  Paper,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { IconAlertTriangle, IconBox, IconLock } from '@tabler/icons-react';
import { useAuth } from '../../auth/useAuth';
import { errorMessage } from '../../lib/api/errors';
import { passwordChangePath, safeReturnTo } from '../../lib/navigation/returnTo';

export function LoginPage() {
  const { status, user, login } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  const next = safeReturnTo(searchParams.get('next'));

  if (status === 'authenticated') {
    return (
      <Navigate to={user?.mustChangePassword ? passwordChangePath(next) : next} replace />
    );
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const authenticated = await login({ email: email.trim(), password });
      navigate(authenticated.mustChangePassword ? passwordChangePath(next) : next, {
        replace: true,
      });
    } catch (caught) {
      setError(caught);
    } finally {
      setSubmitting(false);
    }
  };

  const fillDemoAccount = (role: 'owner' | 'staff') => {
    setEmail(role === 'owner' ? 'owner@stockledger.io' : 'staff@stockledger.io');
    setPassword(role === 'owner' ? 'OwnerPass2026!' : 'StaffPass2026!');
    setError(null);
  };

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
      <Paper withBorder shadow="sm" radius="md" p="xl" w="100%" maw={440}>
        <Stack gap="md">
          <Group gap="sm">
            <Box
              aria-hidden="true"
              style={{
                width: 38,
                height: 38,
                borderRadius: 8,
                background: 'var(--mantine-color-emerald-7)',
                color: 'white',
                display: 'grid',
                placeItems: 'center',
                fontWeight: 800,
                fontSize: 13,
              }}
            >
              SL
            </Box>
            <Box>
              <Title order={1} size="h3">
                StockLedger
              </Title>
              <Text size="xs" c="dimmed">
                Exact stock. Calm dispatch.
              </Text>
            </Box>
          </Group>

          <Divider />

          <Box>
            <Title order={2} size="h4">
              Sign in
            </Title>
            <Text size="sm" c="dimmed" mt={2}>
              Use your store account to open the operational ledger.
            </Text>
          </Box>

          {error ? (
            <Alert color="red" variant="light" icon={<IconAlertTriangle size={16} />} role="alert">
              {errorMessage(error)}
            </Alert>
          ) : null}

          <form onSubmit={(event) => void submit(event)} noValidate>
            <Stack gap="sm">
              <TextInput
                label="Email"
                type="email"
                autoComplete="username"
                value={email}
                onChange={(event) => setEmail(event.currentTarget.value)}
                placeholder="owner@stockledger.io"
                required
              />
              <PasswordInput
                label="Password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.currentTarget.value)}
                required
                leftSection={<IconLock size={15} aria-hidden="true" />}
              />
              <Button type="submit" fullWidth loading={submitting} leftSection={<IconBox size={16} />}>
                Sign in
              </Button>
            </Stack>
          </form>

          <Divider label="Demo accounts" labelPosition="center" />

          <Group grow gap="xs">
            <Button variant="light" size="xs" onClick={() => fillDemoAccount('owner')}>
              Use owner demo
            </Button>
            <Button variant="light" color="gray" size="xs" onClick={() => fillDemoAccount('staff')}>
              Use staff demo
            </Button>
          </Group>
          <Text size="xs" c="dimmed" ta="center">
            Owner sees financial settings. Staff sees operations only.
          </Text>
        </Stack>
      </Paper>
    </Box>
  );
}
