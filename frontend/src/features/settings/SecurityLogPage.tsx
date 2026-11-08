import { useMemo, useState } from 'react';
import { Button, Group, Select, Stack, Text, TextInput, Tooltip } from '@mantine/core';
import { IconFilterOff, IconSearch, IconShieldLock } from '@tabler/icons-react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { listAuthEvents } from '../../lib/api/endpoints/authEvents';
import { listUsers } from '../../lib/api/endpoints/users';
import type { AuthEventType, AuthEvent, User } from '../../lib/api/types';
import { queryKeys } from '../../lib/query/keys';
import { formatDateTime } from '../../lib/format/date';
import { formatRelativeTime } from '../../lib/format/relativeTime';
import { PageHeader } from '../../components/feedback/PageHeader';
import { ErrorState } from '../../components/feedback/ErrorState';
import { EmptyState } from '../../components/feedback/EmptyState';
import { DataTable, MobileRow, type DataTableColumn } from '../../components/data/DataTable';
import { FilterBar } from '../../components/data/FilterBar';
import { TablePagination } from '../../components/data/TablePagination';
import { CopyableCode } from '../../components/data/CopyableCode';
import {
  SECURITY_EVENT_TYPES,
  SIGN_IN_EVENT_TYPES,
  describeAuthEvent,
  isLoopbackAddress,
} from './securityLog';

const PAGE_SIZE = 20;
const DAY_MS = 24 * 60 * 60 * 1000;
const CAPTION = 'Security events, newest first.';

type Scope = 'security' | 'signins' | 'all';
type Range = '7d' | '30d' | 'all';

function windowFor(range: Range): { from: string; to: string } | undefined {
  if (range === 'all') return undefined;
  const days = range === '7d' ? 7 : 30;
  const to = new Date();
  const from = new Date(to.getTime() - days * DAY_MS);
  return { from: from.toISOString(), to: to.toISOString() };
}

function eventTypesFor(scope: Scope): AuthEventType[] | undefined {
  if (scope === 'security') return SECURITY_EVENT_TYPES;
  if (scope === 'signins') return SIGN_IN_EVENT_TYPES;
  return undefined;
}

function EventTime({ event }: { event: AuthEvent }) {
  const exact = formatDateTime(event.occurredAt);
  return (
    <Tooltip label={exact} withArrow>
      <time dateTime={event.occurredAt} title={exact}>
        {formatRelativeTime(event.occurredAt)}
      </time>
    </Tooltip>
  );
}

function DeviceLabel({ event }: { event: AuthEvent }) {
  if (isLoopbackAddress(event.ipAddress)) {
    return <Text size="sm">This device</Text>;
  }
  if (!event.ipAddress) {
    return (
      <Text size="sm" c="dimmed">
        Unknown device
      </Text>
    );
  }
  return <CopyableCode value={event.ipAddress} label="IP address" />;
}

export function SecurityLogPage() {
  const [page, setPage] = useState(0);
  const [scope, setScope] = useState<Scope>('security');
  const [range, setRange] = useState<Range>('30d');
  const [dateWindow, setDateWindow] = useState(() => windowFor('30d'));
  const [actorEmail, setActorEmail] = useState('');

  const eventTypes = eventTypesFor(scope);
  const filters = useMemo(
    () => ({
      page,
      size: PAGE_SIZE,
      ...(eventTypes ? { eventType: eventTypes } : {}),
      ...(actorEmail.trim() ? { actorEmail: actorEmail.trim() } : {}),
      ...(dateWindow ?? {}),
    }),
    [page, eventTypes, actorEmail, dateWindow],
  );

  const eventsQuery = useQuery({
    queryKey: queryKeys.authEvents(filters),
    queryFn: () => listAuthEvents(filters),
    placeholderData: keepPreviousData,
  });

  const usersQuery = useQuery({ queryKey: queryKeys.users, queryFn: listUsers });
  const users: User[] = usersQuery.data ?? [];

  const events = eventsQuery.data?.data ?? [];
  const pagination = eventsQuery.data?.pagination;

  const changeScope = (next: Scope) => {
    setScope(next);
    setPage(0);
  };

  const changeRange = (next: Range) => {
    setRange(next);
    setDateWindow(windowFor(next));
    setPage(0);
  };

  const changeActor = (next: string) => {
    setActorEmail(next);
    setPage(0);
  };

  const clearFilters = () => {
    setScope('security');
    setRange('30d');
    setDateWindow(windowFor('30d'));
    setActorEmail('');
    setPage(0);
  };

  const columns: Array<DataTableColumn<AuthEvent>> = [
    {
      key: 'time',
      header: 'Time',
      width: 170,
      render: (event) => <EventTime event={event} />,
    },
    {
      key: 'activity',
      header: 'Activity',
      render: (event) => <Text size="sm">{describeAuthEvent(event, users)}</Text>,
    },
    {
      key: 'device',
      header: 'Device',
      width: 220,
      render: (event) => <DeviceLabel event={event} />,
    },
  ];

  return (
    <>
      <PageHeader
        title="Security log"
        description="Owner-only record of sign-ins, password changes, and account administration. Times are shown relative to now."
        actions={
          <Button
            variant="default"
            leftSection={<IconFilterOff size={16} />}
            onClick={clearFilters}
          >
            Clear filters
          </Button>
        }
      />

      <FilterBar>
        <Select
          label="Activity"
          value={scope}
          onChange={(value) => changeScope((value as Scope) ?? 'security')}
          data={[
            { value: 'security', label: 'Sign-ins & security' },
            { value: 'signins', label: 'Sign-ins only' },
            { value: 'all', label: 'All activity' },
          ]}
          allowDeselect={false}
          w={230}
        />
        <Select
          label="Time range"
          value={range}
          onChange={(value) => changeRange((value as Range) ?? '30d')}
          data={[
            { value: '7d', label: 'Last 7 days' },
            { value: '30d', label: 'Last 30 days' },
            { value: 'all', label: 'All time' },
          ]}
          allowDeselect={false}
          w={180}
        />
        <TextInput
          label="User"
          placeholder="Filter by email"
          leftSection={<IconSearch size={16} aria-hidden="true" />}
          value={actorEmail}
          onChange={(event) => changeActor(event.currentTarget.value)}
          w={260}
        />
      </FilterBar>

      {eventsQuery.isError ? (
        <ErrorState
          error={eventsQuery.error}
          title="Security log could not load"
          onRetry={() => void eventsQuery.refetch()}
        />
      ) : (
        <>
          <DataTable
            data={events}
            columns={columns}
            getRowKey={(event) => event.id}
            caption={CAPTION}
            loading={eventsQuery.isLoading}
            minWidth={640}
            empty={
              <EmptyState
                title="No security events"
                description="No events match these filters yet. Widen the activity or time range."
                icon={<IconShieldLock size={22} />}
              />
            }
            mobileCard={(event) => (
              <Stack gap={6}>
                <Group justify="space-between" align="flex-start" gap="sm" wrap="nowrap">
                  <Text size="sm">{describeAuthEvent(event, users)}</Text>
                  <EventTime event={event} />
                </Group>
                <MobileRow label="Device">
                  <DeviceLabel event={event} />
                </MobileRow>
              </Stack>
            )}
          />
          <TablePagination
            page={pagination?.page ?? page}
            totalPages={pagination?.totalPages ?? 0}
            totalElements={pagination?.totalElements ?? 0}
            size={pagination?.size ?? PAGE_SIZE}
            onChange={setPage}
          />
        </>
      )}
    </>
  );
}
