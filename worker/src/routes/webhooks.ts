import { Hono } from 'hono';
import type { AppEnv, BonsaiRow, PotRow } from '../types';
import { hmacSha256Base64, safeEqual } from '../lib/crypto';
import { appendEvent } from '../lib/history';
import { markForSync, syncToWoo } from '../lib/woo';
import { nowIso, todayIso } from '../lib/util';

interface WooOrder {
  id: number;
  number?: string;
  status: string;
  date_paid?: string | null;
  line_items?: { sku?: string; name?: string }[];
}

/** Order statuses that mean the piece is gone (paid or committed). */
const SOLD = ['processing', 'completed', 'on-hold'];
/** Order statuses that release the piece back to the shop. */
const RELEASED = ['cancelled', 'refunded', 'failed'];

export const webhookRoutes = new Hono<AppEnv>();

/**
 * WooCommerce → Settings → Advanced → Webhooks: topic "Order updated" (and
 * "Order created"), delivery URL {PUBLIC_BASE_URL}/api/webhooks/woocommerce,
 * secret = WOO_WEBHOOK_SECRET.
 */
webhookRoutes.post('/woocommerce', async (c) => {
  const raw = await c.req.text();
  const secret = c.env.WOO_WEBHOOK_SECRET;
  const signature = c.req.header('X-WC-Webhook-Signature') ?? '';
  // WooCommerce sends an unsigned "webhook_id=N" ping when a webhook is saved.
  if (!signature && raw.startsWith('webhook_id=')) return c.json({ ok: true });
  if (!secret || !safeEqual(await hmacSha256Base64(secret, raw), signature)) {
    return c.json({ error: 'Invalid signature' }, 401);
  }
  const order = JSON.parse(raw) as WooOrder;
  if (!order?.id) return c.json({ ok: true, ignored: 'not an order' });

  const skus = [...new Set((order.line_items ?? []).map((l) => l.sku).filter((s): s is string => !!s))];
  const matched: string[] = [];
  const orderRef = order.number ?? String(order.id);
  const previous = await c.env.DB.prepare('SELECT status FROM woo_orders WHERE order_id = ?')
    .bind(order.id)
    .first<{ status: string }>();
  const wasSold = previous ? SOLD.includes(previous.status) : false;
  const isSold = SOLD.includes(order.status);
  const isReleased = RELEASED.includes(order.status);

  for (const sku of skus) {
    const code = sku.toUpperCase();
    const b = await c.env.DB.prepare('SELECT * FROM bonsai WHERE code = ?').bind(code).first<BonsaiRow>();
    const p = b ? null : await c.env.DB.prepare('SELECT * FROM pots WHERE code = ?').bind(code).first<PotRow>();
    if (!b && !p) continue;
    matched.push(code);
    const now = nowIso();

    if (b && isSold && b.status !== 'sold') {
      await appendEvent(
        c.env.DB,
        {
          bonsai_id: b.id,
          type: 'sold',
          performed_on: (order.date_paid ?? now).slice(0, 10) || todayIso(),
          performed_by: 'potr.in shop',
          details: { channel: 'woocommerce', order: orderRef },
        },
        () => [
          c.env.DB.prepare(`UPDATE bonsai SET status = 'sold', updated_at = ? WHERE id = ?`).bind(now, b.id),
          // The pot leaves with the tree.
          ...(b.pot_id
            ? [c.env.DB.prepare(`UPDATE pots SET status = 'sold', updated_at = ? WHERE id = ?`).bind(now, b.pot_id)]
            : []),
        ],
      );
    } else if (b && isReleased && wasSold && b.status === 'sold') {
      await appendEvent(
        c.env.DB,
        {
          bonsai_id: b.id,
          type: 'status_change',
          performed_on: todayIso(),
          performed_by: 'potr.in shop',
          details: { status: { from: 'sold', to: 'active' }, reason: `order ${orderRef} ${order.status}` },
          is_public: false,
        },
        () => [
          c.env.DB.prepare(`UPDATE bonsai SET status = 'active', updated_at = ? WHERE id = ?`).bind(now, b.id),
          ...(b.pot_id
            ? [c.env.DB.prepare(`UPDATE pots SET status = 'in_use', updated_at = ? WHERE id = ?`).bind(now, b.pot_id)]
            : []),
        ],
      );
    } else if (p && isSold && p.status !== 'sold') {
      await c.env.DB.prepare(`UPDATE pots SET status = 'sold', updated_at = ? WHERE id = ?`).bind(now, p.id).run();
    } else if (p && isReleased && wasSold && p.status === 'sold') {
      await c.env.DB.prepare(`UPDATE pots SET status = 'in_stock', updated_at = ? WHERE id = ?`).bind(now, p.id).run();
    }
    const kind = b ? 'bonsai' : 'pot';
    const id = (b ?? p)!.id;
    if (await markForSync(c.env, kind, id)) c.executionCtx.waitUntil(syncToWoo(c.env, kind, id));
  }

  await c.env.DB.prepare(
    `INSERT INTO woo_orders (order_id, status, codes) VALUES (?, ?, ?)
     ON CONFLICT (order_id) DO UPDATE SET status = excluded.status, codes = excluded.codes, received_at = ?`,
  )
    .bind(order.id, order.status, JSON.stringify(matched), nowIso())
    .run();
  return c.json({ ok: true, matched });
});
