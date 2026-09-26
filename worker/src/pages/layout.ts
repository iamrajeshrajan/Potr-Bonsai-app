import { escapeHtml } from '../lib/util';

const STYLE = `
:root {
  --paper: #f6f2ea; --card: #fffdf8; --ink: #23261f; --muted: #6b6a60; --line: #e2dccd;
  --moss: #3e5a36; --moss-soft: #e5ecdd; --clay: #9c4f2c; --clay-soft: #f5e4da;
}
@media (prefers-color-scheme: dark) {
  :root {
    --paper: #151713; --card: #1d201a; --ink: #ece8de; --muted: #a29f93; --line: #33372e;
    --moss: #9cc28d; --moss-soft: #26311f; --clay: #e0976f; --clay-soft: #3a2519;
  }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--paper); color: var(--ink);
  font: 16px/1.55 'Inter', system-ui, -apple-system, sans-serif; }
h1, h2, h3 { font-family: 'Cormorant Garamond', Georgia, serif; font-weight: 600; line-height: 1.15; margin: 0; }
a { color: var(--moss); }
.wrap { max-width: 860px; margin: 0 auto; padding: 24px 16px 64px; }
.brand { display: flex; justify-content: space-between; align-items: center; gap: 12px;
  font-size: 13px; letter-spacing: .12em; text-transform: uppercase; color: var(--muted); }
.brand a { color: inherit; text-decoration: none; }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 14px; padding: 20px; margin-top: 16px; }
.muted { color: var(--muted); }
.pill { display: inline-block; padding: 3px 10px; border-radius: 999px; font-size: 13px; font-weight: 500; }
.pill.ok { background: var(--moss-soft); color: var(--moss); }
.pill.warn { background: var(--clay-soft); color: var(--clay); }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; word-break: break-all; }
`;

export function page(title: string, body: string, opts: { brand: string; shopUrl: string; description?: string; image?: string | null }) {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)}</title>
${opts.description ? `<meta name="description" content="${escapeHtml(opts.description)}">` : ''}
<meta property="og:title" content="${escapeHtml(title)}">
${opts.image ? `<meta property="og:image" content="${escapeHtml(opts.image)}">` : ''}
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:wght@500;600&family=Inter:wght@400;500;600&display=swap" rel="stylesheet">
<style>${STYLE}</style>
</head>
<body>
<div class="wrap">
  <div class="brand"><a href="${escapeHtml(opts.shopUrl)}">${escapeHtml(opts.brand)}</a><span>Bonsai Master</span></div>
  ${body}
</div>
</body>
</html>`;
}
