import { Hono } from 'hono';
import type { AppEnv, EventType, TaskRow } from '../types';
import { EVENT_TYPES, MANUAL_EVENT_TYPES } from '../types';
import { eventView } from '../lib/present';
import { addDays, badRequest, conflict, isIsoDate, notFound, nowIso, pickFields, readJson, uuid } from '../lib/util';
import { findBonsai, parseEventInput, recordEvent } from './bonsai';

export const taskRoutes = new Hono<AppEnv>();

const TASK_SELECT = `SELECT t.*, b.code AS bonsai_code, COALESCE(b.name, b.species_common) AS bonsai_label
  FROM tasks t JOIN bonsai b ON b.id = t.bonsai_id`;

async function findTask(db: D1Database, id: string) {
  const t = await db.prepare(`${TASK_SELECT} WHERE t.id = ?`).bind(id).first<TaskRow & { bonsai_code: string }>();
  if (!t) notFound('Task not found');
  return t;
}

taskRoutes.get('/', async (c) => {
  const status = c.req.query('status') ?? 'open';
  const where = ['t.status = ?'];
  const args: unknown[] = [status];
  const bonsai = c.req.query('bonsai_id');
  if (bonsai) {
    where.push('(b.id = ? OR b.code = ?)');
    args.push(bonsai, bonsai.toUpperCase());
  }
  const dueBefore = c.req.query('due_before');
  if (dueBefore) {
    if (!isIsoDate(dueBefore)) badRequest('due_before must be a date');
    where.push('t.due_on <= ?');
    args.push(dueBefore);
  }
  const { results } = await c.env.DB.prepare(`${TASK_SELECT} WHERE ${where.join(' AND ')} ORDER BY t.due_on LIMIT 500`)
    .bind(...args)
    .all();
  return c.json({ items: results });
});

taskRoutes.post('/', async (c) => {
  const body = await readJson(c.req.raw);
  const b = await findBonsai(c.env, String(body.bonsai_id ?? ''));
  const v = pickFields(body, {
    type: { enum: EVENT_TYPES },
    title: 'string',
    due_on: 'date',
    notes: 'string',
    recurrence_days: 'int',
    assigned_to: 'string',
  });
  if (!v.type || !v.due_on) badRequest('type and due_on are required');
  if (typeof v.recurrence_days === 'number' && v.recurrence_days < 1) badRequest('recurrence_days must be at least 1');
  const id = uuid();
  const cols = ['id', 'bonsai_id', 'created_by', ...Object.keys(v)];
  await c.env.DB.prepare(`INSERT INTO tasks (${cols.join(', ')}) VALUES (${cols.map(() => '?').join(', ')})`)
    .bind(id, b.id, c.get('user').id, ...Object.values(v))
    .run();
  return c.json(await findTask(c.env.DB, id), 201);
});

taskRoutes.patch('/:id', async (c) => {
  const t = await findTask(c.env.DB, c.req.param('id'));
  const v = pickFields(await readJson(c.req.raw), {
    title: 'string',
    due_on: 'date',
    notes: 'string',
    recurrence_days: 'int',
    assigned_to: 'string',
    status: { enum: ['open', 'cancelled'] },
  });
  if (t.status === 'done') conflict('Completed tasks cannot be changed');
  const cols = Object.keys(v);
  if (cols.length) {
    await c.env.DB.prepare(`UPDATE tasks SET ${cols.map((k) => `${k} = ?`).join(', ')} WHERE id = ?`)
      .bind(...Object.values(v), t.id)
      .run();
  }
  return c.json(await findTask(c.env.DB, t.id));
});

/** Completing a task records the matching care event (and schedules the next one). */
taskRoutes.post('/:id/complete', async (c) => {
  const user = c.get('user');
  const t = await findTask(c.env.DB, c.req.param('id'));
  if (t.status !== 'open') conflict(`Task is already ${t.status}`);
  const body = await readJson(c.req.raw);
  const type: EventType = MANUAL_EVENT_TYPES.includes(t.type as EventType) ? (t.type as EventType) : 'note';
  const input = parseEventInput({ ...body, type, notes: body.notes ?? t.title ?? t.notes }, user);
  const b = await findBonsai(c.env, t.bonsai_id);
  const ev = await recordEvent(c.env, b, input, user);
  const stmts = [
    c.env.DB.prepare(`UPDATE tasks SET status = 'done', completed_event_id = ?, completed_at = ? WHERE id = ?`).bind(
      ev.id,
      nowIso(),
      t.id,
    ),
  ];
  let next: string | null = null;
  if (t.recurrence_days) {
    next = uuid();
    stmts.push(
      c.env.DB.prepare(
        `INSERT INTO tasks (id, bonsai_id, type, title, due_on, notes, recurrence_days, assigned_to, created_by)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).bind(
        next,
        t.bonsai_id,
        t.type,
        t.title,
        addDays(input.performed_on, t.recurrence_days),
        t.notes,
        t.recurrence_days,
        t.assigned_to,
        user.id,
      ),
    );
  }
  await c.env.DB.batch(stmts);
  return c.json({ event: eventView(ev), next_task: next ? await findTask(c.env.DB, next) : null });
});
