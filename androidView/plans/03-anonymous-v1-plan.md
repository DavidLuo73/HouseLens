# 免登入第一版 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** 安裝 APK 即可瀏覽 Supabase 房源，優先手機 UI/UX。
**Architecture:** 原有 Compose 單模組與 Postgrest；anon 最小 SELECT；本機收藏。
**Tech Stack:** Kotlin／Compose／Supabase Postgrest／PostgreSQL。
**Spec:** ../spec.md 的第一版最新裁定與本計畫設計。

> v0.1.0可用試用交付已完成；完整B7人工／受控效能項仍OPEN，見acceptance。最新使用者icon需求另列04。

## Tasks

- [x] A1：免登入 App、資訊頁、本機收藏與回歸驗證／獨立審查。
- [x] A2：匿名最小唯讀 SQL、隔離測試、停用腳本／獨立審查。
- [x] A3：正式權限批准與設定、簽署 APK／實機 UX、產物與實際連線驗收。

# Anonymous first version — design and execution brief

User steering 2026-11-07:「第一個版本先不用登入這一塊了，有apk就可以進入使用」and「要記住UI/UX的使用體驗要好」. These override login/member requirements for v1. Original browsing/data-format/read-only/security constraints remain.

## Design

Keep Kotlin/Compose, four tabs, direct Supabase Postgrest, local favorites and filters. Launch directly into find; no login/loading-auth gate, no Google, no anonymous Auth user creation, no hidden shared login/token/service-role key. Info replaces Account: version, local-only favorites disclosure, data status; no email/sign out. Favorites use a stable project + local-device owner namespace, retain data across restarts, never automatically import former authenticated owners' data. Keep request timeout, origin isolation, finite errors/retry, decimal/date handling and all browsing features.

The single suitable architecture under existing no-server constraint is anon SELECT with publishable key. A bundled DB/service-role credential or fake authorized user is unacceptable. Production anonymous grants/exposure require a separate concrete approval; local app/SQL work is authorized now. Public data scope is only the existing minimum viewer columns/views; never expose member identities, NotificationLogs, settings or ErrorMessage; no anon writes.

UI/UX priority: open to find, four accessible navigation tabs, visible load/error/retry; sheet draft/apply/cancel; preserve conditions/scroll on detail return; favorites feedback/persistence; 360dp/200% text, real keyboard, landscape, available multiwindow. No redesign or new dependency. Respect existing brand palette, address concrete inherited UX findings when touching affected code. Never equate fixtures to live/network/performance verification.

## App task (A1)

Worktree C:/Users/David/.codex/worktrees/9611/HouseLens, feature/android-viewer HEAD aee821c plus preserved B7 WIP. Read current androidView/spec.md alongside this overriding brief, checkpoint and existing diffs. Scope androidView app/source/tests/build/validation/README only, no SQL changes by app implementer.

1. Trace graph/client/read/nav tests/callers. Add focused failing no-login startup and data transport assertions: no Auth or ViewerMembers request, only GET business requests, no bearer user token; real production root opens find, info replaces account, favorite persists after recreation/new graph. Existing origin isolation applies. No fake authorized AuthState or bypass flag.
2. Simplify live app path: graph constructs one local browse scope, data reads directly without member/session refresh, MainActivity/ViewerContent mount browse graph. Remove obsolete callback intent filter/launch path and release callback requirement. Reuse existing SessionResources/favorites/viewmodels with stable local owner where sensible. Remove auth-only dependencies and code only if truly unused, or isolate dormant existing tests without expanding toggles; explain smallest coherent choice. Preserve current WIP tests/tools by adapting to new contract.
3. Update affected tests/fixtures, release guard counts, README/acceptance for anonymous v1. Full test suite, lint, assembleDebug; preserve unique logs/XML. Actual release still needs private identity; do not generate it. Add/perform focused landscape/keyboard checks if supported. Known 591 test failure remains unresolved; don't label passing rerun as fixed.
4. Root runs escalated exact scripts prepared under F:/SourceCode/HouseLens/.superpowers/sdd/02-app-plan/transfer; all C writes/builds require escalation. Do not independently execute build/mutation commands. No commits/stage or other agents. Provide report and exact patches/test steps, root reviews/executes.

## SQL task (A2)

Read-only design first, then root-approved local implementation: reuse reviewed viewer.sql and existing isolated PostgreSQL harness. Prepare minimum separately named anon-access installer/revoke script (or coherent v1 replacement with explicit guard), no production mutations. Must retain invoker views, precise column grants, SELECT-only policies and owner/BYPASSRLS writer compatibility; no ViewerMembers access to anon. Real local PostgreSQL tests assert expected data, private-column/admin/member denials, all writes forbidden, install/reinstall/drift refusal/revoke. Do not read production credentials. Produce reviewable proposed production diff + rollback; deploy only after explicit user approval.

## Delivery (A3)

Independent app/SQL review; then concrete long-term signing approval and production anon read approval. Signed APK with hash + signer verification, physical-device browse/UX checks with existing-data protection. Never claim useful live APK until production data read works. Google/account inputs are no longer blockers. Physical network/performance/TalkBack untested items remain explicit. No push/merge/store publishing.
