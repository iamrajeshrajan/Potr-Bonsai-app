import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { HTTPException } from 'hono/http-exception';
import type { AppEnv, Env } from './types';
import { EVENT_LABELS, EVENT_TYPES, MANUAL_EVENT_TYPES } from './types';
import { requireUser } from './lib/auth';
import { codeKind } from './lib/codes';
import { ORIGIN_LABELS } from './lib/present';
import { syncPending, wooEnabled } from './lib/woo';
import { renderCards } from './pages/cards';
import { renderCertificate, renderPotPage } from './pages/certificate';
import { bonsaiRoutes } from './routes/bonsai';
import { photoRoutes } from './routes/photos';
import { potRoutes } from './routes/pots';
import { taskRoutes } from './routes/tasks';
import { bootstrapRoute, userRoutes } from './routes/users';
import { webhookRoutes } from './routes/webhooks';

const app = new Hono<AppEnv>();

app.onError((err, c) => {
  if (err instanceof HTTPException) return c.json({ error: err.message }, err.status);
  console.error(err);
  return c.json({ error: 'Internal error' }, 500);
});

// ---- Public pages -----------------------------------------------------------

app.get('/', (c) => c.redirect(c.env.SHOP_URL, 302));

/** QR target: certificate for a bonsai, piece page for a pot. */
app.get('/c/:code', async (c) => {
  const code = c.req.param('code');
  const html = codeKind(c.env, code) === 'pot' ? await renderPotPage(c.env, code) : await renderCertificate(c.env, code);
  if (!html) return c.text('Certificate not found', 404);
  return c.html(html, 200, { 'Cache-Control': 'public, max-age=60' });
});

app.get('/cards', async (c) => c.html(await renderCards(c.env, (c.req.query('codes') ?? '').split(','))));
app.get('/card/:code', async (c) => c.html(await renderCards(c.env, [c.req.param('code')])));

app.get('/media/*', async (c) => {
  const key = c.req.path.slice('/media/'.length);
  const obj = await c.env.PHOTOS.get(key);
  if (!obj) return c.text('Not found', 404);
  const headers = new Headers();
  obj.writeHttpMetadata(headers);
  headers.set('ETag', obj.httpEtag);
  headers.set('Cache-Control', 'public, max-age=31536000, immutable');
  return new Response(obj.body, { headers });
});

/** Lets the Android app open certificate links (Android App Links). */
app.get('/.well-known/assetlinks.json', (c) => {
  const fingerprints = c.env.ANDROID_CERT_SHA256.split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  return c.json(
    fingerprints.length
      ? [
          {
            relation: ['delegate_permission/common.handle_all_urls'],
            target: { namespace: 'android_app', package_name: c.env.ANDROID_PACKAGE, sha256_cert_fingerprints: fingerprints },
          },
        ]
      : [],
  );
});

// ---- API --------------------------------------------------------------------

app.use('/api/*', cors());
app.get('/api/health', (c) => c.json({ ok: true }));
app.route('/api/auth/bootstrap', bootstrapRoute);
app.route('/api/webhooks', webhookRoutes);

const api = new Hono<AppEnv>();
api.use('*', requireUser);
api.get('/meta', (c) =>
  c.json({
    event_types: EVENT_TYPES.map((t) => ({ value: t, label: EVENT_LABELS[t], manual: MANUAL_EVENT_TYPES.includes(t) })),
    origin_types: Object.entries(ORIGIN_LABELS).map(([value, label]) => ({ value, label })),
    code_prefix: c.env.CODE_PREFIX,
    public_base_url: c.env.PUBLIC_BASE_URL,
    woo_enabled: wooEnabled(c.env),
  }),
);
/** Resolves a scanned QR (full URL or bare code) to a bonsai or pot. */
api.get('/lookup', async (c) => {
  const raw = (c.req.query('q') ?? '').trim();
  const code = (raw.split('/').pop() ?? '').split('?')[0].toUpperCase();
  const kind = codeKind(c.env, code);
  const table = kind === 'pot' ? 'pots' : kind === 'bonsai' ? 'bonsai' : null;
  const row = table ? await c.env.DB.prepare(`SELECT id, code FROM ${table} WHERE code = ?`).bind(code).first() : null;
  if (!row) return c.json({ error: `No bonsai or pot with code ${code || raw}` }, 404);
  return c.json({ kind, ...row });
});
api.route('/bonsai', bonsaiRoutes);
api.route('/pots', potRoutes);
api.route('/photos', photoRoutes);
api.route('/tasks', taskRoutes);
api.route('/', userRoutes);
app.route('/api', api);

export default {
  fetch: app.fetch,
  async scheduled(_controller: ScheduledController, env: Env, ctx: ExecutionContext) {
    ctx.waitUntil(syncPending(env));
  },
} satisfies ExportedHandler<Env>;

export { app };
