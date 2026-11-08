import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { DispatchModal } from './DispatchModal';
import { readyOrder } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';

describe('DispatchModal', () => {
  it('resolves the active shipping rate and dispatches the order', async () => {
    const user = userEvent.setup();
    const onShipped = vi.fn();
    renderWithProviders(
      <DispatchModal order={readyOrder} opened onClose={vi.fn()} onShipped={onShipped} />,
    );

    expect(await screen.findByText('Resolved shipping cost')).toBeInTheDocument();
    expect(screen.getByText('£5.80')).toBeInTheDocument();
    await user.type(screen.getByLabelText(/Tracking number/), '1234567890');
    await user.click(screen.getByRole('button', { name: 'Ship order' }));

    expect(await screen.findByText('Order dispatched')).toBeInTheDocument();
    expect(onShipped).toHaveBeenCalledWith(
      expect.objectContaining({
        status: 'SHIPPED',
        carrier: 'DHL',
        parcelSize: 'MEDIUM',
        trackingNumber: '1234567890',
        actualShippingCost: '5.80',
      }),
    );
  });

  it('requires a tracking number before dispatch', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <DispatchModal order={readyOrder} opened onClose={vi.fn()} onShipped={vi.fn()} />,
    );
    await screen.findByText('Resolved shipping cost');

    await user.click(screen.getByRole('button', { name: 'Ship order' }));

    expect(await screen.findByText('Tracking number is required.')).toBeInTheDocument();
  });

  it('requires an override when no active rate matches and then ships with the override', async () => {
    const user = userEvent.setup();
    const onShipped = vi.fn();
    renderWithProviders(
      <DispatchModal order={readyOrder} opened onClose={vi.fn()} onShipped={onShipped} />,
    );
    await screen.findByText('Resolved shipping cost');

    await user.click(screen.getAllByLabelText(/Carrier/)[0]);
    await user.click(await screen.findByText('Royal Mail'));
    await user.click(screen.getAllByLabelText(/Parcel size/)[0]);
    await user.click(await screen.findByText('Large'));

    expect(await screen.findByText('No active rate')).toBeInTheDocument();
    await user.type(screen.getByLabelText(/Tracking number/), 'RM884129003GB');
    await user.click(screen.getByRole('button', { name: 'Ship order' }));
    expect(
      await screen.findByText('No active shipping rate is configured for this carrier and parcel size.'),
    ).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Actual shipping cost override/), '7.20');
    await user.click(screen.getByRole('button', { name: 'Ship order' }));

    expect(await screen.findByText('Order dispatched')).toBeInTheDocument();
    expect(onShipped).toHaveBeenCalledWith(
      expect.objectContaining({
        carrier: 'ROYAL_MAIL',
        parcelSize: 'LARGE',
        actualShippingCost: '7.20',
      }),
    );
  });

  it('has no detectable accessibility violations', async () => {
    renderWithProviders(
      <DispatchModal order={readyOrder} opened onClose={vi.fn()} onShipped={vi.fn()} />,
    );
    await screen.findByText('Resolved shipping cost');
    expect(await axe(document.body)).toHaveNoViolations();
  });
});
