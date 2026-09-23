# TapConvert ASO & AEO playbook

> Locked honesty guardrails: no outcome guarantees beyond observed runs
> (9.0→4.2 MB video, 11.2→4.8 MB audio extract); WhatsApp framing is
> intent-based ("built for the limit"); MP4-only video output; no
> trim/crop/merge/bitrate claims; six-decimal percentages never shown.

## Keyword map (primary queries per locale)

| Locale | Core queries |
|---|---|
| en | video compressor, compress video, reduce video size, whatsapp video, photo compressor, image to pdf, compress pdf, video to mp3 |
| fr | compresseur vidéo, compresser vidéo, réduire taille vidéo, vidéo whatsapp, compresser photo, image en pdf, compresser pdf |
| es | comprimir video, compresor de video, reducir tamaño video, video para whatsapp, comprimir foto, imagen a pdf, comprimir pdf, extraer mp3 |
| pt-BR | comprimir vídeo, compressor de vídeo, diminuir tamanho do vídeo, vídeo para whatsapp, comprimir foto, imagem para pdf, comprimir pdf, extrair mp3 |
| hi | वीडियो कंप्रेस, वीडियो कंप्रेसर, वीडियो साइज कम, व्हाट्सऐप वीडियो, फोटो कंप्रेस, इमेज टू पीडीएफ, पीडीएफ कंप्रेस |
| id | kompres video, kompresor video, perkecil ukuran video, video untuk whatsapp, kompres foto, gambar ke pdf, kompres pdf, ekstrak mp3 |
| tr | video sıkıştırma, video sıkıştırıcı, video boyut küçültme, whatsapp video, fotoğraf sıkıştırma, resimden pdf, pdf sıkıştırma |
| vi | nén video, giảm dung lượng video, video whatsapp, nén ảnh, ảnh sang pdf, nén pdf, tách mp3 |
| ar | ضغط الفيديو، ضاغط الفيديو، تصغير حجم الفيديو، فيديو واتساب، ضغط الصور، تحويل الصور إلى PDF، ضغط PDF |
| zh-CN | 压缩视频, 视频压缩, 缩小视频, WhatsApp视频, 压缩照片, 图片转PDF, 压缩PDF, 提取MP3 |

App-angle per market: WhatsApp dominates ES/pt-BR/HI/ID/AR; email caps
(Gmail 25 MB) are universal; Telegram/Instagram Reels as secondary angles
everywhere; passport-portal 200 KB resonates in IN/BR (gov uploads).

## AEO structure (Ask Play, Guided Search, Gemini recommendations)

Per Google I/O 2026 mechanics (AppTweak): Ask Play answers first from the
store description (top-weighted), then from the app website. Guided Search
and "Researched with Gemini" listicles occupy Play screens 1–3; classic
rankings sink to screen 4+.

Rules applied to every locale file in `listing-*.md`:

1. Open with literal Q&A pairs (Can it…? Yes. Does it…? Yes.) — the exact
   shape shoppers ask and Gemini quotes.
2. Numbers only from observed runs; intent language for presets
   ("built/designed for the 16 MB limit", never "guarantees under").
3. One outcome per paragraph; no spec sheet (no codecs, no bitrates,
   no resolution tables).
4. `docs/site/index.html` mirrors the same claims 1:1 (FAQPage JSON-LD)
   so listing↔website consistency holds for grounding.
5. When Console's search-trend suggestions arrive post-launch, fold the
   top queries into paragraph 1 verbs (auto-apply feature), never the Q&A.

## Per-locale production notes

- AR: RTL — captions are centered (direction-neutral); verify badge/chip
  order if UI is ever localized; marketing copy reviewed LTR-safe.
- CJK (zh): titles count CJK chars 1:1 — verified ≤30 in-file; keep
  headlines ≤8 chars/line in studio.
- HI: Devanagari shaping verified in export (matras, no tofu).
- UI captures stay English in all locales (standard practice); full
  app-strings localization is a separate effort (Console Gemini
  auto-translation available at publish).
- Console also offers free machine translation + paid human translation;
  our files are the reviewed source of truth — upload via CSV, do not
  auto-overwrite them.

## Launch ops (in Console, when in account)

1. Upload `store-listings.csv` (title/short/full × 10).
2. Upload per-locale graphics (6 screenshots + feature graphic each).
3. Enable store listing experiments (icon vs headline variants).
4. Review Console language recommendations banner after 14 days.
5. Ratings velocity: keep the 5-second review prompt (already in-app).
