# HouseLens v0.1.1 icon plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** 原有免登入App加入與網站一致的現代向量圖示，保留使用體驗與本機資料。
**Architecture:** 單一Compose module，原生vector resources，現有theme與語意／callbacks。
**Tech Stack:** Kotlin／Compose／Android vector與adaptive icon XML。
**Spec:** ../spec.md最新裁定及以下user-specific brief。

- [ ] I1：圖示／launcher／version2實作、現有回歸與正常／大字／橫向PNG核對、獨立review。
- [ ] I2：同key簽署0.1.1、hash／artifact、保留資料更新手機及文件。

# HouseLens website-aligned icons — v0.1.1

User requested:「參考一下網站版本的icon，並且調整app也加入icon，希望色系UI/UX相同，並且能夠時尚一點的icon，幫我配置進去」. Scope is app icon/visual consistency; no new browsing/auth/server feature.

## Completed delivery

The icon work was reviewed, signed as v0.1.1 and installed as a same-key update on S25+. All 21 vector/adaptive resources and UI consumers are included in the local v0.1.5 source commit. Later releases fix filter-sheet boundary gestures and add price sorting with unit-price ascending as the default. See ../handoff.md for release hashes, current delivery, verification evidence and preserved intermittent UI-test failures. Full B7 manual/performance acceptance remains open. The original implementation brief below is historical; do not repeat its completed work or use its old version as the latest release.

## Authority and current state

Worktree C:/Users/David/.codex/worktrees/9611/HouseLens; feature/android-viewer; HEAD aee821c plus fully tested anonymous-v1 WIP. Preserve everything. v0.1.0 signed APK is already installed on S25+ with REAL Find count687 verified. Existing phone app may now contain user favorites: NEVER run connectedDebugAndroidTest against that physical package or uninstall/clear it. Emulator5580 remains isolated test target. Update phone later with same signing identity and install-r only.

Existing anonymous v1: genuine Supabase Postgrest anon SELECT, four tabs, stable project/local-device-v1 favorites. SQL production approved/deployed and real DTO reads passed. No Auth/new credentials/SQL changes. Root owns docs/ledger/handoff and release/install. All C writes/builds need root escalation; provide exact scripts/patches under F:/SourceCode/HouseLens/.superpowers/sdd/02-app-plan/transfer.

## Design and minimum implementation

- Use repo-native Android vector drawables/painterResource or existing Compose vector API; no new icon dependency, downloaded font, PNG generation or hand-coded generic icon framework. Reuse actual website SVG house/heart/star geometry at frontend/src/App.vue first22lines and frontend/src/components/PropertyCard.vue lines50-80 (read-only reference; no frontend edit). Existing site is pink #FF385C, white cards, dark #222222, gray #717171. Interactive text/controls retain accessible #E31C5F theme. Avoid violet defaults.
- Make one coherent rounded outline icon family: 24dp canvas, about1.8–2dp strokes, rounded caps/joins. Selected favorites/star/brand can be filled; icons are not emoji or text glyphs. Use restrained pink selected state and neutral unselected state.
- Cover main bottom navigation Find/Explore/Saved/Districts, existing HouseLens wordmark with website house mark, Information, filter/sort/refresh, card favorite+score+history, detail back/favorite/external/history-related controls where applicable. Don't add icons beside every paragraph or move features. Preserve labels and all current semantics/IDs/callbacks; decorative icons next to text use null description, standalone actions keep existing spoken labels.
- Favorite is a compact circular48dp photo-overlay icon control (dark outline on a sufficiently opaque light surface; filled brand pink when saved), with pending disabled behavior preserved. Never shrink touch targets or make icon tap also open detail.
- Keep existing typography/white/pink/gray layout, no broad redesign or toolbar reshuffle that breaks large text. Existing Find headline can become a house-logo+wordmark row without duplicating the brand elsewhere.
- Add real adaptive launcher icon for API26+, safe-zone-correct foreground; reuse house mark with brand tile, add Android13 monochrome resource if useful. Set manifest icon/roundIcon, no generic Android robot. No asset files from unrelated apps.
- versionCode2/versionName0.1.1 for reviewable update to installed0.1.0; same applicationId tw.houselens.viewer and existing private signing config. Do not read key/password or rebuild identity.

## Verification and completion

This is reversible visual work: no new tests mirroring vector path contents. Reuse existing navigation/card/favorite/callback tests and strengthen existing semantics checks only if a behavior genuinely changes. Root will run JVM/lint/build + API26 full360dp/font2 and key landscape/keyboard tests. Capture synthetic normal-font and200% screenshots, view them, adjust if needed; verify icons in all four tabs and details plus launcher shape. Keep final no-screenshot/sleep readiness helpers and full visible48dp/editor assertions intact. Never weaken assertions for icons.

Write report F transfer/task-icons-report.md with touched consumers, final icon mapping, verification evidence and concerns. Root dispatches independent scoped review, builds signed0.1.1, checks artifact/signature/hash, installs as update preserving device data if phone connected, updates handoff/acceptance and commits local branch. No push/merge/store upload. Do not stage/commit, touchSQL/backend/frontend/oldandroid, spawn agents or run your own C mutations/builds.
