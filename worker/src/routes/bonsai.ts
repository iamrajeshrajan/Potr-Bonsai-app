import { Hono } from 'hono';
import type { AppEnv, BonsaiRow, Env, EventRow, EventType, PhotoRow, PotRow, TaskRow, User } from '../types';
import { MANUAL_EVENT_TYPES } from '../types';
import { nextCode } from '../lib/codes';
import { appendEvent, verifyChain } from '../lib/history';
import { bonsaiView, eventView, photoView, potView } from '../lib/present';
import { markForSync, syncToWoo } from '../lib/woo';
import { badRequest, conflict, isIsoDate, notFound, nowIso, pickFields, readJson, todayIso, uuid } from '../lib/util';
import type { FieldType } from '../lib/util';

const ORIGIN_TYPES = ['seed', 'cutting', 'air_layer', 'graft', 'nursery_stock', 'yamadori', 'import', 'other'] as const;
const STATUSES = ['active', 'reserved', 'sold', 'deceased', 'archived'] as const;

const FIELDS: Record<string, FieldType> = {
  name: 'string',
  species_common: 'string',
  species_botanical: 'string',
  style: 'string',
  origin_type: { enum: ORIGIN_TYPES },
  origin_location: 'string',
  source_name: 'string',
  acquired_on: 'date',
  start_year: 'int',
  age_basis: { enum: ['documented', 'estimated'] },
  height_cm: 'number',
  trunk_cm: 'number',
  spread_cm: 'number',
  pot_id: 'string',
  location: 'string',
  description: 'string',
  internal_notes: 'string',
  cost_inr: 'number',
  status: { enum: STATUSES },
  for_sale: 'bool',
  price_inr: 'number',
};

/** Edits to these fields are logged as a public 'correction' event. */
const RECORD_FIELDS = [
  'name',
  'species_common',
  'species_botanical',
  'style',
  'origin_type',
  'origin_location',
  'source_name',
  'acquired_on',
  'start_year',
  'age_basis',
];
const MEASUREMENT_FIELDS = ['height_cm', 'trunk_cm', 'spread_cm'];

export async function findBonsai(env: Env, ref: string): Promise<BonsaiRow> {
  const row = await env.DB.prepare('SELECT * FROM bonsai WHERE id = ? OR code = ?')
    .bind(ref, ref.toUpperCase())
    .first<BonsaiRow>();
  if (!row) notFound('Bonsai not found');
  return row;
}

async function coverPhoto(env: Env, id: string | null): Promise<PhotoRow | null> {
  return id ? env.DB.prepare('SELECT * FROM photos WHERE id = ?').bind(id).first<PhotoRow>() : null;
}

/** Normalises age input: the app may send age_years instead of start_year. */
function applyAge(body: Record<string, unknown>) {
  if (body.age_years !== undefined && body.age_years !== null && body.age_years !== '' && body.start_year === undefined) {
    const age = Number(body.age_years);
    if (!Number.isFinite(age) || age < 0 || age > 2000) badRequest('age_years must be a positive number');
    body.start_year = new Date().getUTCFullYear() - Math.round(age);
  }
}

function validate(v: Record<string, string | number | null>) {
  const year = new Date().getUTCFullYear();
  if (typeof v.start_year === 'number' && (v.start_year < 0 || v.start_year > year)) {
    badRequest('start_year is out of range');
  }
  if (v.for_sale === 1 && v.price_inr == null) badRequest('A price is needed to list a bonsai for sale');
  if (typeof v.price_inr === 'number' && v.price_inr < 0) badRequest('price_inr must be positive');
}

/** Checks the pot exists and is free, returns it. */
async function claimablePot(env: Env, potId: string, bonsaiId: string | null): Promise<PotRow> {
  const pot = await env.DB.prepare('SELECT * FROM pots WHERE id = ? OR code = ?')
    .bind(potId, potId.toUpperCase())
    .first<PotRow>();
  if (!pot) badRequest('Pot not found');
  const holder = await env.DB.prepare('SELECT id, code FROM bonsai WHERE pot_id = ? AND id IS NOT ?')
    .bind(pot.id, bonsaiId)
    .first<{ id: string; code: string }>();
  if (holder) conflict(`Pot ${pot.code} is already used by ${holder.code}`);
  if (pot.status === 'sold' || pot.status === 'retired') conflict(`Pot ${pot.code} is ${pot.status}`);
  return pot;
}

function potUseStmts(env: Env, newPotId: string | null, oldPotId: string | null): D1PreparedStatement[] {
  const now = nowIso();
  const stmts: D1PreparedStatement[] = [];
  if (oldPotId && oldPotId !== newPotId) {
    stmts.push(
      env.DB.prepare(`UPDATE pots SET status = 'in_stock', updated_at = ? WHERE id = ? AND status = 'in_use'`).bind(
        now,
        oldPotId,
      ),
    );
  }
  if (newPotId) {
    // A pot holding a tree is sold with the tree, never on its own.
    stmts.push(
      env.DB.prepare(`UPDATE pots SET status = 'in_use', for_sale = 0, updated_at = ? WHERE id = ?`).bind(now, newPotId),
    );
  }
  return stmts;
}

export interface ManualEventInput {
  type: EventType;
  performed_on: string;
  performed_by: string | null;
  notes: string | null;
  details: Record<string, unknown>;
  photo_ids: string[];
  is_public: boolean;
}

export function parseEventInput(body: Record<string, unknown>, user: User): ManualEventInput {
  const type = body.type as EventType;
  if (!MANUAL_EVENT_TYPES.includes(type)) badRequest(`type must be one of: ${MANUAL_EVENT_TYPES.join(', ')}`);
  const performed_on = body.performed_on ?? todayIso();
  if (!isIsoDate(performed_on)) badRequest('performed_on must be a date (YYYY-MM-DD)');
  if (performed_on > todayIso()) badRequest('performed_on cannot be in the future');
  const details = body.details ?? {};
  if (typeof details !== 'object' || Array.isArray(details)) badRequest('details must be an object');
  const photo_ids = body.photo_ids ?? [];
  if (!Array.isArray(photo_ids) || !photo_ids.every((p) => typeof p === 'string')) {
    badRequest('photo_ids must be a list of photo ids');
  }
  return {
    type,
    performed_on,
    performed_by: typeof body.performed_by === 'string' && body.performed_by.trim() ? body.performed_by.trim() : user.name,
    notes: typeof body.notes === 'string' && body.notes.trim() ? body.notes.trim() : null,
    details: details as Record<string, unknown>,
    photo_ids: photo_ids as string[],
    is_public: body.is_public !== false,
  };
}

/** Records a care event (optionally with photos) against a bonsai. */
export async function recordEvent(env: Env, b: BonsaiRow, input: ManualEventInput, user: User): Promise<EventRow> {
  let photos: PhotoRow[] = [];
  if (input.photo_ids.length) {
    const marks = input.photo_ids.map(() => '?').join(',');
    ({ results: photos } = await env.DB.prepare(
      `SELECT * FROM photos WHERE id IN (${marks}) AND subject_type = 'bonsai' AND subject_id = ?`,
    )
      .bind(...input.photo_ids, b.id)
      .all<PhotoRow>());
    if (photos.length !== input.photo_ids.length) badRequest('Some photos do not belong to this bonsai');
    if (photos.some((p) => p.event_id)) conflict('A photo is already attached to another event');
  }
  // Measurement events also update the tree's current measurements.
  const measured = pickFields(input.details, { height_cm: 'number', trunk_cm: 'number', spread_cm: 'number' });
  const now = nowIso();
  const ev = await appendEvent(
    env.DB,
    {
      bonsai_id: b.id,
      type: input.type,
      performed_on: input.performed_on,
      performed_by: input.performed_by,
      notes: input.notes,
      details: input.details,
      photo_hashes: photos.map((p) => p.sha256),
      is_public: input.is_public,
      recorded_by: user.id,
    },
    (ev) => {
      const stmts = photos.map((p) => env.DB.prepare('UPDATE photos SET event_id = ? WHERE id = ?').bind(ev.id, p.id));
      if (input.type === 'measurement' && Object.keys(measured).length) {
        const cols = Object.keys(measured);
        stmts.push(
          env.DB.prepare(`UPDATE bonsai SET ${cols.map((k) => `${k} = ?`).join(', ')}, updated_at = ? WHERE id = ?`).bind(
            ...cols.map((k) => measured[k]),
            now,
            b.id,
          ),
        );
      }
      return stmts;
    },
  );
  return ev;
}

export const bonsaiRoutes = new Hono<AppEnv>();

bonsaiRoutes.get('/', async (c) => {
  const q = c.req.query('q')?.trim();
  const status = c.req.query('status');
  const forSale = c.req.query('for_sale');
  const limit = Math.min(Number(c.req.query('limit') ?? 50) || 50, 200);
  const offset = Math.max(Number(c.req.query('offset') ?? 0) || 0, 0);
  const where: string[] = [];
  const args: unknown[] = [];
  if (q) {
    where.push('(b.code LIKE ? OR b.name LIKE ? OR b.species_common LIKE ? OR b.species_botanical LIKE ?)');
    const like = `%${q}%`;
    args.push(like, like, like, like);
  }
  if (status) {
    where.push('b.status = ?');
    args.push(status);
  }
  if (forSale === 'true' || forSale === 'false') {
    where.push('b.for_sale = ?');
    args.push(forSale === 'true' ? 1 : 0);
  }
  const { results } = await c.env.DB.prepare(
    `SELECT b.*, p.r2_key AS cover_key FROM bonsai b LEFT JOIN photos p ON p.id = b.cover_photo_id
     ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
     ORDER BY b.created_at DESC LIMIT ? OFFSET ?`,
  )
    .bind(...args, limit, offset)
    .all<BonsaiRow & { cover_key: string | null }>();
  return c.json({
    items: results.map(({ cover_key, ...b }) =>
      bonsaiView(c.env, b, cover_key ? ({ r2_key: cover_key } as PhotoRow) : null),
    ),
  });
});

bonsaiRoutes.post('/', async (c) => {
  const user = c.get('user');
  const body = await readJson(c.req.raw);
  applyAge(body);
  const v = pickFields(body, FIELDS);
  if (!v.species_common) badRequest('species_common is required');
  validate(v);
  delete v.status; // new trees always start active
  const id = uuid();
  if (typeof v.pot_id === 'string') v.pot_id = (await claimablePot(c.env, v.pot_id, null)).id;
  const code = await nextCode(c.env, 'bonsai');
  const cols = ['id', 'code', ...Object.keys(v)];
  await c.env.DB.batch([
    c.env.DB.prepare(`INSERT INTO bonsai (${cols.join(', ')}) VALUES (${cols.map(() => '?').join(', ')})`).bind(
      id,
      code,
      ...Object.values(v),
    ),
    ...potUseStmts(c.env, (v.pot_id as string | null) ?? null, null),
  ]);
  await appendEvent(c.env.DB, {
    bonsai_id: id,
    type: 'registered',
    performed_on: todayIso(),
    performed_by: user.name,
    // Snapshot of the identity at registration, part of the permanent record.
    details: Object.fromEntries(
      [...RECORD_FIELDS, ...MEASUREMENT_FIELDS].filter((k) => v[k] != null).map((k) => [k, v[k]]),
    ),
    recorded_by: user.id,
  });
  if (v.for_sale === 1 && (await markForSync(c.env, 'bonsai', id))) {
    c.executionCtx.waitUntil(syncToWoo(c.env, 'bonsai', id));
  }
  return c.json(bonsaiView(c.env, await findBonsai(c.env, id)), 201);
});

bonsaiRoutes.get('/:ref', async (c) => {
  const b = await findBonsai(c.env, c.req.param('ref'));
  const [pot, events, photos, tasks, cover] = await Promise.all([
    b.pot_id ? c.env.DB.prepare('SELECT * FROM pots WHERE id = ?').bind(b.pot_id).first<PotRow>() : null,
    c.env.DB.prepare('SELECT * FROM events WHERE bonsai_id = ? ORDER BY seq').bind(b.id).all<EventRow>(),
    c.env.DB.prepare(`SELECT * FROM photos WHERE subject_type = 'bonsai' AND subject_id = ? ORDER BY created_at DESC`)
      .bind(b.id)
      .all<PhotoRow>(),
    c.env.DB.prepare(`SELECT * FROM tasks WHERE bonsai_id = ? AND status = 'open' ORDER BY due_on`)
      .bind(b.id)
      .all<TaskRow>(),
    coverPhoto(c.env, b.cover_photo_id),
  ]);
  return c.json({
    bonsai: bonsaiView(c.env, b, cover),
    pot: pot ? potView(c.env, pot) : null,
    events: events.results.map(eventView).reverse(),
    photos: photos.results.map((p) => photoView(c.env, p)),
    tasks: tasks.results,
    chain: await verifyChain(events.results),
  });
});

bonsaiRoutes.patch('/:ref', async (c) => {
  const user = c.get('user');
  const b = await findBonsai(c.env, c.req.param('ref'));
  const body = await readJson(c.req.raw);
  applyAge(body);
  const v = pickFields(body, FIELDS);
  const merged = { ...b, ...v } as Record<string, unknown>;
  validate({ price_inr: merged.price_inr as number | null, for_sale: merged.for_sale as number, ...v });
  if (merged.for_sale === 1 && !['active', 'reserved'].includes(merged.status as string)) {
    badRequest(`A ${merged.status} bonsai cannot be listed for sale`);
  }
  if ('species_common' in v && !v.species_common) badRequest('species_common cannot be empty');
  if (typeof v.pot_id === 'string') v.pot_id = (await claimablePot(c.env, v.pot_id, b.id)).id;

  const changed = Object.keys(v).filter((k) => v[k] !== (b as unknown as Record<string, unknown>)[k]);
  if (!changed.length) return c.json(bonsaiView(c.env, b, await coverPhoto(c.env, b.cover_photo_id)));

  const now = nowIso();
  await c.env.DB.batch([
    c.env.DB.prepare(`UPDATE bonsai SET ${changed.map((k) => `${k} = ?`).join(', ')}, updated_at = ? WHERE id = ?`).bind(
      ...changed.map((k) => v[k]),
      now,
      b.id,
    ),
    ...(changed.includes('pot_id') ? potUseStmts(c.env, (v.pot_id as string | null) ?? null, b.pot_id) : []),
  ]);

  // Log what changed so the history stays complete.
  const diff = (keys: string[]) =>
    Object.fromEntries(
      keys
        .filter((k) => changed.includes(k))
        .map((k) => [k, { from: (b as unknown as Record<string, unknown>)[k] ?? null, to: v[k] }]),
    );
  const base = { bonsai_id: b.id, performed_on: todayIso(), performed_by: user.name, recorded_by: user.id };
  const corrections = diff(RECORD_FIELDS);
  if (Object.keys(corrections).length) {
    await appendEvent(c.env.DB, { ...base, type: 'correction', details: { changes: corrections } });
  }
  const measurements = diff(MEASUREMENT_FIELDS);
  if (Object.keys(measurements).length) {
    await appendEvent(c.env.DB, { ...base, type: 'measurement', details: { changes: measurements } });
  }
  if (changed.includes('pot_id')) {
    const codes = await c.env.DB.prepare('SELECT id, code FROM pots WHERE id IN (?, ?)')
      .bind(b.pot_id, v.pot_id)
      .all<{ id: string; code: string }>();
    const codeOf = (id: unknown) => codes.results.find((r) => r.id === id)?.code ?? null;
    await appendEvent(c.env.DB, {
      ...base,
      type: 'pot_change',
      details: { from: codeOf(b.pot_id), to: codeOf(v.pot_id) },
    });
  }
  if (changed.includes('location')) {
    await appendEvent(c.env.DB, {
      ...base,
      type: 'location_change',
      details: diff(['location']),
      is_public: false,
    });
  }
  if (changed.includes('status')) {
    await appendEvent(c.env.DB, {
      ...base,
      type: 'status_change',
      details: diff(['status']),
      is_public: v.status === 'deceased',
    });
  }

  if (await markForSync(c.env, 'bonsai', b.id)) c.executionCtx.waitUntil(syncToWoo(c.env, 'bonsai', b.id));
  const updated = await findBonsai(c.env, b.id);
  return c.json(bonsaiView(c.env, updated, await coverPhoto(c.env, updated.cover_photo_id)));
});

bonsaiRoutes.post('/:ref/events', async (c) => {
  const user = c.get('user');
  const b = await findBonsai(c.env, c.req.param('ref'));
  const input = parseEventInput(await readJson(c.req.raw), user);
  const ev = await recordEvent(c.env, b, input, user);
  return c.json(eventView(ev), 201);
});

bonsaiRoutes.post('/:ref/sync', async (c) => {
  const b = await findBonsai(c.env, c.req.param('ref'));
  await markForSync(c.env, 'bonsai', b.id);
  await syncToWoo(c.env, 'bonsai', b.id);
  const after = await findBonsai(c.env, b.id);
  return c.json({
    woo_sync_status: after.woo_sync_status,
    woo_sync_error: after.woo_sync_error,
    woo_product_id: after.woo_product_id,
  });
});
