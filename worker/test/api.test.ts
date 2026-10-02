import { env, SELF } from 'cloudflare:test';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { hmacSha256Base64 } from '../src/lib/crypto';

const BASE = 'https://bonsai.test';
let adminKey = '';

function api(path: string, init: RequestInit & { key?: string; json?: unknown } = {}) {
  const headers = new Headers(init.headers);
  if (init.key !== '') headers.set('Authorization', `Bearer ${init.key ?? adminKey}`);
  let body = init.body;
  if (init.json !== undefined) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(init.json);
  }
  return SELF.fetch(`${BASE}${path}`, { ...init, headers, body });
}

async function json<T = any>(res: Response | Promise<Response>): Promise<T> {
  return (await res).json() as Promise<T>;
}

// A 1×1 JPEG is enough; the server does not decode images.
const JPEG = Uint8Array.from(atob('/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACP/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q=='), (c) => c.charCodeAt(0));

async function uploadPhoto(
  subjectType: string,
  subjectId: string,
  extra: Record<string, string> = {},
  file = new File([JPEG], 'p.jpg', { type: 'image/jpeg' }),
) {
  const form = new FormData();
  form.set('file', file);
  form.set('subject_type', subjectType);
  form.set('subject_id', subjectId);
  for (const [k, v] of Object.entries(extra)) form.set(k, v);
  return api('/api/photos', { method: 'POST', body: form });
}

beforeAll(async () => {
  const res = await SELF.fetch(`${BASE}/api/auth/bootstrap`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ secret: 'bootstrap-secret', name: 'Rajesh' }),
  });
  expect(res.status).toBe(201);
  adminKey = (await res.json<{ access_key: string }>()).access_key;
});

afterEach(() => vi.restoreAllMocks());

describe('auth', () => {
  it('rejects missing or bad keys', async () => {
    expect((await api('/api/me', { key: '' })).status).toBe(401);
    expect((await api('/api/me', { key: 'nope' })).status).toBe(401);
    expect(await json(api('/api/me'))).toMatchObject({ name: 'Rajesh', role: 'admin' });
  });

  it('only bootstraps once and needs the secret', async () => {
    const post = (secret: string) =>
      SELF.fetch(`${BASE}/api/auth/bootstrap`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ secret }),
      });
    expect((await post('wrong')).status).toBe(403);
    expect((await post('bootstrap-secret')).status).toBe(409);
  });

  it('lets admins add staff, who cannot manage users', async () => {
    const staff = await json(api('/api/users', { method: 'POST', json: { name: 'Anu', role: 'staff' } }));
    expect(staff.access_key).toBeTruthy();
    expect(await json(api('/api/me', { key: staff.access_key }))).toMatchObject({ name: 'Anu', role: 'staff' });
    expect((await api('/api/users', { key: staff.access_key })).status).toBe(403);
    const rotated = await json(api(`/api/users/${staff.id}/rotate-key`, { method: 'POST' }));
    expect((await api('/api/me', { key: staff.access_key })).status).toBe(401);
    expect((await api('/api/me', { key: rotated.access_key })).status).toBe(200);
  });
});

describe('inventory and history', () => {
  it('registers a bonsai in a pot with traceable history', async () => {
    const pot = await json(
      api('/api/pots', { method: 'POST', json: { name: 'Tokoname oval', maker: 'Gyozan', length_cm: 30, width_cm: 22, height_cm: 7 } }),
    );
    expect(pot.code).toMatch(/^POTR-P-\d{5}$/);

    const res = await api('/api/bonsai', {
      method: 'POST',
      json: {
        species_common: 'Ficus',
        species_botanical: 'Ficus microcarpa',
        style: 'Informal upright',
        origin_type: 'air_layer',
        age_years: 12,
        age_basis: 'estimated',
        height_cm: 45,
        pot_id: pot.code,
        cost_inr: 1500,
      },
    });
    expect(res.status).toBe(201);
    const b = await res.json<any>();
    expect(b.code).toMatch(/^POTR-B-\d{5}$/);
    expect(b.age_years).toBe(12);
    expect(b.certificate_url).toBe(`${BASE}/c/${b.code}`);

    // Pot is now in use and cannot be given to another tree.
    expect((await json(api(`/api/pots/${pot.id}`))).pot.status).toBe('in_use');
    const clash = await api('/api/bonsai', { method: 'POST', json: { species_common: 'Juniper', pot_id: pot.id } });
    expect(clash.status).toBe(409);

    // Photo update with an attached photo becomes the cover.
    const photo = await json(await uploadPhoto('bonsai', b.code));
    const ev = await api(`/api/bonsai/${b.id}/events`, {
      method: 'POST',
      json: { type: 'photo', notes: 'Registration photo', photo_ids: [photo.id] },
    });
    expect(ev.status).toBe(201);
    await api(`/api/bonsai/${b.id}/events`, {
      method: 'POST',
      json: { type: 'pruning', performed_on: '2026-01-10', notes: 'Cut back to two leaves' },
    });
    await api(`/api/bonsai/${b.id}/events`, {
      method: 'POST',
      json: { type: 'measurement', details: { height_cm: 42 } },
    });
    // Editing identity fields leaves a correction on the record.
    await api(`/api/bonsai/${b.code}`, { method: 'PATCH', json: { species_botanical: 'Ficus retusa' } });

    const detail = await json(api(`/api/bonsai/${b.code}`));
    expect(detail.bonsai.cover_photo_url).toContain('/media/bonsai/');
    expect(detail.bonsai.height_cm).toBe(42);
    expect(detail.events.map((e: any) => e.type)).toEqual(['correction', 'measurement', 'pruning', 'photo', 'registered']);
    expect(detail.chain).toMatchObject({ valid: true, length: 5 });

    // Photos in the history cannot be deleted.
    expect((await api(`/api/photos/${photo.id}`, { method: 'DELETE' })).status).toBe(409);

    // Public certificate hides private data and verifies the chain.
    const cert = await SELF.fetch(`${BASE}/c/${b.code}`);
    expect(cert.status).toBe(200);
    const html = await cert.text();
    expect(html).toContain(b.code);
    expect(html).toContain('Ficus retusa');
    expect(html).toContain('History verified');
    expect(html).not.toContain('1500');

    // Media is served from R2.
    const media = await SELF.fetch(detail.bonsai.cover_photo_url);
    expect(media.status).toBe(200);
    expect(media.headers.get('Content-Type')).toBe('image/jpeg');

    // Tampering with a stored record is detected.
    await env.DB.prepare(`UPDATE events SET notes = 'Never pruned' WHERE bonsai_id = ? AND type = 'pruning'`).bind(b.id).run();
    expect(await (await SELF.fetch(`${BASE}/c/${b.code}`)).text()).toContain('could not be verified');
    expect((await json(api(`/api/bonsai/${b.id}`))).chain).toMatchObject({ valid: false, brokenAtSeq: 3 });
  });

  it('resolves scanned QR links and prints cards', async () => {
    const b = await json(api('/api/bonsai', { method: 'POST', json: { species_common: 'Bougainvillea' } }));
    expect(await json(api(`/api/lookup?q=${encodeURIComponent(`${BASE}/c/${b.code}`)}`))).toMatchObject({
      kind: 'bonsai',
      id: b.id,
    });
    expect((await api('/api/lookup?q=hello')).status).toBe(404);
    const cards = await (await SELF.fetch(`${BASE}/cards?codes=${b.code},POTR-B-99999`)).text();
    expect(cards).toContain('<svg');
    expect(cards).toContain('1 card ');
  });

  it('schedules recurring care and completes it into the history', async () => {
    const b = await json(api('/api/bonsai', { method: 'POST', json: { species_common: 'Juniper' } }));
    const task = await json(
      api('/api/tasks', { method: 'POST', json: { bonsai_id: b.code, type: 'wiring', due_on: '2026-10-01', recurrence_days: 30, title: 'Check wire' } }),
    );
    expect(task.bonsai_code).toBe(b.code);
    const done = await json(api(`/api/tasks/${task.id}/complete`, { method: 'POST', json: { performed_on: '2026-09-20' } }));
    expect(done.event).toMatchObject({ type: 'wiring', notes: 'Check wire' });
    expect(done.next_task).toMatchObject({ due_on: '2026-10-20', status: 'open' });
    expect((await api(`/api/tasks/${task.id}/complete`, { method: 'POST', json: {} })).status).toBe(409);
    const open = await json(api(`/api/tasks?bonsai_id=${b.code}`));
    expect(open.items).toHaveLength(1);
  });

  it('validates input', async () => {
    expect((await api('/api/bonsai', { method: 'POST', json: {} })).status).toBe(400);
    expect((await api('/api/bonsai', { method: 'POST', json: { species_common: 'Ficus', acquired_on: '12/1/2020' } })).status).toBe(400);
    expect((await api('/api/bonsai', { method: 'POST', json: { species_common: 'Ficus', for_sale: true } })).status).toBe(400);
    expect((await api('/api/bonsai/nope')).status).toBe(404);
    const b = await json(api('/api/bonsai', { method: 'POST', json: { species_common: 'Ficus' } }));
    const big = new File([new Uint8Array(100 * 1024 + 1)], 'big.jpg', { type: 'image/jpeg' });
    const tooBig = await uploadPhoto('bonsai', b.id, {}, big);
    expect(tooBig.status).toBe(400);
    expect((await tooBig.json<{ error: string }>()).error).toContain('100 KB');
  });
});

describe('shop', () => {
  function mockWoo() {
    const calls: { method: string; url: string; body: any }[] = [];
    const real = globalThis.fetch;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const req = new Request(input as RequestInfo, init);
      if (!req.url.startsWith('https://shop.test/')) return real(input as RequestInfo, init);
      const body: any = req.method === 'GET' ? null : await req.json();
      calls.push({ method: req.method, url: req.url, body });
      expect(req.headers.get('Authorization')).toBe(`Basic ${btoa('ck_test:cs_test')}`);
      if (req.method === 'GET') return Response.json([]);
      return Response.json({ id: 501, sku: body?.sku });
    });
    return calls;
  }

  it('publishes a bonsai for sale and marks it sold from an order webhook', async () => {
    const calls = mockWoo();
    const pot = await json(api('/api/pots', { method: 'POST', json: { name: 'Blue glaze round' } }));
    const b = await json(
      api('/api/bonsai', { method: 'POST', json: { species_common: 'Ficus', name: 'Old Friend', age_years: 20, pot_id: pot.id } }),
    );
    await uploadPhoto('bonsai', b.id);

    await api(`/api/bonsai/${b.id}`, { method: 'PATCH', json: { for_sale: true, price_inr: 25000 } });
    const sync = await json(api(`/api/bonsai/${b.id}/sync`, { method: 'POST' }));
    expect(sync).toMatchObject({ woo_sync_status: 'synced', woo_product_id: 501 });
    const created = calls.find((c) => c.method === 'POST' || c.method === 'PUT')!;
    expect(created.body).toMatchObject({
      sku: b.code,
      status: 'publish',
      regular_price: '25000',
      stock_quantity: 1,
      categories: [{ id: 11 }],
    });
    // Only the featured (newest) photo goes to the shop.
    expect(created.body.images).toHaveLength(1);
    expect(created.body.images[0].src).toContain('/media/bonsai/');

    // A newer photo replaces the featured image; all photos stay in the growth record.
    const newer = await json(await uploadPhoto('bonsai', b.id, {}, new File([JPEG, new Uint8Array([1])], 'n.webp', { type: 'image/webp' })));
    // The upload pushes it to the shop straight away; a later sync does not re-send it.
    await api(`/api/bonsai/${b.id}/sync`, { method: 'POST' });
    const puts = calls.filter((c) => c.method === 'PUT');
    expect(puts.filter((c) => c.body.images).at(-1)!.body.images).toEqual([{ src: newer.url, alt: b.code }]);
    expect(puts.at(-1)!.body.images).toBeUndefined();
    const withBoth = await json(api(`/api/bonsai/${b.id}`));
    expect(withBoth.photos).toHaveLength(2);
    expect(withBoth.bonsai.cover_photo_url).toBe(newer.url);
    expect(created.body.description).toContain(`${BASE}/c/${b.code}`);

    // Pots inside a tree cannot be listed on their own.
    expect((await api(`/api/pots/${pot.id}`, { method: 'PATCH', json: { for_sale: true, price_inr: 100 } })).status).toBe(400);

    const order = JSON.stringify({ id: 9001, number: '9001', status: 'processing', line_items: [{ sku: b.code }] });
    const bad = await SELF.fetch(`${BASE}/api/webhooks/woocommerce`, {
      method: 'POST',
      headers: { 'X-WC-Webhook-Signature': 'forged' },
      body: order,
    });
    expect(bad.status).toBe(401);
    const ok = await SELF.fetch(`${BASE}/api/webhooks/woocommerce`, {
      method: 'POST',
      headers: { 'X-WC-Webhook-Signature': await hmacSha256Base64('hook-secret', order) },
      body: order,
    });
    expect(await ok.json()).toMatchObject({ matched: [b.code] });

    const after = await json(api(`/api/bonsai/${b.id}`));
    expect(after.bonsai.status).toBe('sold');
    expect(after.pot.status).toBe('sold');
    expect(after.events[0]).toMatchObject({ type: 'sold', details: { order: '9001' } });
    expect(after.chain.valid).toBe(true);
    expect(await (await SELF.fetch(`${BASE}/c/${b.code}`)).text()).toContain('In a private collection');

    // Stock goes to zero in the shop and images are not re-sent.
    const resync = await json(api(`/api/bonsai/${b.id}/sync`, { method: 'POST' }));
    expect(resync.woo_sync_status).toBe('synced');
    const last = calls.filter((c) => c.method === 'PUT').at(-1)!;
    expect(last.body).toMatchObject({ stock_quantity: 0 });
    expect(last.body.images).toBeUndefined();

    // A cancelled order puts the tree back.
    const cancel = JSON.stringify({ id: 9001, status: 'cancelled', line_items: [{ sku: b.code }] });
    await SELF.fetch(`${BASE}/api/webhooks/woocommerce`, {
      method: 'POST',
      headers: { 'X-WC-Webhook-Signature': await hmacSha256Base64('hook-secret', cancel) },
      body: cancel,
    });
    expect((await json(api(`/api/bonsai/${b.id}`))).bonsai.status).toBe('active');
  });

  it('lists a loose pot in the pots category', async () => {
    const calls = mockWoo();
    const pot = await json(
      api('/api/pots', { method: 'POST', json: { name: 'Nanban cascade', maker: 'Local potter', for_sale: true, price_inr: 4200 } }),
    );
    await json(api(`/api/pots/${pot.id}/sync`, { method: 'POST' }));
    const sent = calls.find((c) => c.method === 'POST')!;
    expect(sent.body).toMatchObject({ sku: pot.code, categories: [{ id: 12 }], regular_price: '4200', stock_quantity: 1 });
  });
});
