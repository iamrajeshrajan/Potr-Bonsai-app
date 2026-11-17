# POTR Bonsai (`in.potr.bonsai`)

Native Android (Kotlin) application for **Pots on the Rocks (`potr.in`)** to catalog, value, track care schedules, log before/after lifespan videos, and publish a 1,000-tree Bonsai collection.

## Key Features Included in v1.0
1. **Complete Interactive Bonsai Studio UI**:
   - **POTR Branding**: Pre-loaded with the official `potr` studio logo and 4 botanical specimen studio photos.
   - **1,000-Tree Collection KPIs & Views**: Instant toggle between **Grid View** and **Table View**, plus filters for **Due/Overdue Care Alerts**, **Approximate Age**, and **Published Showcase**.
   - **Valuation & Pricing Engine**: Tracks **Purchase Price**, **New Pot Price**, and **Materials & Effort**, auto-calculates **Estimated Current Price**, and records **Selling Price** in INR (`₹`).
   - **Care Alerts & Lifespan Video Timeline**: Supports **Hard Pruning (+30d)**, **Root Check (+90d)**, **Repotting (+90d)**, and reusable custom **"Other"** actions, plus Before & After video logging.
2. **On-Device Photo Compression (`PhotoCompressor.kt`)**:
   - Automatically downscales 50MP Nothing Phone camera captures to crisp **1600px JPEG (82% quality, ~250 KB)** with EXIF orientation correction before uploading to Google Drive or `potr.in`.
3. **Google Ecosystem Sync (`GoogleWorkspaceService.kt`)**:
   - **Google Sign-In**: Connects any `@gmail.com` account.
   - **Google Drive REST API v3**: Automatically creates and syncs compressed specimen photos into a dedicated `Bonsai Catalogue` Drive folder.
   - **Google Sheets REST API v4**: Creates and updates the `POTR Bonsai Master Catalogue` Google Sheet with all specimen metadata and valuation columns.
4. **Pots on the Rocks (`potr.in`) WooCommerce Publishing (`WooCommercePublisher.kt`)**:
   - Publishes specimens marked **Published** directly to `https://potr.in` under the **Bonsai** WooCommerce category using WooCommerce REST API v3 and WordPress Application Passwords.

---

## How to Build the APK on GitHub (Zero Local Setup Required)

1. **Create a New GitHub Repository**:
   - Go to [github.com/new](https://github.com/new), name the repository `potr-bonsai-android`, choose **Private** (or Public), and click **Create repository**.
2. **Upload These Project Files**:
   - Unzip `POTR-Bonsai-Android-GitHub-Ready.zip` on your computer.
   - On your new GitHub repository page, click **uploading an existing file** (or drag and drop the unzipped files and folders—making sure `.github/workflows/build-apk.yml`, `app/`, `build.gradle.kts`, `settings.gradle.kts`, and `gradle.properties` are included) and click **Commit changes**.
3. **Download Your Compiled APK**:
   - Click the **Actions** tab at the top of your GitHub repository.
   - Select the running **Build POTR Bonsai APK** workflow.
   - In ~3–4 minutes, scroll down to **Artifacts** at the bottom of the workflow summary and download **`POTR-Bonsai-APK-Build-1`**, which contains your installable `.apk` file ready for the Nothing phone.

---

## Optional: Persistent Signing Key & Google Sign-In OAuth Setup

- **Automatic Signing Out-of-the-Box**: By default, the workflow compiles an immediately installable APK without requiring any secrets.
- **Google Cloud SHA-1 Fingerprint**: In the GitHub Actions run log, open the step **"Print APK Signing SHA-1 Fingerprint"** to copy the exact `SHA-1` certificate fingerprint into your [Google Cloud Console Credentials](https://console.cloud.google.com/apis/credentials) when creating your Android OAuth 2.0 Client ID (`in.potr.bonsai`).
- **Custom Release Keystore (Optional)**: To keep the exact same signing key across every future repo rebuild, add `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` under **Settings → Secrets and variables → Actions** in your GitHub repository.
