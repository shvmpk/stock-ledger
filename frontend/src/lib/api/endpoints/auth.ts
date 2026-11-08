import { apiGet, apiPost } from '../client';
import type { User } from '../types';

export interface LoginInput {
  email: string;
  password: string;
}

export interface ChangePasswordInput {
  currentPassword?: string;
  newPassword: string;
}

export function login(input: LoginInput): Promise<User> {
  return apiPost<User>('/auth/login', input);
}

export function fetchCurrentUser(): Promise<User> {
  return apiGet<User>('/auth/me');
}

export function logout(): Promise<{ message: string }> {
  return apiPost<{ message: string }>('/auth/logout');
}

export function changePassword(input: ChangePasswordInput): Promise<User> {
  return apiPost<User>('/auth/password', input);
}
