import type { ReactNode } from 'react';
import { Button, Group, Modal, Text } from '@mantine/core';

export function ConfirmDialog({
  opened,
  title,
  children,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  danger = false,
  loading = false,
  onConfirm,
  onClose,
}: {
  opened: boolean;
  title: string;
  children?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Modal opened={opened} onClose={onClose} title={title} size="sm">
      <Text size="sm" mb="lg">
        {children}
      </Text>
      <Group justify="flex-end">
        <Button variant="default" onClick={onClose} disabled={loading}>
          {cancelLabel}
        </Button>
        <Button
          color={danger ? 'red' : 'emerald'}
          loading={loading}
          onClick={onConfirm}
        >
          {confirmLabel}
        </Button>
      </Group>
    </Modal>
  );
}
