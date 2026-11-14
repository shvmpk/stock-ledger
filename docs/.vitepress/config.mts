import { defineConfig } from 'vitepress';
import { withMermaid } from 'vitepress-plugin-mermaid';
import mathjax3 from 'markdown-it-mathjax3';

// Documentation site for StockLedger.
// Source of truth is every Markdown file in docs/ — nothing is duplicated here.
export default withMermaid(
  defineConfig({
    lang: 'en-GB',
    title: 'StockLedger',
    description:
      'Inventory and order ledger for small e-commerce merchants: exact stock truth, zero overselling, an audit trail behind every figure.',
    base: '/stock-ledger/',
    cleanUrls: true,
    // Roadmaps are kept local and gitignored — never publish them via the build either.
    srcExclude: ['**/ROADMAP*.md'],
    // Markdown pages live in docs/pages/ but are served from the site root:
    // docs/pages/API.md → /stock-ledger/API
    rewrites: {
      'pages/:slug.md': ':slug.md',
    },
    themeConfig: {
      siteTitle: 'StockLedger Docs',
      nav: [
        { text: 'Docs', link: '/' },
        { text: 'GitHub', link: 'https://github.com/shvmpk/stock-ledger' },
      ],
      sidebar: [
        { text: 'Product overview', link: '/PROJECT-OVERVIEW' },
        { text: 'Architecture', link: '/ARCHITECTURE' },
        { text: 'API reference', link: '/API' },
        { text: 'Operations guide', link: '/DOCKER-GUIDE' },
        { text: 'Load testing', link: '/LOAD-TESTING' },
        { text: 'User guide', link: '/GUIDE' },
      ],
      socialLinks: [{ icon: 'github', link: 'https://github.com/shvmpk/stock-ledger' }],
      editLink: {
        pattern: 'https://github.com/shvmpk/stock-ledger/edit/main/docs/:path',
        text: 'Edit this page on GitHub',
      },
      outline: [2, 3],
      search: { provider: 'local' },
      footer: {
        message: 'Generated from the repository docs — edits land here automatically via CI.',
      },
    },
    markdown: {
      config(md) {
        md.use(mathjax3);
      },
    },
    mermaid: {},
  }),
);
