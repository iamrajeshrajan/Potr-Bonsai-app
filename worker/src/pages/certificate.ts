import type { BonsaiRow, Env, EventRow, PhotoRow, PotRow } from '../types';
import { EVENT_LABELS } from '../types';
import type { EventType } from '../types';
import { mediaUrl } from '../lib/codes';
import { verifyChain } from '../lib/history';
import { ageText, originLabel } from '../lib/present';
import { escapeHtml } from '../lib/util';
import { page } from './layout';

const STYLE = `
.hero { display: grid; grid-template-columns: 1fr; gap: 20px; margin-top: 20px; }
@media (min-width: 720px) { .hero { grid-template-columns: 1.1fr 1fr; align-items: start; } }
.hero img { width: 100%; aspect-ratio: 4/5; object-fit: cover; border-radius: 14px; background: var(--line); }
.cert-label { font-size: 12px; letter-spacing: .14em; text-transform: uppercase; color: var(--clay); font-weight: 600; }
.hero h1 { font-size: 40px; margin: 6px 0 4px; }
.botanical { font-style: italic; color: var(--muted); font-size: 18px; }
.facts { display: grid; grid-template-columns: 1fr 1fr; gap: 14px 18px; margin: 20px 0 0; }
.facts dt { font-size: 12px; text-transform: uppercase; letter-spacing: .08em; color: var(--muted); }
.facts dd { margin: 2px 0 0; font-weight: 500; }
.code { font-family: ui-monospace, Menlo, monospace; font-size: 15px; font-weight: 600; }
.cta { display: inline-block; margin-top: 18px; padding: 12px 20px; border-radius: 10px; background: var(--moss);
  color: var(--card); text-decoration: none; font-weight: 600; }
.gallery { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 8px; margin-top: 12px; }
.gallery figure { margin: 0; }
.gallery img { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 8px; display: block; }
.gallery figcaption { font-size: 12px; color: var(--muted); margin-top: 2px; }
.timeline { list-style: none; margin: 12px 0 0; padding: 0; }
.timeline li { display: grid; grid-template-columns: 96px 1fr; gap: 12px; padding: 12px 0; border-top: 1px solid var(--line); }
.timeline li:first-child { border-top: 0; }
.timeline time { color: var(--muted); font-size: 14px; }
.timeline strong { display: block; }
.timeline p { margin: 2px 0 0; color: var(--muted); font-size: 15px; }
.seal { display: flex; gap: 14px; align-items: flex-start; }
.seal svg { flex: none; }
h2 { font-size: 26px; }
`;

function fmtDate(iso: string): string {
  const d = new Date(`${iso.slice(0, 10)}T00:00:00Z`);
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
}

function eventSummary(e: EventRow): string {
  const d = JSON.parse(e.details) as Record<string, unknown>;
  const parts: string[] = [];
  if (e.type === 'pot_change' && d.to) parts.push(`Moved into pot ${d.to}`);
  if (e.type === 'repotting' && d.soil_mix) parts.push(`Soil: ${d.soil_mix}`);
  if (e.type === 'wiring' && d.wire) parts.push(`Wire: ${d.wire}`);
  if (e.type === 'sold') parts.push('Found a new home through the Potr shop');
  const changes = d.changes as Record<string, { from: unknown; to: unknown }> | undefined;
  if (changes) {
    for (const [k, c] of Object.entries(changes)) parts.push(`${k.replace(/_/g, ' ')}: ${c.from ?? '—'} → ${c.to ?? '—'}`);
  }
  if (e.notes) parts.push(e.notes);
  return parts.join(' · ');
}

export async function renderCertificate(env: Env, code: string): Promise<string | null> {
  const b = await env.DB.prepare('SELECT * FROM bonsai WHERE code = ?').bind(code.toUpperCase()).first<BonsaiRow>();
  if (!b) return null;
  const [pot, events, photos] = await Promise.all([
    b.pot_id ? env.DB.prepare('SELECT * FROM pots WHERE id = ?').bind(b.pot_id).first<PotRow>() : null,
    env.DB.prepare('SELECT * FROM events WHERE bonsai_id = ? ORDER BY seq').bind(b.id).all<EventRow>(),
    env.DB.prepare(`SELECT * FROM photos WHERE subject_type = 'bonsai' AND subject_id = ? ORDER BY COALESCE(taken_on, created_at) DESC`)
      .bind(b.id)
      .all<PhotoRow>(),
  ]);
  const chain = await verifyChain(events.results);
  const cover = photos.results.find((p) => p.id === b.cover_photo_id) ?? photos.results[0];
  const title = b.name ? `${b.name}` : `${b.species_common}`;
  const registered = events.results.find((e) => e.type === 'registered');
  const publicEvents = events.results.filter((e) => e.is_public === 1).reverse();
  const available = b.for_sale === 1 && b.status === 'active';

  const facts: [string, string][] = [
    ['Certificate no.', `<span class="code">${escapeHtml(b.code)}</span>`],
    ['Age', escapeHtml(ageText(b)) || '—'],
    ['Origin', escapeHtml([originLabel(b.origin_type), b.origin_location].filter(Boolean).join(', ')) || '—'],
    ['Style', escapeHtml(b.style) || '—'],
    ['Height', b.height_cm ? `${b.height_cm} cm` : '—'],
    ['Trunk', b.trunk_cm ? `${b.trunk_cm} cm` : '—'],
    ['Pot', pot ? escapeHtml(`${pot.name}${pot.maker ? ` · ${pot.maker}` : ''}`) : '—'],
    ['In Potr care since', registered ? fmtDate(registered.performed_on) : '—'],
  ];
  const statusPill =
    b.status === 'sold'
      ? '<span class="pill ok">In a private collection</span>'
      : b.status === 'deceased'
        ? '<span class="pill warn">No longer living</span>'
        : available
          ? '<span class="pill ok">Available</span>'
          : '';

  const body = `
<section class="hero">
  ${cover ? `<img src="${escapeHtml(mediaUrl(env, cover.r2_key))}" alt="${escapeHtml(title)}">` : ''}
  <div>
    <div class="cert-label">Certificate of authenticity</div>
    <h1>${escapeHtml(title)}</h1>
    <div class="botanical">${escapeHtml(b.species_botanical ?? b.species_common)}</div>
    <div style="margin-top:10px">${statusPill}</div>
    <dl class="facts">${facts.map(([k, v]) => `<div><dt>${k}</dt><dd>${v}</dd></div>`).join('')}</dl>
    ${b.description ? `<p>${escapeHtml(b.description)}</p>` : ''}
    ${
      available
        ? `<a class="cta" href="${escapeHtml(`${env.SHOP_URL.replace(/\/$/, '')}/?s=${encodeURIComponent(b.code)}&post_type=product`)}">View in the Potr shop${b.price_inr ? ` · ₹${b.price_inr.toLocaleString('en-IN')}` : ''}</a>`
        : ''
    }
  </div>
</section>

${
  photos.results.length > 1
    ? `<section class="card"><h2>Growth record</h2>
  <p class="muted" style="margin:4px 0 0;font-size:14px">${photos.results.length} photos, newest first.</p>
  <div class="gallery">${photos.results
    .map(
      (p) =>
        `<figure><a href="${escapeHtml(mediaUrl(env, p.r2_key))}"><img loading="lazy" src="${escapeHtml(mediaUrl(env, p.r2_key))}" alt=""></a><figcaption>${fmtDate(p.taken_on ?? p.created_at)}</figcaption></figure>`,
    )
    .join('')}</div></section>`
    : ''
}

<section class="card">
  <h2>Care history</h2>
  <ul class="timeline">
  ${publicEvents
    .map(
      (e) => `<li><time datetime="${e.performed_on}">${fmtDate(e.performed_on)}</time><div><strong>${escapeHtml(
        EVENT_LABELS[e.type as EventType] ?? e.type,
      )}</strong>${eventSummary(e) ? `<p>${escapeHtml(eventSummary(e))}</p>` : ''}${
        e.performed_by ? `<p>by ${escapeHtml(e.performed_by)}</p>` : ''
      }</div></li>`,
    )
    .join('')}
  </ul>
</section>

<section class="card seal">
  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="${chain.valid ? 'currentColor' : '#b3261e'}" stroke-width="1.6" aria-hidden="true" style="color:var(--moss)">
    <path d="M12 2l7 3v6c0 5-3.5 9-7 11-3.5-2-7-6-7-11V5l7-3z"/>${chain.valid ? '<path d="M8.5 12l2.5 2.5 4.5-5"/>' : '<path d="M9 9l6 6M15 9l-6 6"/>'}
  </svg>
  <div>
    <strong>${chain.valid ? 'History verified' : 'History could not be verified'}</strong>
    <div class="muted" style="font-size:14px">${
      chain.valid
        ? `All ${chain.length} history records are sealed in a SHA-256 chain; changing any past record would break it. Photos are fingerprinted the same way.`
        : `Record ${chain.brokenAtSeq} does not match its seal. Please contact Potr.`
    }</div>
    <div class="mono muted" style="margin-top:6px">Seal: ${chain.head}</div>
  </div>
</section>
`;
  return page(`${title} · ${b.code} · Potr certificate`, `<style>${STYLE}</style>${body}`, {
    brand: env.BRAND_NAME,
    shopUrl: env.SHOP_URL,
    description: `Certificate of authenticity for ${b.species_common} bonsai ${b.code} from Potr.`,
    image: cover ? mediaUrl(env, cover.r2_key) : null,
  });
}

export async function renderPotPage(env: Env, code: string): Promise<string | null> {
  const p = await env.DB.prepare('SELECT * FROM pots WHERE code = ?').bind(code.toUpperCase()).first<PotRow>();
  if (!p) return null;
  const { results: photos } = await env.DB.prepare(
    `SELECT * FROM photos WHERE subject_type = 'pot' AND subject_id = ? ORDER BY created_at DESC LIMIT 12`,
  )
    .bind(p.id)
    .all<PhotoRow>();
  const cover = photos.find((ph) => ph.id === p.cover_photo_id) ?? photos[0];
  const dims = [p.length_cm, p.width_cm, p.height_cm].every((v) => v != null)
    ? `${p.length_cm} × ${p.width_cm} × ${p.height_cm} cm`
    : null;
  const facts: [string, string | null][] = [
    ['Piece no.', `<span class="code">${escapeHtml(p.code)}</span>`],
    ['Maker', escapeHtml(p.maker) || null],
    ['Origin', escapeHtml(p.origin) || null],
    ['Material', escapeHtml(p.material) || null],
    ['Glaze', escapeHtml(p.glaze) || null],
    ['Shape', escapeHtml(p.shape) || null],
    ['Size (L×W×H)', dims],
    ['Made', p.year_made ? String(p.year_made) : null],
  ];
  const available = p.for_sale === 1 && p.status === 'in_stock';
  const body = `
<section class="hero">
  ${cover ? `<img src="${escapeHtml(mediaUrl(env, cover.r2_key))}" alt="${escapeHtml(p.name)}">` : ''}
  <div>
    <div class="cert-label">One-of-a-kind bonsai pot</div>
    <h1>${escapeHtml(p.name)}</h1>
    <dl class="facts">${facts
      .filter(([, v]) => v)
      .map(([k, v]) => `<div><dt>${k}</dt><dd>${v}</dd></div>`)
      .join('')}</dl>
    ${p.description ? `<p>${escapeHtml(p.description)}</p>` : ''}
    ${
      available
        ? `<a class="cta" href="${escapeHtml(`${env.SHOP_URL.replace(/\/$/, '')}/?s=${encodeURIComponent(p.code)}&post_type=product`)}">View in the Potr shop${p.price_inr ? ` · ₹${p.price_inr.toLocaleString('en-IN')}` : ''}</a>`
        : ''
    }
  </div>
</section>`;
  return page(`${p.name} · ${p.code} · Potr`, `<style>${STYLE}</style>${body}`, {
    brand: env.BRAND_NAME,
    shopUrl: env.SHOP_URL,
    image: cover ? mediaUrl(env, cover.r2_key) : null,
  });
}
