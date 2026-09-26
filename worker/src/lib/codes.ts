import type { Env } from '../types';

export type CodeKind = 'bonsai' | 'pot';

/** Allocates the next sequential code, e.g. POTR-B-00042 / POTR-P-00007. */
export async function nextCode(env: Env, kind: CodeKind): Promise<string> {
  const row = await env.DB.prepare('UPDATE counters SET value = value + 1 WHERE name = ? RETURNING value')
    .bind(kind)
    .first<{ value: number }>();
  if (!row) throw new Error(`counter ${kind} missing`);
  const letter = kind === 'bonsai' ? 'B' : 'P';
  return `${env.CODE_PREFIX}-${letter}-${String(row.value).padStart(5, '0')}`;
}

export function codeKind(env: Env, code: string): CodeKind | null {
  const m = new RegExp(`^${env.CODE_PREFIX}-([BP])-\\d+$`, 'i').exec(code);
  if (!m) return null;
  return m[1].toUpperCase() === 'B' ? 'bonsai' : 'pot';
}

export function certificateUrl(env: Env, code: string): string {
  return `${env.PUBLIC_BASE_URL.replace(/\/$/, '')}/c/${code}`;
}

export function mediaUrl(env: Env, r2Key: string): string {
  return `${env.PUBLIC_BASE_URL.replace(/\/$/, '')}/media/${r2Key}`;
}
