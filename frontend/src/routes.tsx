import { lazy, Suspense, type ReactNode } from 'react';
import { Navigate, Outlet, createBrowserRouter, type RouteObject } from 'react-router-dom';
import { AuthProvider } from './auth/AuthProvider';
import { RequireAuth } from './auth/RequireAuth';
import { RequireRole } from './auth/RequireRole';
import { AppLayout } from './components/layout/AppLayout';
import { OrdersLayout } from './features/orders/OrdersLayout';
import { ForbiddenPage } from './auth/RequireRole';
import { RouteFallback } from './RouteSuspense';
import { setPasswordChangeRedirectHandler } from './lib/api/passwordChangeRedirect';
import {
  PASSWORD_CHANGE_PATH,
  currentPathWithSearch,
  passwordChangePath,
} from './lib/navigation/returnTo';

const LoginPage = lazy(() =>
  import('./features/auth/LoginPage').then((module) => ({ default: module.LoginPage })),
);
const ChangePasswordPage = lazy(() =>
  import('./features/auth/ChangePasswordPage').then((module) => ({
    default: module.ChangePasswordPage,
  })),
);
const SecurityLogPage = lazy(() =>
  import('./features/settings/SecurityLogPage').then((module) => ({
    default: module.SecurityLogPage,
  })),
);
const AllProductsPage = lazy(() =>
  import('./features/stock/AllProductsPage').then((module) => ({ default: module.AllProductsPage })),
);
const ProductFormPage = lazy(() =>
  import('./features/stock/ProductFormPage').then((module) => ({
    default: module.ProductFormPage,
  })),
);
const ReorderPage = lazy(() =>
  import('./features/stock/ReorderPage').then((module) => ({ default: module.ReorderPage })),
);
const StockCountPage = lazy(() =>
  import('./features/stock/StockCountPage').then((module) => ({ default: module.StockCountPage })),
);
const OrderQueuePage = lazy(() =>
  import('./features/orders/OrderQueuePage').then((module) => ({ default: module.OrderQueuePage })),
);
const MovementsPage = lazy(() =>
  import('./features/history/MovementsPage').then((module) => ({ default: module.MovementsPage })),
);
const ShippingSummaryPage = lazy(() =>
  import('./features/history/ShippingSummaryPage').then((module) => ({
    default: module.ShippingSummaryPage,
  })),
);
const ShippingRatesPage = lazy(() =>
  import('./features/settings/ShippingRatesPage').then((module) => ({
    default: module.ShippingRatesPage,
  })),
);
const PlatformsPage = lazy(() =>
  import('./features/settings/PlatformsPage').then((module) => ({ default: module.PlatformsPage })),
);
const AddPlatformPage = lazy(() =>
  import('./features/settings/AddPlatformPage').then((module) => ({
    default: module.AddPlatformPage,
  })),
);
const UsersPage = lazy(() =>
  import('./features/settings/UsersPage').then((module) => ({ default: module.UsersPage })),
);
const ExportsPage = lazy(() =>
  import('./features/settings/ExportsPage').then((module) => ({ default: module.ExportsPage })),
);
const NotFoundPage = lazy(() =>
  import('./features/NotFoundPage').then((module) => ({ default: module.NotFoundPage })),
);

function lazyPage(node: ReactNode) {
  return <Suspense fallback={<RouteFallback />}>{node}</Suspense>;
}

export const routeObjects: RouteObject[] = [
  {
    element: (
      <AuthProvider>
        <Outlet />
      </AuthProvider>
    ),
    children: [
      {
        path: '/login',
        element: lazyPage(<LoginPage />),
      },
      {
        path: '/',
        element: <RequireAuth />,
        children: [
          {
            path: 'account/password',
            element: lazyPage(<ChangePasswordPage />),
          },
          {
            element: <AppLayout />,
            children: [
              { index: true, element: <Navigate to="/stock/products" replace /> },
              { path: 'stock/products', element: lazyPage(<AllProductsPage />) },
              { path: 'stock/reorder', element: lazyPage(<ReorderPage />) },
              { path: 'stock/low-stock', element: <Navigate to="/stock/reorder" replace /> },
              { path: 'stock/count', element: lazyPage(<StockCountPage />) },
              {
                path: 'orders',
                element: <OrdersLayout />,
                children: [
                  { index: true, element: <Navigate to="new" replace /> },
                  { path: ':queue', element: lazyPage(<OrderQueuePage />) },
                ],
              },
              { path: 'history/movements', element: lazyPage(<MovementsPage />) },
              {
                element: <RequireRole roles={['OWNER']} />,
                children: [
                  { path: 'history/shipping', element: lazyPage(<ShippingSummaryPage />) },
                  { path: 'settings/rates', element: lazyPage(<ShippingRatesPage />) },
                  { path: 'settings/platforms', element: lazyPage(<PlatformsPage />) },
                  { path: 'settings/platforms/new', element: lazyPage(<AddPlatformPage />) },
                  { path: 'stock/products/new', element: lazyPage(<ProductFormPage />) },
                  {
                    path: 'stock/products/:productId/edit',
                    element: lazyPage(<ProductFormPage />),
                  },
                  { path: 'settings/users', element: lazyPage(<UsersPage />) },
                  { path: 'settings/security-log', element: lazyPage(<SecurityLogPage />) },
                  { path: 'settings/exports', element: lazyPage(<ExportsPage />) },
                ],
              },
              { path: 'forbidden', element: <ForbiddenPage /> },
              { path: '*', element: lazyPage(<NotFoundPage />) },
            ],
          },
        ],
      },
    ],
  },
];

export const router = createBrowserRouter(routeObjects);

setPasswordChangeRedirectHandler(() => {
  const { pathname, search } = router.state.location;
  if (pathname === PASSWORD_CHANGE_PATH) return;
  void router.navigate(passwordChangePath(currentPathWithSearch(pathname, search)));
});
