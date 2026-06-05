# Mama Backend — Checklist

지금 무엇이 열려있는가만 한눈에 보는 평면 리스트. 결정·근거의 narrative 는 `WORKLOG.md`에.

업데이트: 2026-06-05 (W4 5/12 진행 — 중단, 다음 세션 Task 6 부터 재개)

---

## 🔴 보안 / 운영 (긴급)

- [ ] **노출 GitHub PAT 두 개 즉시 폐기**
  - `ghp_3G3fMi...` (classic, push에 사용됨)
  - `github_pat_11AM...` (fine-grained, 키체인)
  - 폐기처: https://github.com/settings/tokens
- [ ] 신규 인증은 `gh auth login --web -h github.com`만 사용 (PAT 채팅 노출 방지)
- [ ] 셸 history에서 토큰 라인 제거 (`~/.zsh_history`)
- [x] ~~`JAVA_HOME=...` 영구화~~ — Windows 머신은 Gradle toolchain 자동, 불필요

## 🟡 진행 중 — W4 파이프라인 (5/12 완료)

스펙: `docs/superpowers/specs/2026-06-05-w4-pipeline-design.md` (커밋 `73d6662`)
플랜: `docs/superpowers/plans/2026-06-05-w4-pipeline.md` (커밋 `7f1f577`)
실행 방식: subagent-driven-development. 구현자/spec/quality 3-에이전트 루프.
재개 base SHA: `6f914d0`

- [x] Task 1 — KIS typed responses (`QuoteResponse`, `BalanceResponse`) — `d42fdf7` + fix `7cc297a`
- [x] Task 2 — `SignalEntity` + `SignalRepository.findExecutable` — `47d3b77`
- [x] Task 3 — `DisclosureIngestService` → `IngestPage` — `3fe39fc` (doc'd deviation: `totalPage=0 → 1` fallback)
- [x] Task 4 — `SignalGenerator(DisclosureEntity)` — `28b4e0f`
- [x] Task 5 — `MamaProperties.Watchlist/Executor/Pipeline` + yml + 4 test sites — `6f914d0`
- [ ] **Task 6** — `OrderExecutor` `min-confidence` 외부화 (`MamaProperties.Executor`)
- [ ] **Task 7** — `pipeline/RetryHelper` + 4 단위 테스트
- [ ] **Task 8** — `PipelineRunner.runSignalPhase` (Phase A) + `SignalPhaseResult` + 6 테스트
- [ ] **Task 9** — `PipelineRunner.runExecutionPhase` (Phase B) + `ExecutionPhaseResult` + 7 테스트
- [ ] **Task 10** — `PipelineScheduler` + `MamaApplication`의 `@EnableScheduling`
- [ ] **Task 11** — `PipelineCliRunner` (`--phase=signal|execute`)
- [ ] **Task 12** — `./gradlew clean build` 그린 + bootRun signal DDL 확인 + WORKLOG/CHECKLIST 최종 갱신

## 🟡 운영 측 다음 (W4 마무리 후)

- [ ] **워치리스트 시드** — `mama.watchlist.tickers` 에 5~10개. 비면 Phase A 가 신호 0건.
- [ ] **첫 자동 실행 관찰** — 평일 16:00 KST Phase A 로그·DB·OpenAI 응답 확인 → 익일 09:05 KST Phase B 주문 사이클.
- [ ] **실 API 스모크 (수동)** 잔여
  - ~~OpenAI: 신호 생성 1회~~ — W4 첫 자동 실행이 사실상 첫 실호출. 별도 수동 안 함.
  - ~~KIS: 모의투자 토큰 발급 + 1주 시장가 매수~~ — 2026-06-04 인증/시세/잔고 OK. 주문은 운영시간 내 09:05 자동 실행으로 검증.

## 🟢 안정화 후 백로그

- [ ] DART 공시 본문 fetch (`/document.xml`) — 헤드라인만으로 신호가 약하면
- [ ] 재시도 / rate limit 백오프 강화 (KIS 429 transient 분류, OpenAI 429/529 백오프)
- [ ] Prompt caching (OpenAI 자동 prompt cache 적중률 — system prompt 안정화 후)
- [ ] Flyway 도입 + `ddl-auto=validate` — 스키마 안정화 후
- [ ] `OrderExecutor` 포지션 사이징 정책 — Phase B는 cash-fraction 으로 사이즈 결정, OrderExecutor 자체는 호출자 qty
- [ ] 지정가 주문 + 가격 결정 로직 (시세 조회 포함)
- [ ] 휴장일 자동 감지 — 현재는 KIS 거절을 `markFailed`로 영구화
- [ ] Signal 측정·회고 (W5) — 신호 vs 다음날 수익률 join, 정확도 리포트

## ✅ 완료

- [x] **W1**: DART 클라이언트 + 공시 영속화 (`DartClient`, `DisclosureEntity`/`Repository`/`IngestService`)
- [x] **W2**: LLM 신호 (`OpenAiClient` Chat Completions, `SignalGenerator` JSON 파싱 + HOLD fallback)
- [x] **W2 (마이그레이션)**: Anthropic Claude → OpenAI GPT (`gpt-4o-mini`)
- [x] **W3**: KIS 인증 (`KisTokenManager` 메모리 + 디스크 영속화) + 주문 (`KisClient` 시장가/지정가/정정/취소) + `OrderExecutor`
- [x] **W3 스모크**: 토큰/시세/잔고 24/7 정상 / 주문은 운영시간 09:00–18:00 KST 한정 (`EGW00202` 함정 기록됨)
- [x] **W4 설계 + 플랜** (`73d6662`, `7f1f577`)
- [x] **W4 Task 1~5/12 구현** (위 진행 중 섹션 참조)
- [x] `CLAUDE.md` 단일 backend 워크스페이스로 재정의
- [x] SQLite + JPA + community-dialects 통합
