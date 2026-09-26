export interface Env {
  DB: D1Database;
  PHOTOS: R2Bucket;
  PUBLIC_BASE_URL: string;
  CODE_PREFIX: string;
  BRAND_NAME: string;
  SHOP_URL: string;
  WOO_URL: string;
  WOO_CATEGORY_BONSAI_ID: string;
  WOO_CATEGORY_POTS_ID: string;
  ANDROID_CERT_SHA256: string;
  ANDROID_PACKAGE: string;
  BOOTSTRAP_SECRET?: string;
  WOO_CONSUMER_KEY?: string;
  WOO_CONSUMER_SECRET?: string;
  WOO_WEBHOOK_SECRET?: string;
}

export interface User {
  id: string;
  name: string;
  email: string | null;
  role: 'admin' | 'staff';
}

export type AppEnv = { Bindings: Env; Variables: { user: User } };

export interface PotRow {
  id: string;
  code: string;
  name: string;
  maker: string | null;
  origin: string | null;
  material: string | null;
  glaze: string | null;
  shape: string | null;
  length_cm: number | null;
  width_cm: number | null;
  height_cm: number | null;
  year_made: number | null;
  condition: string | null;
  description: string | null;
  internal_notes: string | null;
  cost_inr: number | null;
  status: 'in_stock' | 'in_use' | 'reserved' | 'sold' | 'retired';
  for_sale: number;
  price_inr: number | null;
  cover_photo_id: string | null;
  woo_product_id: number | null;
  woo_sync_status: 'none' | 'pending' | 'synced' | 'error';
  woo_sync_error: string | null;
  woo_image_ids: string | null;
  created_at: string;
  updated_at: string;
}

export interface BonsaiRow {
  id: string;
  code: string;
  name: string | null;
  species_common: string;
  species_botanical: string | null;
  style: string | null;
  origin_type: string | null;
  origin_location: string | null;
  source_name: string | null;
  acquired_on: string | null;
  start_year: number | null;
  age_basis: 'documented' | 'estimated' | null;
  height_cm: number | null;
  trunk_cm: number | null;
  spread_cm: number | null;
  pot_id: string | null;
  location: string | null;
  description: string | null;
  internal_notes: string | null;
  cost_inr: number | null;
  status: 'active' | 'reserved' | 'sold' | 'deceased' | 'archived';
  for_sale: number;
  price_inr: number | null;
  cover_photo_id: string | null;
  woo_product_id: number | null;
  woo_sync_status: 'none' | 'pending' | 'synced' | 'error';
  woo_sync_error: string | null;
  woo_image_ids: string | null;
  created_at: string;
  updated_at: string;
}

export interface EventRow {
  id: string;
  bonsai_id: string;
  seq: number;
  type: string;
  performed_on: string;
  performed_by: string | null;
  notes: string | null;
  details: string;
  photo_hashes: string;
  is_public: number;
  recorded_by: string | null;
  created_at: string;
  prev_hash: string;
  hash: string;
}

export interface PhotoRow {
  id: string;
  subject_type: 'bonsai' | 'pot';
  subject_id: string;
  event_id: string | null;
  r2_key: string;
  content_type: string;
  bytes: number;
  sha256: string;
  caption: string | null;
  taken_on: string | null;
  uploaded_by: string | null;
  created_at: string;
}

export interface TaskRow {
  id: string;
  bonsai_id: string;
  type: string;
  title: string | null;
  due_on: string;
  notes: string | null;
  recurrence_days: number | null;
  status: 'open' | 'done' | 'cancelled';
  assigned_to: string | null;
  completed_event_id: string | null;
  created_by: string | null;
  created_at: string;
  completed_at: string | null;
}

/** Care event types understood by the app and certificate. */
export const EVENT_TYPES = [
  'registered',
  'photo',
  'pruning',
  'repotting',
  'wiring',
  'wire_removal',
  'defoliation',
  'styling',
  'fertilizing',
  'pest_treatment',
  'measurement',
  'pot_change',
  'location_change',
  'award',
  'note',
  'correction',
  'reserved',
  'sold',
  'status_change',
] as const;
export type EventType = (typeof EVENT_TYPES)[number];

/** Event types a person can record directly (the rest are system generated). */
export const MANUAL_EVENT_TYPES: readonly EventType[] = [
  'photo',
  'pruning',
  'repotting',
  'wiring',
  'wire_removal',
  'defoliation',
  'styling',
  'fertilizing',
  'pest_treatment',
  'measurement',
  'award',
  'note',
  'correction',
];

export const EVENT_LABELS: Record<EventType, string> = {
  registered: 'Registered',
  photo: 'Photo update',
  pruning: 'Pruning',
  repotting: 'Repotting',
  wiring: 'Wiring',
  wire_removal: 'Wire removed',
  defoliation: 'Defoliation',
  styling: 'Styling',
  fertilizing: 'Fertilising',
  pest_treatment: 'Pest / disease treatment',
  measurement: 'Measurement',
  pot_change: 'Pot change',
  location_change: 'Moved',
  award: 'Award / exhibition',
  note: 'Note',
  correction: 'Correction',
  reserved: 'Reserved',
  sold: 'Sold',
  status_change: 'Status change',
};
