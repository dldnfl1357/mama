# Mama Backend — Checklist

지금 무엇이 열려있는가만 한눈에 보는 평면 리스트. 결정·근거의 narrative 는 `WORKLOG.md`에.

업데이트: 2026-06-04 (LLM: Anthropic → OpenAI 이전 완료)

---

## 🔴 보안 / 운영 (긴급)

- [ ] **노출 GitHub PAT 두 개 즉시 폐기**
  - `ghp_3G3fMi...` (classic, push에 사용됨)
  - `github_pat_11AM...` (fine-grained, 키체인)
  - 폐기처: https://github.com/settings/tokens
- [ ] 신규 인증은 `gh auth login --web -h github.com`만 사용 (PAT 채팅 노출 방지)
- [ ] 셸 history에서 토큰 라인 제거 (`~/.zsh_history`)
- [ ] `JAVA_HOME=/opt/homebrew/opt/openjdk@21` 를 `~/.zshrc`에 영구화

## 🟡 진행 중 / 다음 (W4 / 측정 루프)

- [ ] **W4: 파이프라인 결합** — ingest → generate → execute 트리거 결정 (`@Scheduled` vs CLI vs HTTP)
- [ ] **실 API 스모크 (수동)**
  - Anthropic: 신호 생성 1회 (모킹 외 첫 실호출)
  - KIS: 모의투자 토큰 발급 + 1주 시장가 매수 → 정정/취소 한 사이클
- [ ] **Signal 영속화** — `SignalEntity` (+ `order_no`, `executed_at`). 출력 안정화 확인 후

## 🟢 안정화 후 백로그

- [ ] DART 페이지네이션 자동 반복 (호출부 책임 → 스케줄러 단계에서)
- [ ] DART 공시 본문 fetch (`/document.xml`) — 헤드라인만으로 신호가 약하면
- [ ] KIS 토큰 디스크 영속화 (프로세스 재시작 시 재발급 부담)
- [ ] KIS 잔고/포지션 조회 — 자금 부족 사전 회피
- [ ] 재시도 / rate limit 백오프 (KIS 429, Anthropic 429/529)
- [ ] Prompt caching (OpenAI는 자동 prompt cache 적용 — system prompt 안정화 후 적중률 확인)
- [ ] Flyway 도입 + `ddl-auto=validate` — 스키마 안정화 후
- [ ] `min-confidence` 0.6 하드코딩 → `MamaProperties.Executor`로 외부화
- [ ] `OrderExecutor` 포지션 사이징 정책 — 현재 qty는 호출부가 결정
- [ ] 지정가 주문 + 가격 결정 로직 (시세 조회 포함)

## ✅ 완료

- [x] **W1**: DART 클라이언트 (`DartClient` + records)
- [x] **W1**: 공시 영속화 (`DisclosureEntity` / `Repository` / `IngestService`)
- [x] **W2**: LLM 분석 (`OpenAiClient` + Chat Completions API, `response_format: json_object`)
- [x] **W2**: 신호 생성 (`SignalGenerator` JSON 파싱 + HOLD fallback)
- [x] **W2 (마이그레이션)**: Anthropic Claude → OpenAI GPT (`gpt-4o-mini`) 전환
- [x] **W3**: KIS 인증 (`KisTokenManager` 메모리 캐싱)
- [x] **W3**: KIS 주문 (`KisClient` 시장가 매수/매도, paper/live TR_ID 자동 스왑)
- [x] **W3**: 신호 → 주문 (`OrderExecutor`)
- [x] `CLAUDE.md` 단일 backend 워크스페이스로 재정의
- [x] SQLite + JPA + community-dialects 통합
- [x] 전체 빌드 그린 (31 테스트)
- [x] 첫 푸시 (`592dcba`)
