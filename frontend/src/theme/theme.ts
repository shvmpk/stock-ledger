import { createTheme } from '@mantine/core';

export const emerald = [
  '#e6f7f1',
  '#c9eddf',
  '#9ddfc8',
  '#6bcfad',
  '#43bf96',
  '#25ad82',
  '#15966f',
  '#0d7a5b',
  '#075f47',
  '#034b38',
] as const;

export const slate = [
  '#f8fafc',
  '#f1f5f9',
  '#e2e8f0',
  '#cbd5e1',
  '#94a3b8',
  '#64748b',
  '#475569',
  '#334155',
  '#1e293b',
  '#0f172a',
] as const;

export const theme = createTheme({
  primaryColor: 'emerald',
  primaryShade: { light: 6, dark: 5 },
  colors: {
    emerald,
    slate,
  },
  fontFamily: 'Inter Variable, Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
  fontFamilyMonospace: '"JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace',
  headings: {
    fontFamily: 'Inter Variable, Inter, system-ui, sans-serif',
    fontWeight: '650',
  },
  defaultRadius: 'sm',
  radius: {
    sm: '6px',
    md: '8px',
  },
  components: {
    Button: {
      defaultProps: {
        radius: 'sm',
      },
    },
    Badge: {
      defaultProps: {
        radius: 'sm',
      },
    },
    Modal: {
      defaultProps: {
        radius: 'md',
        centered: true,
        closeButtonProps: { 'aria-label': 'Close dialog' },
      },
    },
    Drawer: {
      defaultProps: {
        radius: 'md',
        closeButtonProps: { 'aria-label': 'Close dialog' },
      },
    },
    Table: {
      defaultProps: {
        verticalSpacing: 'xs',
        horizontalSpacing: 'sm',
        highlightOnHover: true,
      },
    },
    TextInput: {
      defaultProps: {
        radius: 'sm',
      },
    },
    NumberInput: {
      defaultProps: {
        radius: 'sm',
      },
    },
    Select: {
      defaultProps: {
        radius: 'sm',
      },
    },
  },
});
