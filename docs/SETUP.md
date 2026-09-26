# Setup guide

Setup takes about 30 minutes and uses only free tiers. You need a Cloudflare account, admin access to
the potr.in WordPress site, and this GitHub repository.

## 1. Cloudflare (API, database, photos)

```bash
cd worker
npm install
npx wrangler login
npx wrangler d1 create potr-bonsai          # copy the database_id into wrangler.toml
npx wrangler r2 bucket create potr-bonsai-photos
npx wrangler d1 migrations apply potr-bonsai --remote
```

In `worker/wrangler.toml`, set:

| Variable | Value |
| --- | --- |
| `PUBLIC_BASE_URL` | The address printed in QR codes. **Use a custom domain you will keep**, for example `https://bonsai.potr.in` (Workers → Settings → Domains & Routes → add custom domain). Cards printed with it keep working even if hosting changes later. |
| `WOO_URL` | `https://potr.in` |
| `WOO_CATEGORY_BONSAI_ID` / `WOO_CATEGORY_POTS_ID` | IDs of the "Bonsai" and "Bonsai Pots" product categories. Create the categories in WooCommerce first; the ID is shown when you hover over a category. |

Set the secrets, then deploy:

```bash
npx wrangler secret put BOOTSTRAP_SECRET       # any long random string, used once
npx wrangler secret put WOO_CONSUMER_KEY       # from step 2
npx wrangler secret put WOO_CONSUMER_SECRET
npx wrangler secret put WOO_WEBHOOK_SECRET     # any long random string, reused in step 2
npx wrangler deploy
```

Create the first admin and save the `access_key` from the response:

```bash
curl -X POST https://bonsai.potr.in/api/auth/bootstrap \
  -H 'Content-Type: application/json' \
  -d '{"secret":"<BOOTSTRAP_SECRET>","name":"Rajesh"}'
```

**Auto-deploy (optional):** add the repository secrets `CLOUDFLARE_API_TOKEN` (template *Edit Cloudflare
Workers*, plus D1 edit permission) and `CLOUDFLARE_ACCOUNT_ID`. After that, every merge to `main` runs
the tests, applies migrations and deploys.

## 2. WooCommerce (potr.in)

1. **WooCommerce → Settings → Advanced → REST API → Add key.** Choose a user who can manage products and
   set the permission to *Read/Write*. Copy the consumer key and secret into the Worker secrets from step 1.
2. **WooCommerce → Settings → Advanced → Webhooks → Add webhook**, twice: one with topic *Order created*
   and one with topic *Order updated*. For both, use:
   - Delivery URL: `https://bonsai.potr.in/api/webhooks/woocommerce`
   - Secret: the same `WOO_WEBHOOK_SECRET`
   - API version: WP REST API v3
3. The site must be served over HTTPS; the API uses Basic auth over HTTPS. Some hosts strip the
   `Authorization` header. If syncing fails with a 401 error, ask the host to pass it through.

## 3. Android app

The **Android** GitHub Actions workflow builds an APK on every push. Open the workflow run and download
the `potr-bonsai-apk` artifact.

- Before building, set `potr.serverUrl` and `potr.linkHost` in `android/gradle.properties` to your domain.
- **Signed release builds:** create a keystore once and keep it safe; losing it means you cannot update
  the app.
  ```bash
  keytool -genkey -v -keystore potr-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias potr
  base64 -w0 potr-release.jks   # add as repo secret ANDROID_KEYSTORE_BASE64
  ```
  Also add the repository secrets `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
  `ANDROID_KEY_PASSWORD`.
- **Open QR links in the app:** get the SHA-256 fingerprint with
  `keytool -list -v -keystore potr-release.jks` and set it as `ANDROID_CERT_SHA256` in `wrangler.toml`.
  The Worker then serves `/.well-known/assetlinks.json`, and Android opens certificate links straight
  in the app.
- To install the APK, enable *Install unknown apps* on each phone. Publishing to Google Play is optional
  (one-time USD 25 fee).

**First sign-in:** in the app, enter the server URL and the admin access key. After that, go to
**More → Team → Add team member**; the app shows a setup QR that the new person scans to sign in.

## 4. Printing QR cards

Open a bonsai in the app, then tap **QR → Print card**. To print a whole batch, open
`https://bonsai.potr.in/cards?codes=POTR-B-00001,POTR-B-00002,...` in a desktop browser. Print on A4 card
stock at 100% scale and cut along the guides; each card is 85 × 55 mm. Laminate the cards or use
weatherproof stock for pots kept outdoors.

## Free-tier limits (as of writing)

| Service | Free allowance | Expected use |
| --- | --- | --- |
| Workers | 100k requests/day | Thousands |
| D1 | 5 GB storage, 5M reads/day | Megabytes |
| R2 | 10 GB storage, no egress fees | About 300 KB per photo, so roughly 30k photos |
| GitHub Actions | 2,000 min/month (private repo) | About 6 min per APK build |
