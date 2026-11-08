# Verification: Phase 8 — Reader Mode Parity (Mozilla Readability Integration)

## Status: COMPLETE

### 1. Robust Multi-Stage Article Extraction
- **Root Cause**: The legacy reader mode extractor in [`PetalReaderBridge.kt`](file:///data/data/com.termux/files/home/petal/app/src/main/java/com/petal/browser/compose/reader/PetalReaderBridge.kt) used a simplistic `querySelector` heuristic with naive `<p>` length checking (>20 characters). On complex layouts (Substack, Medium, modern news with embeds, multi-column grids), this omitted article bodies, included advertisements, sidebars, cookie banners, or completely failed.
- **Fix**: Upgraded the in-page evaluation script to implement Mozilla's Readability principles:
  1. **Metadata Resolution**: Extracts high-fidelity title, byline/author, and lead hero images from OpenGraph (`og:title`, `og:image`), Twitter cards (`twitter:title`, `twitter:image`), and Schema.org metadata before DOM fallback.
  2. **Node Cloning & Deep Sanitization**: Clones candidate roots and aggressively prunes noisy tags (`script`, `style`, `noscript`, `iframe`, `svg`, `nav`, `footer`, `header`, `aside`, `form`, `[role="banner"]`, `[role="navigation"]`, `[aria-hidden="true"]`, `.advertisement`, `.social-share`, `.cookie-banner`).
  3. **Structured Content Assembly**: Gathers headings (`<h2>`, `<h3>`), paragraphs (`<p>`), blockquotes (`<blockquote>`), and lists (`<li>`), filtering disclaimers and deduplicating adjacent identical text blocks.
  4. **Preference Gating**: Gated behind `sp_mozilla_readability` (default `true`), allowing users to revert to the legacy extractor if needed.

### 2. UI Shell & Expressive Styling Preservation
- Confirmed zero modifications to [`PetalReaderScreen.kt`](file:///data/data/com.termux/files/home/petal/app/src/main/java/com/petal/browser/compose/reader/PetalReaderScreen.kt), Liquid Glass background blur snapshots, font family choices (GS_FLEX, Serif, Dyslexic), theme options, or Text-To-Speech (TTS) speech bridge.
