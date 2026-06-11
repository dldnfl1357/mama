# Mama Backend — Checklist

지금 무엇이 열려있는가만 한눈에 보는 평면 리스트. 결정·근거의 narrative 는 `WORKLOG.md`에.

업데이트: 2026-06-11 (W4 12/12 완료 — 파이프라인 코드 마무리, 운영 가동 대기)

---

## 🔴 보안 / 운영 (긴급)

- [ ] **노출 GitHub PAT 두 개 즉시 폐기**
  - `ghp_3G3fMi...` (classic, push에 사용됨)
  - `github_pat_11AM...` (fine-grained, 키체인)
  - 폐기처: https://github.com/settings/tokens
- [ ] 신규 인증은 `gh auth login --web -h github.com`만 사용 (PAT 채팅 노출 방지)
- [ ] 셸 history에서 토큰 라인 제거 (`~/.zsh_history`)
- [x] ~~`JAVA_HOME=...` 영구화~~ — Windows 머신은 Gradle toolchain 자동, 불필요

## 🟡 운영 측 다음 (W4 마무리 후)

- [ ] **워치리스트 시드** — `mama.watchlist.tickers` 에 5~10개. 비면 Phase A 가 신호 0건.
- [ ] **SELL 사이징 정책 결정 (최종 리뷰 C1)** — 현행 cash-fraction 부분 매도는 현금 소진 시 SELL 이 조용히 묻히고, 평시에도 부분 청산만 됨. 청산형 `qty=held` 로 바꿀지 결정. 실계좌 전환 전 필수.
- [ ] **첫 자동 실행 관찰** — 평일 16:00 KST Phase A 로그·DB·OpenAI 응답 확인 → 익일 09:05 KST Phase B 주문 사이클. **Phase B 는 장일 당 1회만 (재실행 가드 없음).**
- [ ] **실 API 스모크 (수동)** 잔여
  - ~~OpenAI: 신호 생성 1회~~ — W4 첫 자동 실행이 사실상 첫 실호출. 별도 수동 안 함.
  - ~~KIS: 모의투자 토큰 발급 + 1주 시장가 매수~~ — 2026-06-04 인증/시세/잔고 OK. 주문은 운영시간 내 09:05 자동 실행으로 검증.

## 🟢 안정화 후 백로그

- [ ] DART 공시 본문 fetch (`/document.xml`) — 헤드라인만으로 신호가 약하면
- [ ] 재시도 / rate limit 백오프 강화 (KIS 429 transient 분류, OpenAI 429/529 백오프)
- [ ] Prompt caching (OpenAI 자동 prompt cache 적중률 — system prompt 안정화 후)
- [ ] Flyway 도입 + `ddl-auto=validate` — 스키마 안정화 후
- [ ] `OrderExecutor` 미사용 빈 정리 — PipelineRunner 가 KisClient 직접 호출, min-confidence/HOLD 가드는 `findExecutable` 과 중복. 삭제 또는 Phase B 가 경유하도록 결정
- [ ] Phase B 트랜잭션 경계 재검토 — `@Transactional` 이 KIS HTTP 호출을 품어 "주문 성공 + 저장 실패 → 기록 누락" 가능 (최종 리뷰 I1)
- [ ] Phase B 같은 날 재실행 가드 (idempotency) — 현재는 운영 수칙으로만 방어 (최종 리뷰 I2)
- [ ] KIS 잔고 조회 페이지네이션 (CTX cursor) — 보유 종목 많아지면
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
- [x] **W4**: 파이프라인 결합 12/12 완료 (2026-06-11)
  - Task 1~7: `d42fdf7`+`7cc297a`, `47d3b77`, `3fe39fc`, `28b4e0f`, `6f914d0`, `2d90a0c`, `fe8c761`
  - Task 8 — `PipelineRunner.runSignalPhase` (Phase A) + 6 테스트 — `ce2bdfb`
  - Task 9 — `PipelineRunner.runExecutionPhase` (Phase B) + 7 테스트 — `e48fbaf`
  - Task 10 — `PipelineScheduler` + `@EnableScheduling` — `de80399`
  - Task 11 — `PipelineCliRunner` (`--phase=signal|execute`) — `21e8403`
  - Task 12 — `clean build` 그린 + bootRun `signal` DDL 확인 + 문서 갱신
- [x] `CLAUDE.md` 단일 backend 워크스페이스로 재정의
- [x] SQLite + JPA + community-dialects 통합
