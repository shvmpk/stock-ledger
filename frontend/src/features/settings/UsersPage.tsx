import { useMemo, useState } from 'react';
import {
  ActionIcon,
  Badge,
  Box,
  Button,
  CopyButton,
  Group,
  Modal,
  PasswordInput,
  Select,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Title,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconCheck,
  IconCopy,
  IconKey,
  IconPlus,
  IconRefresh,
  IconUserOff,
  IconUserCheck,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createUser,
  deactivateUser,
  listUsers,
  resetUserPassword,
  updateUser,
} from '../../lib/api/endpoints/users';
import { generateTemporaryPassword } from '../../lib/auth/password';
import type { CreateUserInput, Role, UpdateUserInput, User } from '../../lib/api/types';
import { isApiError } from '../../lib/api/errors';
import { queryKeys } from '../../lib/query/keys';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ErrorState } from '../../components/feedback/ErrorState';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';
import { TableSkeleton } from '../../components/feedback/TableSkeleton';
import { ConfirmDialog } from '../../components/feedback/ConfirmDialog';
import { useAuth } from '../../auth/useAuth';
import { formatQuantity } from '../../lib/format/quantity';

interface CreateForm {
  email: string;
  fullName: string;
  password: string;
  role: Role;
}

interface EditForm {
  fullName: string;
  role: Role;
  active: boolean;
}

function firstName(fullName: string): string {
  return fullName.trim().split(/\s+/)[0] ?? fullName;
}

export function UsersPage() {
  const { user: currentUser } = useAuth();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.users, queryFn: listUsers });
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<User | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<User | null>(null);
  const [resetTarget, setResetTarget] = useState<User | null>(null);
  const [resetPassword, setResetPassword] = useState('');
  const [createForm, setCreateForm] = useState<CreateForm>({
    email: '',
    fullName: '',
    password: '',
    role: 'STAFF',
  });
  const [editForm, setEditForm] = useState<EditForm>({ fullName: '', role: 'STAFF', active: true });
  const [error, setError] = useState<unknown>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const users = useMemo(() => query.data ?? [], [query.data]);
  const summary = useMemo(
    () => ({
      total: users.length,
      owners: users.filter((user) => user.role === 'OWNER' && user.active).length,
      staff: users.filter((user) => user.role === 'STAFF' && user.active).length,
      inactive: users.filter((user) => !user.active).length,
    }),
    [users],
  );

  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.users });

  const openEdit = (user: User) => {
    setEditTarget(user);
    setEditForm({ fullName: user.fullName, role: user.role, active: user.active });
    setError(null);
    setFieldErrors({});
  };

  const openReset = (user: User) => {
    setError(null);
    setResetPassword(generateTemporaryPassword());
    setResetTarget(user);
  };

  const createMutation = useMutation({
    mutationFn: (input: CreateUserInput) => createUser(input),
    onSuccess: async (created) => {
      notifications.show({
        title: 'User created',
        message: `${created.fullName} can now sign in as ${created.role === 'OWNER' ? 'owner' : 'staff'}.`,
        color: 'emerald',
        icon: <IconCheck size={18} />,
      });
      setCreateOpen(false);
      setCreateForm({ email: '', fullName: '', password: '', role: 'STAFF' });
      setFieldErrors({});
      setError(null);
      await invalidate();
    },
    onError: (caught) => {
      setError(caught);
      if (isApiError(caught)) {
        const mapped: Record<string, string> = {};
        for (const param of caught.invalidParams) mapped[param.field] = param.message;
        setFieldErrors(mapped);
      }
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateUserInput }) => updateUser(id, input),
    onSuccess: async (updated) => {
      notifications.show({
        title: 'User updated',
        message: `${updated.fullName} is ${updated.active ? 'active' : 'inactive'} as ${updated.role === 'OWNER' ? 'owner' : 'staff'}.`,
        color: 'emerald',
        icon: <IconCheck size={18} />,
      });
      setEditTarget(null);
      setError(null);
      await invalidate();
    },
    onError: (caught) => {
      setError(caught);
      if (isApiError(caught)) {
        const mapped: Record<string, string> = {};
        for (const param of caught.invalidParams) mapped[param.field] = param.message;
        setFieldErrors(mapped);
      }
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: (id: string) => deactivateUser(id),
    onSuccess: async (updated) => {
      notifications.show({
        title: 'User deactivated',
        message: `${updated.fullName} can no longer sign in.`,
        color: 'gray',
        icon: <IconUserOff size={18} />,
      });
      setDeactivateTarget(null);
      setError(null);
      await invalidate();
    },
    onError: (caught) => setError(caught),
  });

  const resetMutation = useMutation({
    mutationFn: ({ id, password }: { id: string; password: string }) =>
      resetUserPassword(id, password),
    onSuccess: async (updated) => {
      notifications.show({
        title: 'Password reset',
        message: `${firstName(updated.fullName)} will choose a new password the next time they sign in.`,
        color: 'emerald',
        icon: <IconCheck size={18} />,
      });
      setResetTarget(null);
      setResetPassword('');
      setError(null);
      await invalidate();
    },
    onError: (caught) => setError(caught),
  });

  const reactivate = (user: User) => {
    setError(null);
    updateMutation.mutate({ id: user.id, input: { active: true } });
  };

  const submitCreate = () => {
    setError(null);
    const next: Record<string, string> = {};
    if (!createForm.email.trim()) next.email = 'Email is required.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(createForm.email.trim())) {
      next.email = 'Enter a valid email address.';
    }
    if (!createForm.fullName.trim()) next.fullName = 'Full name is required.';
    if (createForm.password.length < 12) {
      next.password = 'Password must be at least 12 characters.';
    }
    setFieldErrors(next);
    if (Object.keys(next).length > 0) return;
    createMutation.mutate({
      email: createForm.email.trim(),
      fullName: createForm.fullName.trim(),
      password: createForm.password,
      role: createForm.role,
    });
  };

  const submitEdit = () => {
    if (!editTarget) return;
    setError(null);
    if (!editForm.fullName.trim()) {
      setFieldErrors({ fullName: 'Full name is required.' });
      return;
    }
    setFieldErrors({});
    updateMutation.mutate({
      id: editTarget.id,
      input: {
        fullName: editForm.fullName.trim(),
        role: editForm.role,
        active: editForm.active,
      },
    });
  };

  return (
    <>
      <PageHeader
        title="Users"
        description="Owner-only staff access. Deactivated users keep their history but cannot sign in."
        actions={
          <>
            <Button
              variant="default"
              leftSection={<IconRefresh size={16} />}
              onClick={() => void query.refetch()}
              loading={query.isFetching}
            >
              Refresh
            </Button>
            <Button
              leftSection={<IconPlus size={16} />}
              onClick={() => {
                setError(null);
                setFieldErrors({});
                setCreateOpen(true);
              }}
            >
              Add user
            </Button>
          </>
        }
      />

      <Box className="sl-summary-strip" mb="md" aria-label="User summary">
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(summary.total)}</Text>
          <Text className="sl-summary-label">Users</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(summary.owners)}</Text>
          <Text className="sl-summary-label">Active owners</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(summary.staff)}</Text>
          <Text className="sl-summary-label">Active staff</Text>
        </Box>
        <Box className="sl-summary-item">
          <Text className="sl-summary-value">{formatQuantity(summary.inactive)}</Text>
          <Text className="sl-summary-label">Inactive</Text>
        </Box>
      </Box>

      {error && !createOpen && !editTarget && !resetTarget ? (
        <ProblemAlert error={error} title="User administration failed" />
      ) : null}

      {query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : query.isLoading ? (
        <TableSkeleton rows={4} columns={5} />
      ) : (
        <Table.ScrollContainer minWidth={760}>
          <Table className="sl-data-table" captionSide="top">
            <Table.Caption className="sl-table-caption">
              Active and inactive accounts. Self-deactivation and removal of the last active owner are
              rejected by the server.
            </Table.Caption>
            <Table.Thead>
              <Table.Tr>
                <Table.Th scope="col">Name</Table.Th>
                <Table.Th scope="col">Email</Table.Th>
                <Table.Th scope="col">Role</Table.Th>
                <Table.Th scope="col">Status</Table.Th>
                <Table.Th scope="col" ta="right">
                  Actions
                </Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {users.map((user) => {
                const self = currentUser?.id === user.id;
                return (
                  <Table.Tr key={user.id}>
                    <Table.Th scope="row" fw={600}>
                      <Group gap="xs">
                        <Text size="sm">{user.fullName}</Text>
                        {self ? (
                          <Badge size="xs" variant="light" color="emerald">
                            You
                          </Badge>
                        ) : null}
                      </Group>
                    </Table.Th>
                    <Table.Td>
                      <Text size="sm" className="sl-code">
                        {user.email}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <Badge color={user.role === 'OWNER' ? 'emerald' : 'slate'} variant="light" size="sm">
                        {user.role === 'OWNER' ? 'Owner' : 'Staff'}
                      </Badge>
                    </Table.Td>
                    <Table.Td>
                      <Group gap={4} wrap="wrap">
                        <Badge color={user.active ? 'green' : 'gray'} variant="light" size="sm">
                          {user.active ? 'Active' : 'Inactive'}
                        </Badge>
                        {user.mustChangePassword ? (
                          <Badge color="orange" variant="light" size="sm">
                            Password change pending
                          </Badge>
                        ) : null}
                      </Group>
                    </Table.Td>
                    <Table.Td ta="right">
                      <Group gap={4} justify="flex-end" wrap="nowrap">
                        <Button size="compact-xs" variant="subtle" onClick={() => openEdit(user)}>
                          Edit
                        </Button>
                        <Tooltip
                          label="Use the account menu to change your own password."
                          withArrow
                          disabled={!self}
                        >
                          <Box component="span" style={{ display: 'inline-flex' }}>
                            <Button
                              size="compact-xs"
                              variant="subtle"
                              leftSection={<IconKey size={13} />}
                              disabled={self}
                              onClick={() => openReset(user)}
                            >
                              Reset password
                            </Button>
                          </Box>
                        </Tooltip>
                        {user.active ? (
                          <Button
                            size="compact-xs"
                            variant="subtle"
                            color="red"
                            leftSection={<IconUserOff size={13} />}
                            disabled={self}
                            onClick={() => {
                              setError(null);
                              setDeactivateTarget(user);
                            }}
                          >
                            Deactivate
                          </Button>
                        ) : (
                          <Button
                            size="compact-xs"
                            variant="subtle"
                            color="emerald"
                            leftSection={<IconUserCheck size={13} />}
                            loading={updateMutation.isPending}
                            onClick={() => reactivate(user)}
                          >
                            Reactivate
                          </Button>
                        )}
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                );
              })}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}

      <Modal
        opened={createOpen}
        onClose={() => {
          if (!createMutation.isPending) setCreateOpen(false);
        }}
        title="Add user"
        size="md"
      >
        <Stack gap="md">
          <TextInput
            label="Full name"
            required
            value={createForm.fullName}
            onChange={(event) => setCreateForm({ ...createForm, fullName: event.currentTarget.value })}
            error={fieldErrors.fullName}
            placeholder="Astrid Lindgren"
          />
          <TextInput
            label="Email"
            type="email"
            required
            value={createForm.email}
            onChange={(event) => setCreateForm({ ...createForm, email: event.currentTarget.value })}
            error={fieldErrors.email}
            placeholder="astrid@stockledger.io"
          />
          <Box>
            <PasswordInput
              label="Temporary password"
              description="At least 12 characters. Shown once — share it securely."
              required
              value={createForm.password}
              onChange={(event) => setCreateForm({ ...createForm, password: event.currentTarget.value })}
              error={fieldErrors.password}
            />
            <Button
              mt="xs"
              size="compact-xs"
              variant="subtle"
              leftSection={<IconKey size={13} />}
              onClick={() =>
                setCreateForm({ ...createForm, password: generateTemporaryPassword() })
              }
            >
              Generate temporary password
            </Button>
          </Box>
          <Select
            label="Role"
            value={createForm.role}
            onChange={(value) => setCreateForm({ ...createForm, role: (value as Role) ?? 'STAFF' })}
            data={[
              { value: 'STAFF', label: 'Staff — operations only' },
              { value: 'OWNER', label: 'Owner — full access' },
            ]}
            allowDeselect={false}
            required
          />
          {error ? <ProblemAlert error={error} title="Could not create user" compact /> : null}
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setCreateOpen(false)} disabled={createMutation.isPending}>
              Cancel
            </Button>
            <Button onClick={submitCreate} loading={createMutation.isPending}>
              Create user
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal
        opened={editTarget !== null}
        onClose={() => {
          if (!updateMutation.isPending) setEditTarget(null);
        }}
        title="Edit user"
        size="md"
      >
        <Stack gap="md">
          <Title order={3} size="h5">
            {editTarget?.email}
          </Title>
          <TextInput
            label="Full name"
            required
            value={editForm.fullName}
            onChange={(event) => setEditForm({ ...editForm, fullName: event.currentTarget.value })}
            error={fieldErrors.fullName}
          />
          <Select
            label="Role"
            value={editForm.role}
            onChange={(value) => setEditForm({ ...editForm, role: (value as Role) ?? 'STAFF' })}
            data={[
              { value: 'STAFF', label: 'Staff — operations only' },
              { value: 'OWNER', label: 'Owner — full access' },
            ]}
            allowDeselect={false}
          />
          <Switch
            label="Active"
            description={
              currentUser?.id === editTarget?.id
                ? 'You cannot deactivate your own account.'
                : 'Inactive users keep their history but cannot sign in.'
            }
            checked={editForm.active}
            disabled={currentUser?.id === editTarget?.id}
            onChange={(event) => setEditForm({ ...editForm, active: event.currentTarget.checked })}
          />
          {error ? <ProblemAlert error={error} title="Could not update user" compact /> : null}
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setEditTarget(null)} disabled={updateMutation.isPending}>
              Cancel
            </Button>
            <Button onClick={submitEdit} loading={updateMutation.isPending}>
              Save changes
            </Button>
          </Group>
        </Stack>
      </Modal>

      {resetTarget ? (
        <Modal
          opened
          onClose={() => {
            if (!resetMutation.isPending) {
              setResetTarget(null);
              setResetPassword('');
            }
          }}
          title="Reset password"
          size="md"
        >
          <Stack gap="md">
            <Text size="sm" c="dimmed">
              {`Set a one-time temporary password for ${resetTarget.fullName}. It is shown once, so copy it now and share it securely.`}
            </Text>
            <TextInput
              label="Temporary password"
              value={resetPassword}
              readOnly
              styles={{ input: { fontFamily: 'var(--mantine-font-family-monospace)' } }}
              rightSection={
                <CopyButton value={resetPassword} timeout={1500}>
                  {({ copied, copy }) => (
                    <Tooltip label={copied ? 'Copied' : 'Copy'} withArrow>
                      <ActionIcon
                        variant="subtle"
                        color={copied ? 'emerald' : 'gray'}
                        aria-label={copied ? 'Temporary password copied' : 'Copy temporary password'}
                        onClick={copy}
                      >
                        {copied ? <IconCheck size={14} /> : <IconCopy size={14} />}
                      </ActionIcon>
                    </Tooltip>
                  )}
                </CopyButton>
              }
            />
            <Group>
              <Button
                size="compact-xs"
                variant="subtle"
                leftSection={<IconRefresh size={13} />}
                onClick={() => setResetPassword(generateTemporaryPassword())}
              >
                Generate another
              </Button>
            </Group>
            {error ? <ProblemAlert error={error} title="Could not reset password" compact /> : null}
            <Group justify="flex-end">
              <Button
                variant="default"
                onClick={() => {
                  setResetTarget(null);
                  setResetPassword('');
                }}
                disabled={resetMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                onClick={() =>
                  resetMutation.mutate({ id: resetTarget.id, password: resetPassword })
                }
                loading={resetMutation.isPending}
              >
                Reset password
              </Button>
            </Group>
          </Stack>
        </Modal>
      ) : null}

      <ConfirmDialog
        opened={deactivateTarget !== null}
        title="Deactivate user?"
        confirmLabel="Deactivate"
        danger
        loading={deactivateMutation.isPending}
        onClose={() => setDeactivateTarget(null)}
        onConfirm={() => {
          if (deactivateTarget) deactivateMutation.mutate(deactivateTarget.id);
        }}
      >
        {deactivateTarget
          ? `${deactivateTarget.fullName} will no longer be able to sign in. Their ledger history stays intact.`
          : null}
      </ConfirmDialog>
    </>
  );
}
