import { apiGet, apiPost, apiPut } from '../client';
import type { Product, ProductInput } from '../types';

export function listProducts(includeRetired = false): Promise<Product[]> {
  return apiGet<Product[]>('/products', includeRetired ? { includeRetired: true } : undefined);
}

export function getProduct(productId: string): Promise<Product> {
  return apiGet<Product>(`/products/${productId}`);
}

export function createProduct(input: ProductInput): Promise<Product> {
  return apiPost<Product>('/products', input);
}

export function updateProduct(productId: string, input: ProductInput): Promise<Product> {
  return apiPut<Product>(`/products/${productId}`, input);
}

export function retireProduct(productId: string): Promise<Product> {
  return apiPost<Product>(`/products/${productId}/retire`);
}

export function restoreProduct(productId: string): Promise<Product> {
  return apiPost<Product>(`/products/${productId}/restore`);
}

export function retireVariant(productId: string, variantId: string): Promise<Product> {
  return apiPost<Product>(`/products/${productId}/variants/${variantId}/retire`);
}

export function restoreVariant(productId: string, variantId: string): Promise<Product> {
  return apiPost<Product>(`/products/${productId}/variants/${variantId}/restore`);
}
