import { http, HttpResponse } from 'msw';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { UsersPage } from './UsersPage';
import { ownerUser, staffUser } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';

async function openCreateDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'Add user' }));
  return screen.findByRole('dialog', { name: 'Add user' });
}

describe('UsersPage', () => {
  it('shows the user summary and disables self-deactivation', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UsersPage />, { route: '/settings/users' });

    expect(await screen.findByRole('heading', { name: 'Users' })).toBeInTheDocument();
    const ownerRow = await screen.findByRole('row', { name: /Mara Ellison/ });
    const summary = screen.getByLabelText('User summary');
    expect(summary).toHaveTextContent('3');
    expect(summary).toHaveTextContent('Users');
    expect(summary).toHaveTextContent('Active owners');
    expect(summary).toHaveTextContent('Inactive');

    expect(within(ownerRow).getByRole('button', { name: 'Deactivate' })).toBeDisabled();
    await user.click(within(ownerRow).getByRole('button', { name: 'Edit' }));

    const dialog = await screen.findByRole('dialog', { name: 'Edit user' });
    expect(within(dialog).getByRole('switch', { name: /Active/ })).toBeDisabled();
    expect(within(dialog).getByText('You cannot deactivate your own account.')).toBeInTheDocument();
  });

  it('validates a new user and creates the account', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UsersPage />, { route: '/settings/users' });
    await screen.findByRole('row', { name: /Mara Ellison/ });

    const dialog = await openCreateDialog(user);
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }));

    expect(await within(dialog).findByText('Full name is required.')).toBeInTheDocument();
    expect(within(dialog).getByText('Email is required.')).toBeInTheDocument();
    expect(within(dialog).getByText('Password must be at least 12 characters.')).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText(/Full name/), 'Astrid Lindgren');
    await user.type(within(dialog).getByLabelText(/Email/), 'astrid.lindgren@stockledger.io');
    await user.type(within(dialog).getByLabelText(/Temporary password/), 'NordicStock2026!');
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }));

    expect(await screen.findByText('User created')).toBeInTheDocument();
    expect(
      await screen.findByRole('row', { name: /Astrid Lindgren/ }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('User summary')).toHaveTextContent('4');
  });

  it('surfaces the duplicate-email rejection inside the create dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UsersPage />, { route: '/settings/users' });
    await screen.findByRole('row', { name: /Mara Ellison/ });

    const dialog = await openCreateDialog(user);
    await user.type(within(dialog).getByLabelText(/Full name/), 'Astrid Lindgren');
    await user.type(within(dialog).getByLabelText(/Email/), ownerUser.email);
    await user.type(within(dialog).getByLabelText(/Temporary password/), 'NordicStock2026!');
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }));

    expect(await within(dialog).findByText('Could not create user')).toBeInTheDocument();
    expect(within(dialog).getByText('A user with that email already exists.')).toBeInTheDocument();
    expect(within(dialog).getByText('DUPLICATE_RESOURCE')).toBeInTheDocument();
  });

  it('edits a staff member, deactivates another, and reactivates them', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UsersPage />, { route: '/settings/users' });
    await screen.findByRole('row', { name: /Theo Marsh/ });

    await user.click(
      within(screen.getByRole('row', { name: /Theo Marsh/ })).getByRole('button', { name: 'Edit' }),
    );
    const editDialog = await screen.findByRole('dialog', { name: 'Edit user' });
    const fullName = within(editDialog).getByLabelText(/Full name/);
    await user.clear(fullName);
    await user.type(fullName, 'Theo Marsh-Bell');
    await user.click(within(editDialog).getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('User updated')).toBeInTheDocument();
    expect(await screen.findByRole('row', { name: /Theo Marsh-Bell/ })).toBeInTheDocument();

    const staffRow = screen.getByRole('row', { name: /Theo Marsh-Bell/ });
    await user.click(within(staffRow).getByRole('button', { name: 'Deactivate' }));
    const confirm = await screen.findByRole('dialog', { name: 'Deactivate user?' });
    expect(
      within(confirm).getByText(/Theo Marsh-Bell will no longer be able to sign in/),
    ).toBeInTheDocument();
    await user.click(within(confirm).getByRole('button', { name: 'Deactivate' }));

    expect(await screen.findByText('User deactivated')).toBeInTheDocument();
    await waitFor(() => {
      const inactiveRow = screen.getByRole('row', { name: /Theo Marsh-Bell/ });
      expect(within(inactiveRow).getByText('Inactive')).toBeInTheDocument();
      expect(within(inactiveRow).getByRole('button', { name: 'Reactivate' })).toBeInTheDocument();
    });

    await user.click(
      within(screen.getByRole('row', { name: /Theo Marsh-Bell/ })).getByRole('button', {
        name: 'Reactivate',
      }),
    );
    expect((await screen.findAllByText('User updated')).length).toBeGreaterThan(0);
    await waitFor(() =>
      expect(
        within(screen.getByRole('row', { name: /Theo Marsh-Bell/ })).getByText('Active'),
      ).toBeInTheDocument(),
    );
  });

  it('generates a one-time temporary password and resets a staff account', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UsersPage />, { route: '/settings/users' });
    await screen.findByRole('row', { name: /Theo Marsh/ });

    const staffRow = screen.getByRole('row', { name: /Theo Marsh/ });
    await user.click(within(staffRow).getByRole('button', { name: 'Reset password' }));

    const dialog = await screen.findByRole('dialog', { name: 'Reset password' });
    const temporaryPassword = within(dialog).getByLabelText('Temporary password');
    const generated = (temporaryPassword as HTMLInputElement).value;
    expect(generated).toHaveLength(16);
    expect(generated).not.toMatch(/[0OIl1]/);
    expect(within(dialog).getByRole('button', { name: 'Copy temporary password' })).toBeInTheDocument();
    expect(within(dialog).queryByText(/sign out|signed out/i)).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Reset password' }));

    expect(
      await screen.findByText('Theo will choose a new password the next time they sign in.'),
    ).toBeInTheDocument();
    expect(
      within(await screen.findByRole('row', { name: /Theo Marsh/ })).getByText(
        'Password change pending',
      ),
    ).toBeInTheDocument();
    expect(within(dialog).queryByLabelText('Temporary password')).not.toBeInTheDocument();
  });

  it('shows the pending chip and disables reset on the owner own row', async () => {
    const user = userEvent.setup();
    server.use(
      http.get('/api/v1/admin/users', () =>
        HttpResponse.json({
          success: true,
          data: [
            { ...ownerUser, mustChangePassword: true },
            { ...staffUser, mustChangePassword: true },
          ],
          meta: { timestamp: new Date().toISOString() },
        }),
      ),
    );

    renderWithProviders(<UsersPage />, { route: '/settings/users' });
    const ownerRow = await screen.findByRole('row', { name: /Mara Ellison/ });
    expect(within(ownerRow).getByText('Password change pending')).toBeInTheDocument();

    const ownReset = within(ownerRow).getByRole('button', { name: 'Reset password' });
    expect(ownReset).toBeDisabled();
    await user.hover(ownReset);
    expect(
      await screen.findByText('Use the account menu to change your own password.'),
    ).toBeInTheDocument();

    const staffRow = screen.getByRole('row', { name: /Theo Marsh/ });
    expect(within(staffRow).getByText('Password change pending')).toBeInTheDocument();
  });

  it('surfaces the last-active-owner guard from the API', async () => {
    server.use(
      http.patch('/api/v1/admin/users/:userId', () =>
        HttpResponse.json(
          {
            type: 'https://api.stockledger.io/errors/last-active-owner',
            title: 'Last Owner',
            status: 409,
            detail: 'You cannot deactivate the last active owner account.',
            code: 'LAST_ACTIVE_OWNER',
            timestamp: '2026-10-23T09:00:00Z',
            invalidParams: [],
          },
          { status: 409 },
        ),
      ),
    );

    const user = userEvent.setup();
    renderWithProviders(<UsersPage />, { route: '/settings/users' });
    await screen.findByRole('row', { name: /Mara Ellison/ });

    await user.click(
      within(screen.getByRole('row', { name: /Mara Ellison/ })).getByRole('button', { name: 'Edit' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Edit user' });
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));

    expect(await within(dialog).findByText('Could not update user')).toBeInTheDocument();
    expect(
      within(dialog).getByText('You cannot deactivate the last active owner account.'),
    ).toBeInTheDocument();
    expect(within(dialog).getByText('LAST_ACTIVE_OWNER')).toBeInTheDocument();
  });
});
