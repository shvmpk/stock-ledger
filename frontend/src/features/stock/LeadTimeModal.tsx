import { useState } from 'react';
import { Button, Group, Loader, Modal, NumberInput, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconCheck } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateReorderSettings } from '../../lib/api/endpoints/reorder';
import { queryKeys } from '../../lib/query/keys';
import { ProblemAlert } from '../../components/feedback/ProblemAlert';

export function LeadTimeModal({
  initialLeadTimeDays,
  targetCoverDays,
  onClose,
}: {
  initialLeadTimeDays: number;
  targetCoverDays: number;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  // The page mounts this modal only while it is open, so the initial value is
  // always the lead time the owner last saved rather than stale local state.
  const [leadTime, setLeadTime] = useState<number | string>(initialLeadTimeDays);
  const [error, setError] = useState<unknown>(null);

  const mutation = useMutation({
    mutationFn: (leadTimeDays: number) => updateReorderSettings({ leadTimeDays }),
    onSuccess: async (saved) => {
      notifications.show({
        title: 'Lead time saved',
        message: `Suggestions now cover a ${saved.leadTimeDays}-day supplier lead time plus ${targetCoverDays} days of cover.`,
        color: 'emerald',
        icon: <IconCheck size={18} />,
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.reorder }),
        queryClient.invalidateQueries({ queryKey: queryKeys.reorderSettings }),
      ]);
      onClose();
    },
    onError: (caught) => setError(caught),
  });

  const save = () => {
    setError(null);
    const days = typeof leadTime === 'number' ? leadTime : Number(leadTime);
    if (!Number.isInteger(days) || days < 1 || days > 365) {
      setError(new Error('Supplier lead time must be between 1 and 365 days.'));
      return;
    }
    mutation.mutate(days);
  };

  return (
    <Modal
      opened
      onClose={() => {
        if (!mutation.isPending) onClose();
      }}
      title="Reorder assumptions"
      size="sm"
    >
      <Stack gap="md">
        <Text size="sm" c="dimmed">
          Buying suggestions cover the supplier lead time plus {targetCoverDays} days of target
          cover. Editing the lead time changes every suggestion immediately.
        </Text>
        <NumberInput
          label="Supplier lead time (days)"
          description="How long stock usually takes to arrive after ordering."
          min={1}
          max={365}
          value={leadTime}
          onChange={setLeadTime}
          required
        />
        {error ? <ProblemAlert error={error} title="Could not save lead time" compact /> : null}
        {mutation.isPending ? (
          <Group gap="xs">
            <Loader size="xs" />
            <Text size="xs" c="dimmed">
              Saving lead time…
            </Text>
          </Group>
        ) : null}
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button onClick={save} loading={mutation.isPending}>
            Save lead time
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
