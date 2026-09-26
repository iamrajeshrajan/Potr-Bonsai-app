import { renderSVG } from 'uqr';
import type { Env } from '../types';
import { certificateUrl } from '../lib/codes';
import { escapeHtml } from '../lib/util';

interface CardItem {
  code: string;
  title: string;
  subtitle: string | null;
}

/**
 * Print-ready QR cards, 85 × 55 mm (business-card size), 10 per A4 sheet.
 * Print at 100% scale on card stock; cut along the light guides.
 */
export async function renderCards(env: Env, codes: string[]): Promise<string> {
  const wanted = [...new Set(codes.map((c) => c.trim().toUpperCase()).filter(Boolean))].slice(0, 100);
  const items: CardItem[] = [];
  for (const code of wanted) {
    const b = await env.DB.prepare('SELECT code, name, species_common, species_botanical FROM bonsai WHERE code = ?')
      .bind(code)
      .first<{ code: string; name: string | null; species_common: string; species_botanical: string | null }>();
    if (b) {
      items.push({ code: b.code, title: b.name ?? b.species_common, subtitle: b.species_botanical ?? (b.name ? b.species_common : null) });
      continue;
    }
    const p = await env.DB.prepare('SELECT code, name, maker FROM pots WHERE code = ?')
      .bind(code)
      .first<{ code: string; name: string; maker: string | null }>();
    if (p) items.push({ code: p.code, title: p.name, subtitle: p.maker });
  }

  const cards = items
    .map((it) => {
      const qr = renderSVG(certificateUrl(env, it.code), { ecc: 'M', border: 0, blackColor: '#1d2419', whiteColor: 'transparent' });
      return `<div class="c">
  <div class="qr">${qr}</div>
  <div class="t">
    <div class="b">${escapeHtml(env.BRAND_NAME)}</div>
    <div class="n">${escapeHtml(it.title)}</div>
    ${it.subtitle ? `<div class="s">${escapeHtml(it.subtitle)}</div>` : ''}
    <div class="code">${escapeHtml(it.code)}</div>
    <div class="h">Scan for certificate &amp; care history</div>
  </div>
</div>`;
    })
    .join('');

  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Potr QR cards</title>
<link href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:wght@600&family=Inter:wght@400;600&display=swap" rel="stylesheet">
<style>
@page { size: A4; margin: 10mm 20mm; }
* { box-sizing: border-box; }
body { margin: 0; font-family: 'Inter', system-ui, sans-serif; color: #1d2419; background: #fff; }
.bar { padding: 12px 16px; font-size: 14px; background: #f6f2ea; border-bottom: 1px solid #e2dccd; }
.bar button { font: inherit; padding: 6px 14px; margin-left: 8px; }
.sheet { display: grid; grid-template-columns: repeat(2, 85mm); grid-auto-rows: 55mm; justify-content: center; padding: 8mm 0; }
.c { width: 85mm; height: 55mm; padding: 5mm; display: flex; gap: 4mm; align-items: center; outline: 0.2mm dashed #ccc; break-inside: avoid; }
.qr { width: 32mm; height: 32mm; flex: none; }
.qr svg { width: 100%; height: 100%; display: block; }
.t { min-width: 0; }
.b { font-size: 6.5pt; letter-spacing: .12em; text-transform: uppercase; color: #6b6a60; }
.n { font-family: 'Cormorant Garamond', Georgia, serif; font-size: 15pt; font-weight: 600; line-height: 1.1; margin-top: 1.5mm; }
.s { font-size: 8pt; font-style: italic; color: #4a4a42; margin-top: .5mm; }
.code { font-family: ui-monospace, Menlo, monospace; font-size: 9pt; font-weight: 600; margin-top: 2mm; }
.h { font-size: 6.5pt; color: #6b6a60; margin-top: 1mm; }
@media print { .bar { display: none; } .sheet { padding: 0; } }
</style></head>
<body>
<div class="bar">${items.length} card${items.length === 1 ? '' : 's'} · print at 100% scale on A4 card stock <button onclick="print()">Print</button></div>
<div class="sheet">${cards || '<p style="padding:16px">No matching codes.</p>'}</div>
</body></html>`;
}
