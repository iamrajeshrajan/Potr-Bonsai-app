import type { BonsaiRow, Env, EventRow, PhotoRow, PotRow } from '../types';
import { certificateUrl, mediaUrl } from './codes';

export const ORIGIN_LABELS: Record<string, string> = {
  seed: 'Grown from seed',
  cutting: 'Cutting',
  air_layer: 'Air layer',
  graft: 'Graft',
  nursery_stock: 'Nursery stock',
  yamadori: 'Collected (yamadori)',
  import: 'Imported',
  other: 'Other',
};

export function originLabel(t: string | null): string {
  return t ? (ORIGIN_LABELS[t] ?? t) : '';
}

export function ageYears(b: Pick<BonsaiRow, 'start_year'>, now = new Date()): number | null {
  return b.start_year == null ? null : Math.max(0, now.getUTCFullYear() - b.start_year);
}

export function ageText(b: Pick<BonsaiRow, 'start_year' | 'age_basis'>, now = new Date()): string {
  const y = ageYears(b, now);
  if (y == null) return '';
  const years = `${y} year${y === 1 ? '' : 's'}`;
  return b.age_basis === 'documented' ? years : `approx. ${years}`;
}

export function photoView(env: Env, p: PhotoRow) {
  return {
    id: p.id,
    url: mediaUrl(env, p.r2_key),
    sha256: p.sha256,
    caption: p.caption,
    taken_on: p.taken_on,
    event_id: p.event_id,
    created_at: p.created_at,
  };
}

export function eventView(e: EventRow) {
  return {
    id: e.id,
    seq: e.seq,
    type: e.type,
    performed_on: e.performed_on,
    performed_by: e.performed_by,
    notes: e.notes,
    details: JSON.parse(e.details) as Record<string, unknown>,
    is_public: e.is_public === 1,
    created_at: e.created_at,
    hash: e.hash,
  };
}

export function bonsaiView(env: Env, b: BonsaiRow, cover?: PhotoRow | null) {
  const { woo_image_ids: _ignored, ...rest } = b;
  return {
    ...rest,
    for_sale: b.for_sale === 1,
    age_years: ageYears(b),
    certificate_url: certificateUrl(env, b.code),
    cover_photo_url: cover ? mediaUrl(env, cover.r2_key) : null,
  };
}

export function potView(env: Env, p: PotRow, cover?: PhotoRow | null) {
  const { woo_image_ids: _ignored, ...rest } = p;
  return {
    ...rest,
    for_sale: p.for_sale === 1,
    cover_photo_url: cover ? mediaUrl(env, cover.r2_key) : null,
  };
}
