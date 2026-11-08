import { MantineProvider, localStorageColorSchemeManager } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { createQueryClient } from './lib/query/client';
import { router } from './routes';
import { theme } from './theme/theme';

const queryClient = createQueryClient();
const colorSchemeManager = localStorageColorSchemeManager({ key: 'stockledger-color-scheme' });

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <MantineProvider
        theme={theme}
        defaultColorScheme="auto"
        colorSchemeManager={colorSchemeManager}
      >
        <Notifications position="top-right" limit={4} />
        <RouterProvider router={router} />
      </MantineProvider>
    </QueryClientProvider>
  );
}
