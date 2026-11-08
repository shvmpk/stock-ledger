import { ActionIcon, TextInput } from '@mantine/core';
import { IconSearch, IconX } from '@tabler/icons-react';

export function SearchInput({
  value,
  onChange,
  placeholder = 'Search',
  label,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  label?: string;
}) {
  return (
    <TextInput
      value={value}
      onChange={(event) => onChange(event.currentTarget.value)}
      placeholder={placeholder}
      aria-label={label ?? placeholder}
      leftSection={<IconSearch size={16} aria-hidden="true" />}
      rightSection={
        value ? (
          <ActionIcon
            variant="subtle"
            color="gray"
            size="sm"
            aria-label="Clear search"
            onClick={() => onChange('')}
          >
            <IconX size={14} />
          </ActionIcon>
        ) : null
      }
    />
  );
}
