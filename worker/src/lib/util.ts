import { HTTPException } from 'hono/http-exception';

export function uuid(): string {
  return crypto.randomUUID();
}

export function nowIso(): string {
  return new Date().toISOString();
}

export function todayIso(): string {
  return nowIso().slice(0, 10);
}

export function badRequest(message: string): never {
  throw new HTTPException(400, { message });
}

export function notFound(message = 'Not found'): never {
  throw new HTTPException(404, { message });
}

export function conflict(message: string): never {
  throw new HTTPException(409, { message });
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export function isIsoDate(v: unknown): v is string {
  return typeof v === 'string' && ISO_DATE.test(v) && !Number.isNaN(Date.parse(v));
}

export function addDays(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** Field spec used to validate and coerce JSON bodies into DB columns. */
export type FieldType = 'string' | 'number' | 'int' | 'bool' | 'date' | { enum: readonly string[] };

export function pickFields(
  body: Record<string, unknown>,
  spec: Record<string, FieldType>,
): Record<string, string | number | null> {
  const out: Record<string, string | number | null> = {};
  for (const [key, type] of Object.entries(spec)) {
    if (!(key in body)) continue;
    const v = body[key];
    if (v === null || v === '') {
      out[key] = null;
      continue;
    }
    if (type === 'string') {
      if (typeof v !== 'string') badRequest(`${key} must be a string`);
      out[key] = v.trim();
    } else if (type === 'number' || type === 'int') {
      const n = typeof v === 'string' ? Number(v) : v;
      if (typeof n !== 'number' || !Number.isFinite(n)) badRequest(`${key} must be a number`);
      if (type === 'int' && !Number.isInteger(n)) badRequest(`${key} must be a whole number`);
      out[key] = n;
    } else if (type === 'bool') {
      if (typeof v !== 'boolean') badRequest(`${key} must be true or false`);
      out[key] = v ? 1 : 0;
    } else if (type === 'date') {
      if (!isIsoDate(v)) badRequest(`${key} must be a date (YYYY-MM-DD)`);
      out[key] = v;
    } else {
      if (typeof v !== 'string' || !type.enum.includes(v)) {
        badRequest(`${key} must be one of: ${type.enum.join(', ')}`);
      }
      out[key] = v;
    }
  }
  return out;
}

export async function readJson(req: Request): Promise<Record<string, unknown>> {
  try {
    const body = await req.json();
    if (body && typeof body === 'object' && !Array.isArray(body)) return body as Record<string, unknown>;
  } catch {
    // fall through
  }
  badRequest('Request body must be a JSON object');
}

export function escapeHtml(s: unknown): string {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
