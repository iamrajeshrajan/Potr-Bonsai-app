import { Hono } from 'hono';
import type { AppEnv } from '../types';
import { requireAdmin } from '../lib/auth';
import { randomKey, safeEqual, sha256Hex } from '../lib/crypto';
import { badRequest, conflict, notFound, pickFields, readJson, uuid } from '../lib/util';

const PUBLIC_COLS = 'id, name, email, role, active, created_at, last_seen_at';

/** Unauthenticated: creates the first admin, only while no users exist. */
export const bootstrapRoute = new Hono<AppEnv>().post('/', async (c) => {
  const body = await readJson(c.req.raw);
  const secret = c.env.BOOTSTRAP_SECRET;
  if (!secret || typeof body.secret !== 'string' || !safeEqual(body.secret, secret)) {
    return c.json({ error: 'Invalid bootstrap secret' }, 403);
  }
  const count = await c.env.DB.prepare('SELECT COUNT(*) AS n FROM users').first<{ n: number }>();
  if ((count?.n ?? 0) > 0) conflict('Already bootstrapped');
  const name = typeof body.name === 'string' && body.name.trim() ? body.name.trim() : 'Admin';
  const key = randomKey();
  const id = uuid();
  await c.env.DB.prepare('INSERT INTO users (id, name, email, role, key_hash) VALUES (?, ?, ?, ?, ?)')
    .bind(id, name, typeof body.email === 'string' ? body.email : null, 'admin', await sha256Hex(key))
    .run();
  return c.json({ id, name, role: 'admin', access_key: key }, 201);
});

export const userRoutes = new Hono<AppEnv>();

userRoutes.get('/me', (c) => c.json(c.get('user')));

userRoutes.use('/users/*', requireAdmin);
userRoutes.use('/users', requireAdmin);

userRoutes.get('/users', async (c) => {
  const { results } = await c.env.DB.prepare(`SELECT ${PUBLIC_COLS} FROM users ORDER BY created_at`).all();
  return c.json({ items: results });
});

/** Returns the new user's access key once; only its hash is stored. */
userRoutes.post('/users', async (c) => {
  const v = pickFields(await readJson(c.req.raw), {
    name: 'string',
    email: 'string',
    role: { enum: ['admin', 'staff'] },
  });
  if (!v.name) badRequest('name is required');
  const key = randomKey();
  const id = uuid();
  await c.env.DB.prepare('INSERT INTO users (id, name, email, role, key_hash) VALUES (?, ?, ?, ?, ?)')
    .bind(id, v.name, v.email ?? null, v.role ?? 'staff', await sha256Hex(key))
    .run();
  const user = await c.env.DB.prepare(`SELECT ${PUBLIC_COLS} FROM users WHERE id = ?`).bind(id).first();
  return c.json({ ...user, access_key: key }, 201);
});

userRoutes.patch('/users/:id', async (c) => {
  const id = c.req.param('id');
  const v = pickFields(await readJson(c.req.raw), {
    name: 'string',
    email: 'string',
    role: { enum: ['admin', 'staff'] },
    active: 'bool',
  });
  if (id === c.get('user').id && (v.active === 0 || v.role === 'staff')) {
    badRequest('You cannot disable or demote yourself');
  }
  const cols = Object.keys(v);
  if (cols.length) {
    await c.env.DB.prepare(`UPDATE users SET ${cols.map((k) => `${k} = ?`).join(', ')} WHERE id = ?`)
      .bind(...Object.values(v), id)
      .run();
  }
  const user = await c.env.DB.prepare(`SELECT ${PUBLIC_COLS} FROM users WHERE id = ?`).bind(id).first();
  if (!user) notFound('User not found');
  return c.json(user);
});

/** Issues a new key (e.g. lost phone); the old one stops working immediately. */
userRoutes.post('/users/:id/rotate-key', async (c) => {
  const key = randomKey();
  const res = await c.env.DB.prepare('UPDATE users SET key_hash = ? WHERE id = ?')
    .bind(await sha256Hex(key), c.req.param('id'))
    .run();
  if (!res.meta.changes) notFound('User not found');
  return c.json({ access_key: key });
});
