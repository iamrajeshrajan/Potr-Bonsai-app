import { Hono } from 'hono';
import type { AppEnv, BonsaiRow, Env, PhotoRow, PotRow } from '../types';
import { nextCode } from '../lib/codes';
import { bonsaiView, photoView, potView } from '../lib/present';
import { markForSync, syncToWoo } from '../lib/woo';
import { badRequest, notFound, nowIso, pickFields, readJson, uuid } from '../lib/util';
import type { FieldType } from '../lib/util';

const STATUSES = ['in_stock', 'in_use', 'reserved', 'sold', 'retired'] as const;

const FIELDS: Record<string, FieldType> = {
  name: 'string',
  maker: 'string',
  origin: 'string',
  material: 'string',
  glaze: 'string',
  shape: 'string',
  length_cm: 'number',
  width_cm: 'number',
  height_cm: 'number',
  year_made: 'int',
  condition: 'string',
  description: 'string',
  internal_notes: 'string',
  cost_inr: 'number',
  status: { enum: STATUSES },
  for_sale: 'bool',
  price_inr: 'number',
};

export async function findPot(env: Env, ref: string): Promise<PotRow> {
  const row = await env.DB.prepare('SELECT * FROM pots WHERE id = ? OR code = ?')
    .bind(ref, ref.toUpperCase())
    .first<PotRow>();
  if (!row) notFound('Pot not found');
  return row;
}

async function withCover(env: Env, p: PotRow) {
  const cover = p.cover_photo_id
    ? await env.DB.prepare('SELECT * FROM photos WHERE id = ?').bind(p.cover_photo_id).first<PhotoRow>()
    : null;
  return potView(env, p, cover);
}

function validate(merged: Partial<PotRow>) {
  if (merged.for_sale === 1) {
    if (merged.price_inr == null) badRequest('A price is needed to list a pot for sale');
    if (merged.status !== 'in_stock' && merged.status !== 'reserved') {
      badRequest(`A pot that is ${merged.status?.replace('_', ' ')} cannot be listed for sale on its own`);
    }
  }
  if (merged.price_inr != null && merged.price_inr < 0) badRequest('price_inr must be positive');
}

export const potRoutes = new Hono<AppEnv>();

potRoutes.get('/', async (c) => {
  const q = c.req.query('q')?.trim();
  const status = c.req.query('status');
  const forSale = c.req.query('for_sale');
  const limit = Math.min(Number(c.req.query('limit') ?? 50) || 50, 200);
  const offset = Math.max(Number(c.req.query('offset') ?? 0) || 0, 0);
  const where: string[] = [];
  const args: unknown[] = [];
  if (q) {
    where.push('(p.code LIKE ? OR p.name LIKE ? OR p.maker LIKE ? OR p.material LIKE ?)');
    const like = `%${q}%`;
    args.push(like, like, like, like);
  }
  if (status) {
    where.push('p.status = ?');
    args.push(status);
  }
  if (forSale === 'true' || forSale === 'false') {
    where.push('p.for_sale = ?');
    args.push(forSale === 'true' ? 1 : 0);
  }
  const { results } = await c.env.DB.prepare(
    `SELECT p.*, ph.r2_key AS cover_key FROM pots p LEFT JOIN photos ph ON ph.id = p.cover_photo_id
     ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
     ORDER BY p.created_at DESC LIMIT ? OFFSET ?`,
  )
    .bind(...args, limit, offset)
    .all<PotRow & { cover_key: string | null }>();
  return c.json({
    items: results.map(({ cover_key, ...p }) =>
      potView(c.env, p, cover_key ? ({ r2_key: cover_key } as PhotoRow) : null),
    ),
  });
});

potRoutes.post('/', async (c) => {
  const v = pickFields(await readJson(c.req.raw), FIELDS);
  if (!v.name) badRequest('name is required');
  delete v.status; // in_use / sold are managed by the system
  validate({ status: 'in_stock', ...(v as Partial<PotRow>) });
  const id = uuid();
  const code = await nextCode(c.env, 'pot');
  const cols = ['id', 'code', ...Object.keys(v)];
  await c.env.DB.prepare(`INSERT INTO pots (${cols.join(', ')}) VALUES (${cols.map(() => '?').join(', ')})`)
    .bind(id, code, ...Object.values(v))
    .run();
  if (v.for_sale === 1 && (await markForSync(c.env, 'pot', id))) c.executionCtx.waitUntil(syncToWoo(c.env, 'pot', id));
  return c.json(await withCover(c.env, await findPot(c.env, id)), 201);
});

potRoutes.get('/:ref', async (c) => {
  const p = await findPot(c.env, c.req.param('ref'));
  const [photos, holder] = await Promise.all([
    c.env.DB.prepare(`SELECT * FROM photos WHERE subject_type = 'pot' AND subject_id = ? ORDER BY created_at DESC`)
      .bind(p.id)
      .all<PhotoRow>(),
    c.env.DB.prepare('SELECT * FROM bonsai WHERE pot_id = ?').bind(p.id).first<BonsaiRow>(),
  ]);
  return c.json({
    pot: await withCover(c.env, p),
    bonsai: holder ? bonsaiView(c.env, holder) : null,
    photos: photos.results.map((ph) => photoView(c.env, ph)),
  });
});

potRoutes.patch('/:ref', async (c) => {
  const p = await findPot(c.env, c.req.param('ref'));
  const v = pickFields(await readJson(c.req.raw), FIELDS);
  if ('name' in v && !v.name) badRequest('name cannot be empty');
  if (v.status === 'in_use' && p.status !== 'in_use') badRequest('Assign the pot to a bonsai to mark it in use');
  if (p.status === 'in_use' && v.status && v.status !== 'in_use') {
    badRequest('This pot holds a bonsai; change the bonsai’s pot first');
  }
  validate({ ...p, ...(v as Partial<PotRow>) });
  const changed = Object.keys(v).filter((k) => v[k] !== (p as unknown as Record<string, unknown>)[k]);
  if (changed.length) {
    await c.env.DB.prepare(`UPDATE pots SET ${changed.map((k) => `${k} = ?`).join(', ')}, updated_at = ? WHERE id = ?`)
      .bind(...changed.map((k) => v[k]), nowIso(), p.id)
      .run();
    if (await markForSync(c.env, 'pot', p.id)) c.executionCtx.waitUntil(syncToWoo(c.env, 'pot', p.id));
  }
  return c.json(await withCover(c.env, await findPot(c.env, p.id)));
});

potRoutes.post('/:ref/sync', async (c) => {
  const p = await findPot(c.env, c.req.param('ref'));
  await markForSync(c.env, 'pot', p.id);
  await syncToWoo(c.env, 'pot', p.id);
  const after = await findPot(c.env, p.id);
  return c.json({
    woo_sync_status: after.woo_sync_status,
    woo_sync_error: after.woo_sync_error,
    woo_product_id: after.woo_product_id,
  });
});
