import { createMiddleware } from 'hono/factory';
import { HTTPException } from 'hono/http-exception';
import type { AppEnv, User } from '../types';
import { sha256Hex } from './crypto';

/** Requires `Authorization: Bearer <access key>` from an active user. */
export const requireUser = createMiddleware<AppEnv>(async (c, next) => {
  const header = c.req.header('Authorization') ?? '';
  const key = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!key) throw new HTTPException(401, { message: 'Missing access key' });
  const user = await c.env.DB.prepare(
    'SELECT id, name, email, role FROM users WHERE key_hash = ? AND active = 1',
  )
    .bind(await sha256Hex(key))
    .first<User>();
  if (!user) throw new HTTPException(401, { message: 'Invalid access key' });
  c.set('user', user);
  // Best effort, don't block the request on it.
  c.executionCtx.waitUntil(
    c.env.DB.prepare('UPDATE users SET last_seen_at = ? WHERE id = ?').bind(new Date().toISOString(), user.id).run(),
  );
  await next();
});

export const requireAdmin = createMiddleware<AppEnv>(async (c, next) => {
  if (c.get('user')?.role !== 'admin') throw new HTTPException(403, { message: 'Admin only' });
  await next();
});
