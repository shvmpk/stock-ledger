import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { AppShell } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { Header } from './Header';
import { Nav } from './Nav';

export function AppLayout() {
  const [mobileOpened, { toggle: toggleMobile, close: closeMobile }] = useDisclosure(false);
  const [desktopOpened] = useState(true);

  return (
    <>
      <a className="sl-skip-link" href="#main">
        Skip to content
      </a>
      <AppShell
        header={{ height: 56 }}
        navbar={{
          width: 240,
          breakpoint: 'md',
          collapsed: { mobile: !mobileOpened, desktop: !desktopOpened },
        }}
        padding="md"
      >
        <AppShell.Header>
          <Header mobileOpened={mobileOpened} onToggleMobile={toggleMobile} />
        </AppShell.Header>
        <AppShell.Navbar p="xs">
          <Nav onNavigate={closeMobile} />
        </AppShell.Navbar>
        <AppShell.Main>
          <main id="main" className="sl-page">
            <Outlet />
          </main>
        </AppShell.Main>
      </AppShell>
    </>
  );
}
