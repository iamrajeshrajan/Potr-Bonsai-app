import type { EventRow, EventType } from '../types';
import { nowIso, uuid } from './util';
import { sha256Hex } from './crypto';

export const GENESIS_HASH = '0'.repeat(64);

export interface NewEvent {
  bonsai_id: string;
  type: EventType;
  performed_on: string;
  performed_by?: string | null;
  notes?: string | null;
  details?: Record<string, unknown>;
  photo_hashes?: string[];
  is_public?: boolean;
  recorded_by?: string | null;
}

type HashInput = Pick<
  EventRow,
  'bonsai_id' | 'seq' | 'type' | 'performed_on' | 'performed_by' | 'notes' | 'details' | 'photo_hashes' | 'created_at' | 'prev_hash'
>;

/** Deterministic hash of the fields that make up the permanent record. */
export function eventHash(e: HashInput): Promise<string> {
  const canonical = JSON.stringify([
    e.bonsai_id,
    e.seq,
    e.type,
    e.performed_on,
    e.performed_by ?? null,
    e.notes ?? null,
    e.details,
    e.photo_hashes,
    e.created_at,
    e.prev_hash,
  ]);
  return sha256Hex(canonical);
}

/** Builds the next event in a bonsai's chain (not yet written). */
export async function buildEvent(db: D1Database, e: NewEvent): Promise<EventRow> {
  const last = await db
    .prepare('SELECT seq, hash FROM events WHERE bonsai_id = ? ORDER BY seq DESC LIMIT 1')
    .bind(e.bonsai_id)
    .first<{ seq: number; hash: string }>();
  const row: EventRow = {
    id: uuid(),
    bonsai_id: e.bonsai_id,
    seq: (last?.seq ?? 0) + 1,
    type: e.type,
    performed_on: e.performed_on,
    performed_by: e.performed_by ?? null,
    notes: e.notes ?? null,
    details: JSON.stringify(e.details ?? {}),
    photo_hashes: JSON.stringify(e.photo_hashes ?? []),
    is_public: e.is_public === false ? 0 : 1,
    recorded_by: e.recorded_by ?? null,
    created_at: nowIso(),
    prev_hash: last?.hash ?? GENESIS_HASH,
    hash: '',
  };
  row.hash = await eventHash(row);
  return row;
}

export function insertEventStmt(db: D1Database, r: EventRow): D1PreparedStatement {
  return db
    .prepare(
      `INSERT INTO events (id, bonsai_id, seq, type, performed_on, performed_by, notes, details, photo_hashes,
        is_public, recorded_by, created_at, prev_hash, hash)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
    .bind(
      r.id,
      r.bonsai_id,
      r.seq,
      r.type,
      r.performed_on,
      r.performed_by,
      r.notes,
      r.details,
      r.photo_hashes,
      r.is_public,
      r.recorded_by,
      r.created_at,
      r.prev_hash,
      r.hash,
    );
}

/**
 * Appends an event, running `extra` statements in the same batch (D1 batches
 * are transactional). Retries once if another write raced us for the seq.
 */
export async function appendEvent(
  db: D1Database,
  e: NewEvent,
  extra: (ev: EventRow) => D1PreparedStatement[] = () => [],
): Promise<EventRow> {
  for (let attempt = 0; ; attempt++) {
    const ev = await buildEvent(db, e);
    try {
      await db.batch([insertEventStmt(db, ev), ...extra(ev)]);
      return ev;
    } catch (err) {
      if (attempt < 2 && String(err).includes('UNIQUE')) continue;
      throw err;
    }
  }
}

export interface ChainCheck {
  valid: boolean;
  length: number;
  head: string;
  brokenAtSeq?: number;
}

/** Recomputes every hash; any edit to a stored event breaks the chain. */
export async function verifyChain(events: EventRow[]): Promise<ChainCheck> {
  let prev = GENESIS_HASH;
  const sorted = [...events].sort((a, b) => a.seq - b.seq);
  for (const [i, ev] of sorted.entries()) {
    if (ev.seq !== i + 1 || ev.prev_hash !== prev || (await eventHash(ev)) !== ev.hash) {
      return { valid: false, length: sorted.length, head: prev, brokenAtSeq: ev.seq };
    }
    prev = ev.hash;
  }
  return { valid: true, length: sorted.length, head: prev };
}
