import { Hono } from 'hono';
import type { AppEnv, PhotoRow } from '../types';
import { sha256Hex } from '../lib/crypto';
import { photoView } from '../lib/present';
import { markForSync, syncToWoo } from '../lib/woo';
import { badRequest, conflict, isIsoDate, notFound, nowIso, uuid } from '../lib/util';
import { findBonsai } from './bonsai';
import { findPot } from './pots';

/** The app compresses every photo to under 100 KB; anything larger is refused. */
export const MAX_PHOTO_BYTES = 100 * 1024;
const TYPES: Record<string, string> = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp' };

export const photoRoutes = new Hono<AppEnv>();

/**
 * Multipart upload: file, subject_type (bonsai|pot), subject_id (id or code),
 * optional caption, taken_on.
 * Every photo is kept (the growth record); the newest one becomes the
 * featured image in the app, on the certificate and in the shop.
 * Bonsai photos are then attached to a care event via photo_ids.
 */
photoRoutes.post('/', async (c) => {
  const form = await c.req.formData();
  const file = form.get('file') as unknown;
  if (!file || typeof file === 'string') badRequest('file is required');
  if (!(file instanceof File)) badRequest('file is required');
  const ext = TYPES[file.type];
  if (!ext) badRequest('Only JPEG, PNG or WebP photos are accepted');
  if (file.size > MAX_PHOTO_BYTES) badRequest('Photo is larger than 100 KB; update the app so it compresses photos');
  const subjectType = form.get('subject_type');
  const subjectRef = String(form.get('subject_id') ?? '');
  if (subjectType !== 'bonsai' && subjectType !== 'pot') badRequest('subject_type must be bonsai or pot');
  const subject = subjectType === 'bonsai' ? await findBonsai(c.env, subjectRef) : await findPot(c.env, subjectRef);
  const takenOn = form.get('taken_on');
  if (takenOn && !isIsoDate(takenOn)) badRequest('taken_on must be a date (YYYY-MM-DD)');
  const caption = form.get('caption');

  const bytes = await file.arrayBuffer();
  const sha256 = await sha256Hex(bytes);
  const id = uuid();
  const key = `${subjectType}/${subject.code}/${id}.${ext}`;
  await c.env.PHOTOS.put(key, bytes, {
    httpMetadata: { contentType: file.type, cacheControl: 'public, max-age=31536000, immutable' },
    customMetadata: { sha256 },
  });
  const table = subjectType === 'bonsai' ? 'bonsai' : 'pots';
  await c.env.DB.batch([
    c.env.DB.prepare(
      `INSERT INTO photos (id, subject_type, subject_id, r2_key, content_type, bytes, sha256, caption, taken_on, uploaded_by)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    ).bind(
      id,
      subjectType,
      subject.id,
      key,
      file.type,
      bytes.byteLength,
      sha256,
      typeof caption === 'string' && caption ? caption : null,
      takenOn || null,
      c.get('user').id,
    ),
    c.env.DB.prepare(`UPDATE ${table} SET cover_photo_id = ?, updated_at = ? WHERE id = ?`).bind(id, nowIso(), subject.id),
  ]);
  // Listed items get the new featured image in the shop.
  if (await markForSync(c.env, subjectType, subject.id)) {
    c.executionCtx.waitUntil(syncToWoo(c.env, subjectType, subject.id));
  }
  const row = await c.env.DB.prepare('SELECT * FROM photos WHERE id = ?').bind(id).first<PhotoRow>();
  return c.json(photoView(c.env, row!), 201);
});

/** Only photos not yet part of the permanent history can be removed. */
photoRoutes.delete('/:id', async (c) => {
  const p = await c.env.DB.prepare('SELECT * FROM photos WHERE id = ?').bind(c.req.param('id')).first<PhotoRow>();
  if (!p) notFound('Photo not found');
  if (p.event_id) conflict('This photo is part of the bonsai history and cannot be deleted');
  const table = p.subject_type === 'bonsai' ? 'bonsai' : 'pots';
  await c.env.DB.batch([
    c.env.DB.prepare(`UPDATE ${table} SET cover_photo_id = NULL WHERE id = ? AND cover_photo_id = ?`).bind(p.subject_id, p.id),
    c.env.DB.prepare('DELETE FROM photos WHERE id = ?').bind(p.id),
  ]);
  await c.env.PHOTOS.delete(p.r2_key);
  return c.body(null, 204);
});
