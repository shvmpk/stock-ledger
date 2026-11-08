import { apiDelete, apiGet, apiPatch, apiPost } from '../client';
import type { CreateUserInput, UpdateUserInput, User } from '../types';

export function listUsers(): Promise<User[]> {
  return apiGet<User[]>('/admin/users');
}

export function createUser(input: CreateUserInput): Promise<User> {
  return apiPost<User>('/admin/users', input);
}

export function updateUser(userId: string, input: UpdateUserInput): Promise<User> {
  return apiPatch<User>(`/admin/users/${userId}`, input);
}

export function deactivateUser(userId: string): Promise<User> {
  return apiDelete<User>(`/admin/users/${userId}`);
}

export function resetUserPassword(userId: string, newPassword: string): Promise<User> {
  return apiPost<User>(`/admin/users/${userId}/password-reset`, { newPassword });
}
