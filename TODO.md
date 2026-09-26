# TODO — TapConvert Play Store domination (next session starts here)

> Paste nothing else. This file is the full handoff: goal, why, quality law,
> phases with outcome-based done-states, locks, state, debt, protocol.

## 1. MISSION (the why — never optimize for anything else)

Own Play Store discovery for video compression in **10 locales from public
launch week**, compounding into millions of clicks/usage through AEO
(Ask Play answers grounded on us), full localization, benefit-led
creatives, Shorts, and ratings velocity. The buyer is a **lazy,
non-technical person** who must look at our listing and drool *"this is
exactly what I need"* in under 5 seconds. Every decision below serves that
person. A completed task that doesn't make that person tap is a failed task.

## 2. QUALITY LAW (non-negotiable — violating "done" is not done)

- **Truth:** numbers only from observed runs (52% / 9.0→4.2 MB video;
  93% MP3 extract; 11.2→4.8 MB audio). WhatsApp framing is intent-based
  ("built for the 16 MB limit") — never a landing guarantee. MP4-only video
  output disclosed. No trim/crop/merge/bitrate claims. No six-decimal
  percentages anywhere.
- **Eyes on everything:** no asset ships without being *viewed* (read tool
  renders images). Reject: sheared/clipped text or buttons; dead voids that
  read as bugs (nav hugs bottom, status sits under camera); contrast <4.5:1;
  hash/technical gibberish in focus; dirty status bars (✈ etc. — status-bar
  hygiene only, never touch app content); tofu/broken shaping (verify
  AR/HI/ZH personally); RTL breakage.
- **Process laws (each written in a past failure):** one unified sheet
  builder with freshness proof (hashes + mtimes) — never present a stale
  sheet; cache-busting filenames on every capture re-import + full studio
  reload + memory-verify before every export; box math keeps shells fully
  on-canvas (bottom ≤1920, aspect exactly 0.475); banner contrast gated by
  measurement (currently 9.4:1 after a local luminance fix to the vendored
  studio — see §5).
- **Sign-off gate:** nothing commits or goes to Console without the user's
  visual approval of pasted screenshots.

## 3. PHASES + outcome-based Definitions of Done

- **A — Website live.** DONE only when
  `https://thegoodb0rg.github.io/tap-convert/` (from `docs/site/index.html`,
  canonical source — Page deployed from `docs/site` by
  `.github/workflows/pages.yml`, docs/store + docs/screenshots stay sibling
  dirs, never merged into the site) returns 200 over HTTPS *and* passes
  W3C HTML + Rich Results (FAQPage) + mobile check + one human view.
  Purpose: the page Ask Play grounds on — claims mirror the listing 1:1.
- **B — Closed testing live.** DONE only when a tester **outside the
  account** installs via the test link on a real device, all 10 locale tabs
  preview correctly, pre-launch report is clean, sandbox Pro gating works.
  Steps: verify vc4 AAB (`aapt` versionCode + cert fingerprint vs upload
  key — tree unchanged since build, **no rebuild, no code changes**);
  upload vc4 over the vc3 Alpha draft; listing text via
  `docs/store/store-listings.csv` + graphics per locale
  (`docs/screenshots/store/<locale>/`: 6 screenshots + feature graphic);
  narrow/truthful declarations (content rating, data safety, ads, audience,
  **financial features**, **health declaration**); create the 3 frozen
  products; attach tester Group `tapconvert-testers`
  (@googlegroups.com address in Console); roll out Alpha.
- Success is measured in installs and taps. Closed testing yields zero
  public traffic — it gates production, where AEO/localization compounds.

## 4. LOCKED DECISIONS (do not re-litigate)

Compressor positioning (never "converter"); 10 locales EN FR ES pt-BR HI
ID TR VI AR zh-CN (Simplified serves TW/HK/SG/MY/diaspora — **no mainland
China, Play is blocked there**); translation bar = drafted simply +
reviewed; EN UI in captures; lagoon palette, mixed compositions, larger
devices; **NO app code changes (veto ACTIVE — commit only, never edit)**;
hosting = same-repo `docs/index.html`, github.io default; testers = Google
Group `tapconvert-testers`; scope = Alpha closed testing.

## 5. STATE (facts)

- Repo `theGoodB0rg/tap-convert`, `master`, tree clean at start of next
  session. Keystore `C:\Users\HP\keystores\tapconvert-upload.jks` — outside
  repo, never commit. Secrets in gitignored `local.properties`.
  `dist/` gitignored (vc4 AAB lives there, untracked).
- Site source = `docs/site/` only (index + privacy), deployed via Pages
  workflow; store assets + screenshots live in `docs/store` +
  `docs/screenshots` as siblings, never copied into the site dir.
- Products (frozen, not created): `tapconvert_pro_monthly` $0.99/mo,
  `tapconvert_pro_annual` $9.99/yr, `tapconvert_pro_lifetime` $19.99.
- Console: payments profile ✓; Alpha holds vc3 draft; dev
  `7042670930824298971` / app `4974302977984924729`.
- Marketing ready: `docs/store/listing-*.md` ×10 (limits verified),
  `store-listings.csv`, 70 graphics, `ASO.md`, deck JSON (studio
  `../store-studio`, local divergence: luminance-aware banner fix in
  `src/studio/canvas/scene-renderer.tsx`).
- Infra: MuMu `127.0.0.1:7555` (wm overrides die on reboot; demo media in
  `/sdcard/Download`); browser session `daily-monetization`; studio `:3100`
  loopback (relaunch if down: `node .\node_modules\next\dist\bin\next dev
  -H 127.0.0.1 -p 3100` in `store-studio`); scripts in
  `C:\Users\HP\AppData\Local\Temp\opencode` (`tc_ui.py`, `qa.py`,
  `sheet.py <deckdir> <label>`, `fit.py`, `stitch.py`, `frame.py`).
- Copy guardrails: §2 Truth + `docs/store/ASO.md`.

## 6. RESOLVED DEBT (verified on emulator & unit test suite)

- [x] **Share-sheet path 16 MB calibration**: Fixed in `ShareTargetViewModel.kt` (`loadFromPayload` now calculates calibrated quality on intake) & `BitrateCalculator.kt` (`effectiveTargetBytes` strictly enforces `targetSize.bytes` as hard ceiling for target-size presets). Verified with unit tests & live emulator run of 76.9MB `queen-amina-story.mp4`.
- [x] **Hash filenames on result screens**: Fixed in `ExportFileNameGenerator.kt` (`sanitizeBaseName` strips `^\d{10,}_[0-9a-fA-F]{4,12}_` staging prefixes). Verified clean `TapConvert_queen-amina-story_...mp4` output generated on device.
- [x] **Share-sheet `%` decimals**: Fixed in `ShareTargetBottomSheet.kt` (rounded with `.roundToInt()`, eliminating six-decimal percentages).

### Post-Launch Follow-ups
AEO follow-ups post-launch: listing experiments, Shorts cut (seed: temp `promo_moremi.mp4`),
ratings-velocity tuning, production rollout plan.

## 7. REVIEW PROTOCOL

Contact sheet → user pastes screenshots → strict analysis → JSON/capture
fix → re-export → re-verify → repeat to sign-off. Scripts cover
structure/honesty only; aesthetics need eyes.
