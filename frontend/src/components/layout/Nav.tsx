import { NavLink as RouterNavLink, useLocation } from 'react-router-dom';
import { NavLink, Stack, Text } from '@mantine/core';
import {
  IconAlertTriangle,
  IconArrowBackUp,
  IconBox,
  IconChartBar,
  IconClipboardList,
  IconClock,
  IconCurrencyPound,
  IconDownload,
  IconHistory,
  IconPackage,
  IconPlugConnected,
  IconShieldLock,
  IconShoppingCart,
  IconTruck,
  IconUsers,
} from '@tabler/icons-react';
import { useAuth } from '../../auth/useAuth';

function isPath(pathname: string, to: string): boolean {
  return pathname === to || pathname.startsWith(`${to}/`);
}

export function Nav({ onNavigate }: { onNavigate: () => void }) {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const owner = user?.role === 'OWNER';

  return (
    <Stack gap={2}>
      <Text size="10px" c="dimmed" tt="uppercase" fw={700} px="sm" pt="xs" style={{ letterSpacing: '0.08em' }}>
        Stock
      </Text>
      <NavLink
        component={RouterNavLink}
        to="/stock/products"
        label="All products"
        leftSection={<IconBox size={17} />}
        active={isPath(pathname, '/stock/products')}
        onClick={onNavigate}
      />
      <NavLink
        component={RouterNavLink}
        to="/stock/reorder"
        label="Reorder"
        leftSection={<IconShoppingCart size={17} />}
        active={isPath(pathname, '/stock/reorder')}
        onClick={onNavigate}
      />
      <NavLink
        component={RouterNavLink}
        to="/stock/count"
        label="Stock count"
        leftSection={<IconClipboardList size={17} />}
        active={isPath(pathname, '/stock/count')}
        onClick={onNavigate}
      />

      <Text size="10px" c="dimmed" tt="uppercase" fw={700} px="sm" pt="md" style={{ letterSpacing: '0.08em' }}>
        Orders
      </Text>
      <NavLink
        component={RouterNavLink}
        to="/orders/new"
        label="New"
        leftSection={<IconClock size={17} />}
        active={isPath(pathname, '/orders/new')}
        onClick={onNavigate}
      />
      <NavLink
        component={RouterNavLink}
        to="/orders/waiting"
        label="Waiting for stock"
        leftSection={<IconAlertTriangle size={17} />}
        active={isPath(pathname, '/orders/waiting')}
        onClick={onNavigate}
      />
      <NavLink
        component={RouterNavLink}
        to="/orders/ready"
        label="Ready to ship"
        leftSection={<IconPackage size={17} />}
        active={isPath(pathname, '/orders/ready')}
        onClick={onNavigate}
      />
      <NavLink
        component={RouterNavLink}
        to="/orders/shipped"
        label="Shipped"
        leftSection={<IconTruck size={17} />}
        active={isPath(pathname, '/orders/shipped')}
        onClick={onNavigate}
      />
      <NavLink
        component={RouterNavLink}
        to="/orders/returns"
        label="Returns & cancellations"
        leftSection={<IconArrowBackUp size={17} />}
        active={isPath(pathname, '/orders/returns')}
        onClick={onNavigate}
      />

      <Text size="10px" c="dimmed" tt="uppercase" fw={700} px="sm" pt="md" style={{ letterSpacing: '0.08em' }}>
        History
      </Text>
      <NavLink
        component={RouterNavLink}
        to="/history/movements"
        label="Stock movements"
        leftSection={<IconHistory size={17} />}
        active={isPath(pathname, '/history/movements')}
        onClick={onNavigate}
      />
      {owner ? (
        <NavLink
          component={RouterNavLink}
          to="/history/shipping"
          label="Shipping summary"
          leftSection={<IconChartBar size={17} />}
          active={isPath(pathname, '/history/shipping')}
          onClick={onNavigate}
        />
      ) : null}

      {owner ? (
        <>
          <Text
            size="10px"
            c="dimmed"
            tt="uppercase"
            fw={700}
            px="sm"
            pt="md"
            style={{ letterSpacing: '0.08em' }}
          >
            Owner settings
          </Text>
          <NavLink
            component={RouterNavLink}
            to="/settings/rates"
            label="Shipping rates"
            leftSection={<IconCurrencyPound size={17} />}
            active={isPath(pathname, '/settings/rates')}
            onClick={onNavigate}
          />
          <NavLink
            component={RouterNavLink}
            to="/settings/platforms"
            label="Platforms"
            leftSection={<IconPlugConnected size={17} />}
            active={isPath(pathname, '/settings/platforms')}
            onClick={onNavigate}
          />
          <NavLink
            component={RouterNavLink}
            to="/settings/users"
            label="Users"
            leftSection={<IconUsers size={17} />}
            active={isPath(pathname, '/settings/users')}
            onClick={onNavigate}
          />
          <NavLink
            component={RouterNavLink}
            to="/settings/security-log"
            label="Security log"
            leftSection={<IconShieldLock size={17} />}
            active={isPath(pathname, '/settings/security-log')}
            onClick={onNavigate}
          />
          <NavLink
            component={RouterNavLink}
            to="/settings/exports"
            label="Exports"
            leftSection={<IconDownload size={17} />}
            active={isPath(pathname, '/settings/exports')}
            onClick={onNavigate}
          />
        </>
      ) : null}
    </Stack>
  );
}
