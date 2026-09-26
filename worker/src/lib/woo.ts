import type { BonsaiRow, Env, PhotoRow, PotRow } from '../types';
import { certificateUrl, mediaUrl } from './codes';
import { ageText, originLabel } from './present';
import { escapeHtml } from './util';

export type SyncKind = 'bonsai' | 'pot';

export function wooEnabled(env: Env): boolean {
  return Boolean(env.WOO_URL && env.WOO_CONSUMER_KEY && env.WOO_CONSUMER_SECRET);
}

async function wooFetch(env: Env, method: string, path: string, body?: unknown): Promise<Response> {
  const url = `${env.WOO_URL.replace(/\/$/, '')}/wp-json/wc/v3${path}`;
  return fetch(url, {
    method,
    headers: {
      Authorization: `Basic ${btoa(`${env.WOO_CONSUMER_KEY}:${env.WOO_CONSUMER_SECRET}`)}`,
      'Content-Type': 'application/json',
      'User-Agent': 'potr-bonsai-worker',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

async function wooJson<T>(env: Env, method: string, path: string, body?: unknown): Promise<T> {
  const res = await wooFetch(env, method, path, body);
  const text = await res.text();
  if (!res.ok) throw new WooError(res.status, text.slice(0, 500));
  return JSON.parse(text) as T;
}

export class WooError extends Error {
  constructor(
    public status: number,
    body: string,
  ) {
    super(`WooCommerce ${status}: ${body}`);
  }
}

interface WooProduct {
  id: number;
  sku: string;
}

async function photosFor(env: Env, kind: SyncKind, row: { id: string; cover_photo_id: string | null }) {
  const { results } = await env.DB.prepare(
    'SELECT * FROM photos WHERE subject_type = ? AND subject_id = ? ORDER BY created_at DESC LIMIT 6',
  )
    .bind(kind, row.id)
    .all<PhotoRow>();
  // Cover photo first, then newest.
  return results.sort((a, b) => Number(b.id === row.cover_photo_id) - Number(a.id === row.cover_photo_id));
}

function attr(name: string, value: string | number | null | undefined) {
  return value === null || value === undefined || value === ''
    ? null
    : { name, options: [String(value)], visible: true };
}

function bonsaiProduct(env: Env, b: BonsaiRow, pot: PotRow | null) {
  const title = b.name ? `${b.name} – ${b.species_common} bonsai` : `${b.species_common} bonsai`;
  const cert = certificateUrl(env, b.code);
  const lines = [
    b.description ? `<p>${escapeHtml(b.description)}</p>` : '',
    '<ul>',
    b.species_botanical ? `<li><strong>Species:</strong> <em>${escapeHtml(b.species_botanical)}</em></li>` : '',
    b.style ? `<li><strong>Style:</strong> ${escapeHtml(b.style)}</li>` : '',
    b.start_year ? `<li><strong>Age:</strong> ${escapeHtml(ageText(b))}</li>` : '',
    b.origin_type ? `<li><strong>Origin:</strong> ${escapeHtml(originLabel(b.origin_type))}</li>` : '',
    b.height_cm ? `<li><strong>Height:</strong> ${b.height_cm} cm</li>` : '',
    b.trunk_cm ? `<li><strong>Trunk:</strong> ${b.trunk_cm} cm</li>` : '',
    pot ? `<li><strong>Pot:</strong> ${escapeHtml(pot.name)}${pot.maker ? ` by ${escapeHtml(pot.maker)}` : ''}</li>` : '',
    '</ul>',
    `<p>Every Potr bonsai comes with a traceable certificate of authenticity: <a href="${cert}">${b.code}</a></p>`,
  ];
  const available = b.for_sale === 1 && b.status === 'active' && b.price_inr != null;
  return {
    name: title,
    sku: b.code,
    type: 'simple',
    status: b.for_sale === 1 ? 'publish' : 'draft',
    regular_price: b.price_inr != null ? String(b.price_inr) : '',
    description: lines.filter(Boolean).join('\n'),
    short_description: `${b.species_common}${b.start_year ? `, ${ageText(b)}` : ''}. Certificate ${b.code}.`,
    categories: env.WOO_CATEGORY_BONSAI_ID ? [{ id: Number(env.WOO_CATEGORY_BONSAI_ID) }] : undefined,
    manage_stock: true,
    stock_quantity: available ? 1 : 0,
    sold_individually: true,
    attributes: [
      attr('Species', b.species_botanical ?? b.species_common),
      attr('Style', b.style),
      attr('Age', b.start_year ? ageText(b) : null),
      attr('Height (cm)', b.height_cm),
    ].filter(Boolean),
    meta_data: [
      { key: 'potr_code', value: b.code },
      { key: 'potr_certificate_url', value: cert },
    ],
  };
}

function potProduct(env: Env, p: PotRow) {
  const dims = [p.length_cm, p.width_cm, p.height_cm].every((v) => v != null)
    ? `${p.length_cm} × ${p.width_cm} × ${p.height_cm} cm`
    : null;
  const lines = [
    p.description ? `<p>${escapeHtml(p.description)}</p>` : '',
    '<ul>',
    p.maker ? `<li><strong>Maker:</strong> ${escapeHtml(p.maker)}</li>` : '',
    p.origin ? `<li><strong>Origin:</strong> ${escapeHtml(p.origin)}</li>` : '',
    p.material ? `<li><strong>Material:</strong> ${escapeHtml(p.material)}</li>` : '',
    p.glaze ? `<li><strong>Glaze:</strong> ${escapeHtml(p.glaze)}</li>` : '',
    p.shape ? `<li><strong>Shape:</strong> ${escapeHtml(p.shape)}</li>` : '',
    dims ? `<li><strong>Size (L×W×H):</strong> ${dims}</li>` : '',
    '</ul>',
    `<p>One of a kind. Piece ${p.code}.</p>`,
  ];
  const available = p.for_sale === 1 && p.status === 'in_stock' && p.price_inr != null;
  return {
    name: `${p.name}${p.maker ? ` by ${p.maker}` : ''}`,
    sku: p.code,
    type: 'simple',
    status: p.for_sale === 1 ? 'publish' : 'draft',
    regular_price: p.price_inr != null ? String(p.price_inr) : '',
    description: lines.filter(Boolean).join('\n'),
    short_description: [p.shape, p.material, dims].filter(Boolean).join(' · '),
    categories: env.WOO_CATEGORY_POTS_ID ? [{ id: Number(env.WOO_CATEGORY_POTS_ID) }] : undefined,
    manage_stock: true,
    stock_quantity: available ? 1 : 0,
    sold_individually: true,
    dimensions:
      dims != null
        ? { length: String(p.length_cm), width: String(p.width_cm), height: String(p.height_cm) }
        : undefined,
    attributes: [attr('Maker', p.maker), attr('Material', p.material), attr('Shape', p.shape)].filter(Boolean),
    meta_data: [{ key: 'potr_code', value: p.code }],
  };
}

/**
 * Pushes one bonsai/pot to WooCommerce and records the outcome on the row.
 * Never throws; failures are stored as woo_sync_status = 'error' and retried
 * by the cron trigger.
 */
export async function syncToWoo(env: Env, kind: SyncKind, id: string): Promise<void> {
  const table = kind === 'bonsai' ? 'bonsai' : 'pots';
  const row = await env.DB.prepare(`SELECT * FROM ${table} WHERE id = ?`).bind(id).first<BonsaiRow | PotRow>();
  if (!row) return;
  // Never listed and not for sale: nothing to do.
  if (row.for_sale !== 1 && row.woo_product_id == null) {
    await env.DB.prepare(`UPDATE ${table} SET woo_sync_status = 'none', woo_sync_error = NULL WHERE id = ?`)
      .bind(id)
      .run();
    return;
  }
  if (!wooEnabled(env)) {
    await env.DB.prepare(`UPDATE ${table} SET woo_sync_status = 'error', woo_sync_error = ? WHERE id = ?`)
      .bind('WooCommerce is not configured on the server', id)
      .run();
    return;
  }
  try {
    let payload: Record<string, unknown>;
    if (kind === 'bonsai') {
      const b = row as BonsaiRow;
      const pot = b.pot_id
        ? await env.DB.prepare('SELECT * FROM pots WHERE id = ?').bind(b.pot_id).first<PotRow>()
        : null;
      payload = bonsaiProduct(env, b, pot);
    } else {
      payload = potProduct(env, row as PotRow);
    }

    // Only (re)send images when the set changed, otherwise WordPress would
    // import duplicate copies into its media library on every sync.
    const photos = await photosFor(env, kind, row);
    const imageIds = JSON.stringify(photos.map((p) => p.id));
    if (imageIds !== (row.woo_image_ids ?? '[]') || row.woo_product_id == null) {
      payload.images = photos.map((p) => ({ src: mediaUrl(env, p.r2_key), alt: row.code }));
    }

    let productId = row.woo_product_id;
    if (productId == null) {
      // Guard against a create whose response we never saw.
      const existing = await wooJson<WooProduct[]>(env, 'GET', `/products?sku=${encodeURIComponent(row.code)}`);
      productId = existing[0]?.id ?? null;
    }
    let product: WooProduct;
    if (productId != null) {
      try {
        product = await wooJson<WooProduct>(env, 'PUT', `/products/${productId}`, payload);
      } catch (err) {
        if (!(err instanceof WooError && err.status === 404)) throw err;
        // Deleted in the shop: create it again with images.
        payload.images = photos.map((p) => ({ src: mediaUrl(env, p.r2_key), alt: row.code }));
        product = await wooJson<WooProduct>(env, 'POST', '/products', payload);
      }
    } else {
      product = await wooJson<WooProduct>(env, 'POST', '/products', payload);
    }
    await env.DB.prepare(
      `UPDATE ${table} SET woo_product_id = ?, woo_sync_status = 'synced', woo_sync_error = NULL, woo_image_ids = ?
       WHERE id = ?`,
    )
      .bind(product.id, imageIds, id)
      .run();
  } catch (err) {
    await env.DB.prepare(`UPDATE ${table} SET woo_sync_status = 'error', woo_sync_error = ? WHERE id = ?`)
      .bind(String(err instanceof Error ? err.message : err).slice(0, 1000), id)
      .run();
  }
}

/** Marks a row as needing a sync if it is (or was) listed in the shop. */
export async function markForSync(env: Env, kind: SyncKind, id: string): Promise<boolean> {
  const table = kind === 'bonsai' ? 'bonsai' : 'pots';
  const res = await env.DB.prepare(
    `UPDATE ${table} SET woo_sync_status = 'pending'
     WHERE id = ? AND (for_sale = 1 OR woo_product_id IS NOT NULL)`,
  )
    .bind(id)
    .run();
  return (res.meta.changes ?? 0) > 0;
}

/** Cron: retry everything pending or failed. */
export async function syncPending(env: Env, limit = 20): Promise<number> {
  if (!wooEnabled(env)) return 0;
  const { results } = await env.DB.prepare(
    `SELECT 'bonsai' AS kind, id FROM bonsai WHERE woo_sync_status IN ('pending', 'error')
     UNION ALL
     SELECT 'pot' AS kind, id FROM pots WHERE woo_sync_status IN ('pending', 'error')
     LIMIT ?`,
  )
    .bind(limit)
    .all<{ kind: SyncKind; id: string }>();
  for (const r of results) await syncToWoo(env, r.kind, r.id);
  return results.length;
}
