import { Center, Loader } from '@mantine/core';

export function RouteFallback() {
  return (
    <Center mih="50vh" role="status" aria-live="polite">
      <Loader color="emerald" aria-label="Loading page" />
    </Center>
  );
}
