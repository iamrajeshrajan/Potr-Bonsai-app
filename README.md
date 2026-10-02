# Potr Bonsai Master

This is the inventory and growth catalogue for **Potr – Pots on the Rocks**. Every bonsai gets a
**traceable certificate of authenticity** that records its origin, age, pruning, repotting and wiring.
A QR card on the pot links to that certificate. Items marked for sale are published to the
WooCommerce shop at [potr.in](https://potr.in). Bonsai pots are a second product category, and each pot is unique.

```
 Android app (staff)  ──HTTPS + access key──▶  Cloudflare Worker  ──▶  D1 (SQLite)  data + history
   register / photos / care / QR scan              /api/*              R2           photos
                                                   /c/{code}   ◀── anyone scanning the QR card
                                                   /cards      printable QR cards
                                                   cron  ──────▶  WooCommerce REST API (potr.in)
                                      webhook ◀──────────────────  WooCommerce order events
```

Everything runs on free tiers: **GitHub** (code, Actions CI, APK builds) and **Cloudflare**
(Workers, D1, R2, cron triggers).

| Folder | What |
| --- | --- |
| [`worker/`](worker) | Cloudflare Worker (TypeScript + Hono): API, certificate pages, QR cards, WooCommerce sync |
| [`android/`](android) | Android staff app (Kotlin + Jetpack Compose) |
| [`docs/SETUP.md`](docs/SETUP.md) | Step-by-step deployment (Cloudflare, WooCommerce, APK) |
| [`docs/QUESTIONS.md`](docs/QUESTIONS.md) | Open questions for Potr, with the defaults already built in |

## What it does

**Inventory with traceable history**
- Register a bonsai with a photo, species, style, origin (seed, cutting, air layer, yamadori…), source,
  age (documented or estimated), size, pot, and its location in the nursery. Each bonsai gets a code
  such as `POTR-B-00001`.
- Care log: pruning, repotting (with soil mix), wiring (with wire), defoliation, fertilising, pest
  treatment, measurements, awards, notes and photo updates. Each entry can include photos.
- **Tamper-evident history.** Entries are append-only. Each one is sealed into a SHA-256 hash chain
  that includes the fingerprints of its photos. Editing identity fields (species, origin, age…) adds a
  *correction* entry, so the record stays complete. The certificate re-checks the chain every time it
  is viewed and shows "History verified".
- Scheduled care: set due dates and repeat intervals (for example, check wiring every 30 days).
  Marking a task done writes the matching history entry and schedules the next one.

**Photos**
- The app compresses every photo on the phone to a **WebP under 100 KB** before upload: the long side is
  at most 1200 px and the best quality that fits is chosen. Re-encoding also strips location data. The
  server refuses anything larger.
- Photos are stored in Cloudflare R2. **Every photo is kept** as the bonsai's growth record and shown on
  its certificate. The **newest photo becomes the featured image** in the app, on the certificate and
  in the potr.in shop listing. Only that one image is sent to WooCommerce, so the potr.in media library
  holds one small image per listed item.
- At 100 KB per photo, R2's free 10 GB holds about 100,000 photos. For example, 500 bonsai with 4 photos
  a year for 10 years comes to about 2 GB.

**QR cards**
- Each bonsai or pot links to `https://<your domain>/c/<code>`. A customer who scans it with any phone
  sees the public certificate. On a staff phone the link opens the item directly in the app.
- `/card/<code>` and `/cards?codes=A,B,C` print business-card-size (85 × 55 mm) QR cards, 10 per A4 sheet.

**Catalogue and shop**
- Switch an item to *for sale* and give it a price. It is created or updated as a WooCommerce product
  with the item code as its SKU, its featured (newest) photo, details, and the certificate link.
- A WooCommerce order webhook marks the bonsai (and its pot) **sold**, adds it to the history, and sets
  shop stock to 0. If the order is cancelled or refunded, the item goes back into stock.
- Failed shop syncs are retried every 30 minutes.

**Pots** are their own catalogue (`POTR-P-00001`): maker, origin, material, glaze, shape, size and
photos. A pot holding a tree is marked *in use* and is sold with that tree. Empty pots can be listed in
the shop's pots category.

**Team**: admins add staff in the app. The app shows a one-time setup QR, and the staff member scans
it to sign in. Access keys can be revoked or reissued at any time, for example if a phone is lost.

## Development

```bash
cd worker && npm install && npm test     # integration tests run in the real Workers runtime
npm run dev                               # local API at http://localhost:8787
```

The Android app is built by GitHub Actions (`.github/workflows/android.yml`) and the APK is uploaded
as a build artifact. For local work, open `android/` in Android Studio.
