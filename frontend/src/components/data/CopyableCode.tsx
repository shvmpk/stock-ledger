import { ActionIcon, CopyButton, Group, Text, Tooltip } from '@mantine/core';
import { IconCheck, IconCopy } from '@tabler/icons-react';

export function CopyableCode({ value, label }: { value: string; label: string }) {
  return (
    <CopyButton value={value} timeout={1500}>
      {({ copied, copy }) => (
        <Group gap={4} wrap="nowrap">
          <Text span className="sl-code">
            {value}
          </Text>
          <Tooltip label={copied ? `${label} copied` : `Copy ${label.toLowerCase()}`} withArrow>
            <ActionIcon
              size="xs"
              variant="subtle"
              color={copied ? 'emerald' : 'gray'}
              aria-label={copied ? `${label} copied` : `Copy ${label.toLowerCase()}`}
              onClick={copy}
            >
              {copied ? <IconCheck size={12} /> : <IconCopy size={12} />}
            </ActionIcon>
          </Tooltip>
        </Group>
      )}
    </CopyButton>
  );
}
