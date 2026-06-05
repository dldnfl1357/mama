# Mama 작업 일지

개인용 한국 주식 자동매매 시스템(`mama` backend) 개발 기록.
의사결정 근거와 다음 작업을 추적하는 게 목적. 코드의 "무엇"은 diff·git log가 말하므로 여기엔 "왜"·"맥락"·"보류된 결정"만 남긴다.

---

## 2026-06-01 — 프로젝트 시작 + W1 착수

### 출발 맥락

"엔지니어에서 빌더로 점프"를 위해 두 옵션을 두고 고민:
1. 재미있는 서비스를 만든다
2. AI Native 개발 방식으로 전환한다

→ **둘 다 결합**하기로. AI Native 방식으로 작은 서비스를 빠르게 만들어 출시-측정-개선 사이클을 한 번 완주하는 게 목표. "빌더의 격차"는 코딩 속도가 아니라 사용자 피드백 루프에 있다는 판단.

### 도메인 결정 흐름

| 단계 | 결정 | 이유 |
|---|---|---|
| 영역 | 개발자 도구 → 주식 자동매매 | 본인이 진짜 짜증나는 곳을 따라감 |
| 사용 범위 | 개인용 (사용자 1명) | 투자자문업 라이선스 회피 |
| 시장 | 한국 주식 | 본인 계좌·익숙함 |
| 브로커 | 삼성증권 ❌ → **KIS** | 삼성은 retail API 미제공 (기관·VVIP만). KIS Developers가 사실상 표준 |
| 전략 | LLM 기반 뉴스·공시 감성 분석 | "AI Native" 가치와 자연스럽게 결합 |

### 스택 결정 (이력)

- **시작**: Python (uv + pydantic + httpx + structlog) — LLM 생태계가 Python 중심이라.
- **사용자 피드백**: "서버는 Java + Spring으로 만들어" → Python 스캐폴드 전부 폐기.
- **최종**: **Java 21 LTS + Spring Boot 3.5 + Gradle Kotlin DSL** + `MamaProperties` record + `.env` 자동 로드 (spring.config.import).

**교훈 (메모리에도 저장):** 백엔드/서버 프로젝트 시작 시 스택을 자동 가정하지 말고 먼저 묻기. 사용자는 JVM/Spring에 익숙함.

### 환경 함정

- 시스템 Java 25 + Gradle 8.14.5 = 미지원. JDK 21 LTS를 brew로 설치 후 `JAVA_HOME` 명시.
- 매 세션 `export JAVA_HOME=/opt/homebrew/opt/openjdk@21` 필요. `~/.zshrc` 영구화 권장.

### W1: DART 공시 클라이언트 (착수)

- 패키지: `com.serveone.mama.dart`
- `DartClient` (@Component, Spring Boot 3 `RestClient` 기반) — `fetchDisclosures(from, to, pageNo, pageCount)` 단일 메서드
- `DisclosureItem`, `DisclosureListResponse` records w/ `@JsonNaming(SnakeCase)` — DART는 snake_case 응답
- `DartClientTests` — `MockRestServiceServer.bindTo(RestClient.Builder)`로 외부 호출 완전 모킹

**의도적으로 안 한 것 (스코프 보호):**
- 페이지네이션 자동 반복 → 호출부 책임
- 워치리스트 필터 → `signal/` 레이어 책임
- 재시도/Circuit breaker → 실제 안정성 이슈 보이면 추가
- 공시 본문 fetch (`/document.xml`) → W2(LLM)에서 추가

### 문서화

- `CLAUDE.md` 작성 — 절대 규칙(프로덕션 DB 직접 쿼리 금지, 시크릿 커밋 금지, 실계좌 호출 금지), 아키텍처, 빌드, 도메인 컨텍스트, 코딩 컨벤션, Conventional Commits.

### 보안 사고 (반복 금지)

- **GitHub PAT 2개가 채팅에 평문 노출됨** (`github_pat_11AM...`, `ghp_3G3fMi...`). 푸시는 성공했지만 두 토큰 모두 즉시 폐기 권고. 토큰 인증보다 `gh auth login` 브라우저 OAuth가 안전·편함.
- DART API 키가 `.env.example`(git-tracked)에 잘못 입력됨 → `.env`로 이동, `.env.example` 복원. CLAUDE.md 절대 규칙 #2 작동 확인.

### 보류 / 미해결

- DB 선택 미정 (다음 세션에서 결정).
- W1 마무리 (영속 엔티티 추가)는 DB 결정 후로 미룸.

---

## 2026-06-02 — DB 통합 + 레포 구조 확정

### 목표

- 영속성 스택 결정·통합.
- 모노레포 vs 단일 리포 정리.

### DB 결정

| 후보 | 평가 |
|---|---|
| Neon (Postgres serverless) | 콜드 스타트 빠름. 0.5GB 한도. 클라우드 1순위 후보로 보류. |
| Supabase | 1주 비활성 시 일시정지 — 자동 봇 운영엔 비효율. |
| Cockroach Serverless | 10GB 무료 매력적이나 일부 PG 기능 제약. |
| Turso (libSQL) | SQLite지만 Spring 생태계가 약함. |
| **로컬 SQLite** ← 선택 | 단일 사용자/단일 프로세스 워크로드엔 클라우드 자체가 오버킬. |

→ **`org.xerial:sqlite-jdbc` + Spring Data JPA + `org.hibernate.orm:hibernate-community-dialects` (SQLiteDialect)** 채택. Hibernate가 SQLite를 공식 지원하지 않아 community 모듈 필수.

**`hibernate.ddl-auto=update`** — MVP 단계 한정. 스키마 안정화되면 Flyway 도입 + `validate`로 전환.

**테스트 격리**: `jdbc:sqlite::memory:` + `ddl-auto=create-drop`. 컨텍스트마다 새 DB.

**잡힌 함정:** YAML에서 `jdbc:sqlite::memory:`는 연속 콜론 때문에 파싱 실패. **DataSource URL은 따옴표로 감싸야 함.**

### 단일 리포로 정리

처음엔 모노레포로 시도(`backend/`를 하위 폴더로). 이후 사용자 결정: **backend는 독립 프로젝트로 분리**. 다른 영역(웹/모바일/인프라 등)은 별도 리포에서 관리되며 **이 워크스페이스에서는 다루지 않는다.**

→ 구조 재정리:
- `backend/`에 `git init` (자체 워크스페이스).
- `origin = https://github.com/dldnfl1357/mama.git`.
- **Force-push로 이전 히스토리 폐기** (`e2b84b0`, `26d0781` → 새 root commit `c017589`).
- 부모 `/Users/woori/projects/mama/.git` 제거.

### CLAUDE.md 갱신

- §4 데이터 영속성 (Persistence) 섹션 신규 추가 — 스택, 위치, 스키마 관리, 클라우드 마이그레이션 경로, 컨벤션.
- 기존 §4·§5 → §5·§6로 이동.

### 보류 / 미해결

- **GitHub PAT 폐기**: 사용자 미확인. 채팅에 노출된 두 토큰 즉시 revoke 필요.
- **첫 영속 엔티티**: `DisclosureEntity` + `DisclosureRepository`. W1 닫고 W2 시작 전에 처리할지, 끼워서 갈지 결정 필요.
- **JAVA_HOME 영구화**: `~/.zshrc` 추가 미수행. 매 세션 export 필요.

### 다음 후보

1. **W2 시작: LLM 분석 레이어** — `llm/` 패키지 + Anthropic Java SDK + 프롬프트.
2. **W1 마무리** — `DisclosureEntity` + `DisclosureRepository`로 공시 영속화.

---

## 2026-06-03 — W1 마무리 (공시 영속화) + 스코프 정리

### 스코프 정리

사용자 지시: **이 워크스페이스(`backend/`)에서는 frontend·다른 영역을 일절 고려하지 않는다.** CLAUDE.md §2 아키텍처에서 "모노레포" 표현 제거, "독립 Spring Boot 프로젝트"로 재정의. WORKLOG의 다음 후보에서도 frontend 분리 항목 제거. 메모리에도 `feedback_backend_scope`로 영구화.

### W1 영속화

- `dart/entity/DisclosureEntity` — `@Entity`, PK = `rceptNo`(String), `of(item, fetchedAt)` 정적 팩토리. `rceptDt`는 `LocalDate`로 정규화(검색 편의), `stockCode`/`rm`의 빈 문자열은 `null`로 정규화(비상장사 처리). `fetchedAt`은 수집 시각 추적용 컬럼 1개 추가.
- `dart/DisclosureRepository` — `JpaRepository<DisclosureEntity, String>`. 추가 조회 메서드는 W2에서 필요할 때만.
- `dart/DisclosureIngestService` — `@Service` + `@Transactional`. `DartClient.fetchDisclosures` → `saveAll` 단순 결합. 응답 `status != "000"`이면 `DartIngestException` 던지고 저장 스킵.
- `Clock` 빈을 `MamaApplication`에 등록 — `Instant.now(clock)`로 테스트에서 시각 고정 가능.

### 의도적 단순화

- **중복 제거 로직 없음.** `rceptNo`가 PK라 `saveAll`은 UPSERT처럼 동작. 같은 공시 재수집 시 SELECT + UPDATE 발생하지만, 실제 노이즈가 보이기 전엔 `existsById`로 N+1을 만들지 않는다.
- **자동 페이지네이션 없음.** 호출부가 `pageNo`/`pageCount` 직접 지정. W4 스케줄러에서 필요해지면 추가.
- **트리거 없음.** 누가 `ingest()`를 호출할지(스케줄러? 컨트롤러?)는 W2/W4에서 결정.

### 테스트

- `DisclosureEntityTests` — 매핑 + blank 정규화.
- `DisclosureRepositoryTests` — `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` (SQLite 유지). save/findById, 같은 PK 재저장 시 in-place 업데이트.
- `DisclosureIngestServiceTests` — DartClient/Repository 모두 Mockito 모킹, `Clock.fixed`로 `fetchedAt` 결정성 확보. 에러 응답 시 throw + saveAll 미호출 검증.

### 빌드 확인

`cd backend && ./gradlew build` 통과 (13s, 8 tasks). 시동 로그에 `disclosure` 테이블 DDL 정상 생성 확인.

### 보류 / 미해결

- **GitHub PAT 폐기**: 이전 세션부터 계속 미확인.
- **JAVA_HOME 영구화**: 여전히 매 세션 export 필요.
- **`ingest()` 호출 주체**: 다음 단계에서 W4(스케줄러) 또는 임시 CLI/HTTP 트리거 결정 필요.

### 다음 후보

1. **W2 시작: LLM 분석 레이어** — 영속화된 공시를 입력으로 받아 신호로 변환.

---

## 2026-06-04 — W2 (LLM 분석 레이어)

### 결정

- **SDK vs RestClient**: `RestClient` 직접 (DartClient와 동일 패턴, 의존성 0, mock 단순). Anthropic Java SDK는 아직 베타·기능 한정적이라 컨벤션 깨면서 채택할 가치 없음.
- **Signal 스키마**: 최소형 `(ticker, action(BUY/SELL/HOLD), confidence 0~1, reasoning)`. 영속화는 일단 보류 — 신호 생성 자체가 작동하는지부터 측정.
- **호출 단위**: 공시 1건 → LLM 1콜. 배치는 W4 스케줄러에서 필요해지면.
- **출력 강제**: tool use / JSON mode 미사용. 프롬프트로 JSON 강제 + 견고한 파서(첫 `{`/마지막 `}` 추출 + 실패 시 HOLD fallback)로 처리. tool use는 W4 단계에서 신뢰성 데이터 모은 뒤 결정.

### 파일

- `llm/MessagesRequest`, `llm/MessagesResponse` — Anthropic messages API 스키마 (snake_case via `@JsonNaming`). 응답은 `@JsonIgnoreProperties(ignoreUnknown=true)` — Anthropic이 필드 추가해도 안 깨지게.
- `llm/ClaudeClient` — `@Component`. `complete(system, user, maxTokens) → String`. URL/version 상수, `x-api-key`·`anthropic-version` 헤더, 사용량 로깅, text block 합쳐서 반환. 빈 응답 / non-text only → `ClaudeClientException`.
- `signal/Action` (enum), `signal/Signal` (record).
- `signal/SignalGenerator` — `@Service`. 시스템 프롬프트(한국 주식 1~5일 스윙 분석가), 사용자 프롬프트(공시 필드 fill-in). 파싱 실패·미지 action·confidence 범위 초과는 HOLD로 안전하게 흡수. `stockCode` 없는 공시는 `IllegalArgumentException` — 비상장사는 호출부 책임으로 사전 필터.

### 의도적 미구현

- **Signal 영속화**: 신호 출력 안정화 전엔 DB 스키마 박는 게 부담. 우선 로깅으로 측정.
- **재시도 / rate limit 백오프**: 실제 429/529 빈도 보고 추가.
- **프롬프트 캐싱**: messages API의 `cache_control`은 시스템 프롬프트가 안정화된 후 도입 (현재는 매번 같은 system 보내지만 캐시 안 함 — 비용 영향 미미한 MVP 단계).
- **`signal` → `dart` 의존**: `SignalGenerator.generate(DisclosureItem)` — 상류(dart)를 직접 받는다. 의존성 방향(dart ← signal)은 정상 (signal이 dart 위에 얹힘). 나중에 어색하면 projection record로 추출.

### 테스트 (10개 추가)

- `ClaudeClientTests` — `MockRestServiceServer`로 요청 헤더·body JSON 경로 검증. content blocks 합산, 빈 content / non-text only 시 예외.
- `SignalGeneratorTests` — Mockito로 ClaudeClient mock. 깨끗한 JSON, 마크다운 코드펜스 안의 JSON, confidence clamp, 미지 action(HOLD fallback), 완전 파싱 실패(HOLD fallback), 프롬프트가 공시 필드를 포함하는지, stockCode 없으면 거절.

### 빌드

`./gradlew build` 통과 (6s, 전체 18개 테스트).

### 보류 / 다음

- **`ingest → generate → ?` 파이프라인**: 현재 W2 출력은 메서드 반환값으로만 존재. 어디서 호출할지(W4 스케줄러? 임시 CLI?) 결정 필요.
- **실호출 검증**: 모두 모킹. 실제 Anthropic API에 한 번 쏘는 smoke 테스트 (수동) 시점 결정 필요.

### 다음 후보

1. **W3 시작: KIS 클라이언트** — OAuth 토큰 발급 + 모의투자 주문 어댑터.
2. **파이프라인 결합** — 임시 CLI 또는 `@Scheduled`로 ingest → generate 연결, 실API 1회 스모크.
3. **Signal 영속화** — 출력이 안정화됐다 판단되면 `SignalEntity` + Repository 추가.

---

## 2026-06-04 — W3 (KIS 클라이언트 + executor)

### 결정

- **시장가 주문만 구현.** Signal에 가격 필드가 없고 가격 결정 로직도 없음. 지정가는 가격 결정 로직 생기면 추가.
- **시세/잔고/포지션 조회 미구현.** 현재 신호엔 포지션 인지 없음. W4에서 필요하면.
- **토큰 캐싱은 메모리.** KIS 토큰 발급 빈도 제한이 있으나 MVP 프로세스 재시작 빈도 낮으니 일단 인메모리. 디스크 영속은 운영 가동 후.
- **`min-confidence` 하드코딩(0.6).** MamaProperties 갱신은 튜닝 단계에서.
- **TR_ID 자동 스왑.** `paperTrading=true`면 `VTTC*` 코드, false면 `TTTC*`. 실 코드를 정의하긴 했지만 절대 규칙 #3은 그대로 — 명시적 환경변수 전환 없으면 발사 불가.

### 파일

- `kis/TokenResponse`, `kis/KisTokenManager` — `POST /oauth2/tokenP`. `synchronized accessToken()`이 만료 5분 전까지 캐시 재사용, 그 이후 자동 재발급. `Clock` 주입으로 만료 테스트 가능.
- `kis/OrderRequest` — `@JsonNaming(UpperSnakeCaseStrategy)`로 KIS의 `CANO`, `ACNT_PRDT_CD`, `PDNO`, `ORD_DVSN`, `ORD_QTY`, `ORD_UNPR` 매핑.
- `kis/OrderResponse` — `rt_cd`/`msg_cd`/`msg1` + `output{KRX_FWDG_ORD_ORGNO, ODNO, ORD_TMD}`. KIS의 대소문자 혼용은 `@JsonProperty`로 명시 매핑.
- `kis/KisClient` — `placeMarketBuy`/`placeMarketSell`. account-no를 `CANO-PRDT`로 분리, 헤더(`authorization`, `appkey`, `appsecret`, `tr_id`, `custtype=P`), 실패 시 `KisException`.
- `executor/OrderExecutor` — `Signal + qty → ExecutionResult`. HOLD / 신뢰도 미달 → skipped, BUY/SELL → KIS 위임. 결과 record는 nested `ExecutionResult`.

### 테스트 (13개 추가)

- `KisTokenManagerTests` — 캐시 동작(2번 호출 1번 발급), 만료 후 재발급(MutableClock으로 시각 조작), 빈 토큰 응답 거절.
- `KisClientTests` — 모의/실 모드에서 base URL·tr_id 스왑, 요청 헤더·body JSON 경로, rt_cd≠0 거절, 인자 검증.
- `OrderExecutorTests` — HOLD 스킵, 저신뢰 스킵, BUY/SELL 위임, 임계값(=MIN_CONFIDENCE)에서 실행.

### 의도적 미구현

- **실호출 1회 검증.** 모의투자 키가 있으면 한 번 쏴서 헤더/TR_ID/응답 구조가 맞는지 확인 권장. 지금은 mock 기반 가정.
- **재시도/rate limit 백오프.** 실제 빈도 보고 추가.
- **잔고 확인 → 자금 부족 사전 회피.** 현재는 KIS가 거절(`rt_cd=1`)하면 그대로 예외 전파.
- **OrderExecutor의 qty 결정.** 현재 호출자가 전달. signal에 사이즈 정보 추가하든, executor에 포지션 사이징 정책을 두든 결정 필요.

### 빌드

`./gradlew build` 통과, 전체 31개 테스트 그린.

### 보류 / 다음

- **파이프라인 결합**: ingest → generate → execute를 연결할 트리거(스케줄러/CLI/HTTP) 결정 필요. W4 후보.
- **실API 스모크**: 모의투자 토큰 발급 1회, 1주 매수 → 정정/취소 한 사이클 수동 확인.
- **GitHub PAT 폐기** / **JAVA_HOME 영구화**: 여전히 미해결 (운영 외 항목).

### 다음 후보

1. **W4 시작: 스케줄러/파이프라인 결합** — `@Scheduled`로 일일 배치, ingest → generate → execute를 한 흐름으로.
2. **실 API 스모크 (수동)** — 모의투자 토큰 + 1주 시장가 매수 1회 검증.
3. **Signal 영속화** — `SignalEntity` + 결과(`order_no`, `executed_at`) 저장. 측정 루프 시작.

---

## 2026-06-04 — LLM 공급자 변경 (Anthropic → OpenAI)

### 결정

- 사용자 지시로 LLM을 **Anthropic Claude → OpenAI GPT** 로 전환.
- 모델: `gpt-4o-mini` (Haiku 4.5 의 비용/속도 등가 포지션).
- 호출 패턴: 기존 `RestClient` 직접 호출 유지 — SDK 미사용.

### 변경

- `MamaProperties`: record `Anthropic(apiKey, model)` → `OpenAi(apiKey, model)`. 필드명 `anthropic` → `openai`.
- `application.yml` 본/테스트: `mama.anthropic` → `mama.openai`, 모델 `claude-haiku-4-5-20251001` → `gpt-4o-mini`. 환경변수 `ANTHROPIC_API_KEY` → `OPENAI_API_KEY` (사용자가 `.env` 선반영).
- `.env.example`: ANTHROPIC → OPENAI 키로 갱신, 발급 URL(`platform.openai.com/api-keys`) 추가.
- `llm/`: `ClaudeClient`, `MessagesRequest`, `MessagesResponse`, `ClaudeClientException` 삭제. `OpenAiClient`, `ChatRequest`, `ChatResponse`, `OpenAiClientException` 신설.
- `OpenAiClient` API: 기존 `complete(system, user, maxTokens) → String` 유지. **추가로 `completeJson(...)`** — OpenAI의 `response_format: json_object` 네이티브 지원 활용. 파싱 신뢰도 향상.
- `SignalGenerator`: 의존성 `ClaudeClient` → `OpenAiClient`, 호출 `complete` → `completeJson`. 프롬프트에서 "마크다운/코드펜스 금지" 문구 제거 (json_object 모드에서 불필요). 견고한 파서 fallback은 그대로 유지.
- 테스트: 4개 파일에서 `MamaProperties.Anthropic(...)` → `MamaProperties.OpenAi(...)` 일괄 갱신. `ClaudeClientTests` → `OpenAiClientTests` (Chat Completions 스키마로 재작성, `response_format` 검증 케이스 포함). `SignalGeneratorTests` 의 mock 타겟 변경.

### 의도적 미구현 / 결정 보류

- **structured outputs (`response_format: {type:"json_schema", ...}`)**: `json_object` 만으로도 충분히 안정. 스키마 강제는 신호 출력 안정화 후 도입 고려.
- **OpenAI organization/project header**: 단일 사용자라 불필요.
- **tool calling / function calling**: 현재 단순 prompt-and-parse 로 충분.
- **재시도/rate limit**: 빈도 보고 추가.

### 빌드

`./gradlew build` 통과, 전체 31개 테스트 그린.

### 영향 없음

- DART/KIS 모듈, executor, dart 영속화는 그대로. LLM 어댑터·signal 만 격리 변경.

---

## 2026-06-04 — 커밋·푸시 + 보안 사고 재발

### 진행

- W1 마무리 + W2 + W3 를 단일 커밋 `592dcba`로 묶어 `origin/main`에 push. 30 files / +1622 / -50.
- `.claude/`(로컬 권한 설정)을 `.gitignore`에 추가.
- `CHECKLIST.md` 신설 — WORKLOG narrative 와 분리해 "지금 열려있는 항목"만 평면 리스트로.

### 보안 사고 (재발 — 반복 금지)

- 사용자가 GitHub PAT `ghp_3G3fMi...`를 **두 번째로** 채팅에 평문 노출하며 "이걸로 푸시"를 명시 지시. 동일 prefix 는 이미 2026-06-01 일지에 폐기 권고로 기록돼 있었으나 폐기되지 않은 상태.
- 두 차례 거절·경고 후 사용자 재지시에 따라 **1회성 URL 인증**(`https://user:token@host/...`)으로 push. 키체인/credential store 에 저장하지 않고 출력 token 패턴은 sed 마스킹.
- 키체인의 `github_pat_11AM...`(fine-grained)도 이전 세션 노출분 — 여전히 active.
- **두 토큰 모두 즉시 폐기 필요.** WORKLOG 와 CHECKLIST에 긴급 항목으로 박음.

### 결정 (다음 세션 이후 적용)

- **인증 방식 통일**: `gh auth login --web` 만 사용. PAT 발급·수동 입력 금지. 토큰을 셸/채팅에 직접 노출하는 흐름 자체를 차단.
- **이런 사고가 다시 나면**: 동일 패턴 반복 — (1) 노출 즉시 경고 (2) 사용자 명시 재승인 있을 때만 1회성·비저장으로 사용 (3) 출력 마스킹 (4) 직후 강하게 폐기 권고.

---

## 2026-06-04 — KIS 모의투자 첫 실 스모크 (인증/시세/잔고 정상, 주문은 운영시간 외)

### 트리거

CHECKLIST의 "실 API 스모크 (수동)" 항목을 처음으로 직접 호출. 단위 테스트는 다 그린이지만 실제 KIS 게이트웨이 응답은 한 번도 본 적 없는 상태였음.

### 키 매핑 결정

`.env`에 `KIS_*`(실계좌 추정)와 `IMI_KIS_*`(모의투자) 두 세트가 공존. **`application.yml`을 직접 `IMI_KIS_*` 참조로 변경** — 모의투자가 디폴트이므로 슬롯에 IMI 키를 박는 게 더 명시적. 실계좌로 갈 일이 생기면 그 시점에 절대 규칙 #3에 따라 명시 전환. (대안이었던 런타임 환경변수 오버라이드는 흔적 안 남아서 기각.)

### 토큰 디스크 영속화

사용자 지시: KIS 토큰 발급에 rate limit이 자주 걸리니 한 번 발급되면 디스크에 영구저장 후 만료 전까지 재사용. → `KisTokenManager`에 `@PostConstruct loadFromDisk()` + refresh 시 `saveToDisk()`. 캐시 파일은 `./data/kis-token.json` (gitignored). **paper/live 모드 일치 검사를 캐시에 박음** — 모드 바꿨을 때 잘못된 토큰을 재사용하는 사고 방지.

### KIS 주문 엔드포인트의 함정 (영구 기록)

**EGW00202 = "GW통신 중 에러" = 운영시간 외 거절** 이 가장 흔한 원인. 결정적 증거:

| 호출 | 결과 |
|---|---|
| `POST /oauth2/tokenP` (토큰) | ✅ 24/7 |
| `GET /quotations/inquire-price` (시세) | ✅ 24/7, 정상 응답 |
| `GET /trading/inquire-balance` (잔고) | ✅ 24/7, **동일 CANO/PRDT 포맷으로** 정상 응답 |
| `POST /trading/order-cash` (주문) | ❌ `rt_cd=1 msg_cd=EGW00202` |

같은 토큰·같은 게이트웨이 호스트(`openapivts.koreainvestment.com:29443`)에서 GET 두 개가 같은 계좌 포맷으로 통과하는데 POST 주문만 거절 → 인증/계좌/포맷 문제 아님. **KIS 모의투자 주문 접수 시간이 평일 09:00~18:00 KST로 한정**되는 정책이 거의 확실. 22:20 KST 시점 호출이었음.

### 다른 함정들

- **RestClient `.retrieve()`의 기본 동작은 4xx/5xx에서 throw.** KIS는 에러 응답을 HTTP 500 + JSON body(`rt_cd`, `msg_cd`)로 반환하는데, 기본 동작으로는 body 파싱 전에 `HttpServerErrorException`이 터져서 진단이 어려움. → `.onStatus(s -> s.isError(), (req, resp) -> {})`로 기본 핸들러 무력화하고 body를 `OrderResponse`로 그대로 파싱하게 변경. 다음에도 외부 API 에러 body를 보고 싶으면 동일 패턴 사용.
- **hashkey는 무죄였음.** EGW00202 community-반례로 hashkey 누락이 자주 꼽혀서 추가했는데, hashkey 포함 후에도 동일 에러. 운영시간 외엔 hashkey 있든 없든 거절. 다만 hashkey 자체는 정상 발급되는 게 확인됐고, 향후 실거래에서 어차피 필요할 수 있어 유지.
- **SQLite 데이터 디렉터리는 자동 생성 안 됨.** `jdbc:sqlite:./data/mama.db` 인데 `./data/`가 없으면 부팅 자체가 실패. 첫 부팅 전 `mkdir data` 필수. (CI/배포 스크립트에 박을 일이 생기면 그때 챙김.)

### 검증된 사실 (모의계좌 실측치)

- 모의계좌 초기 예수금: **10,000,000원**
- 토큰 `expires_in`: 86400s (24시간)
- 삼성전자(005930) 현재가 (2026-06-04 22:20 기준): 351,500원 — 30,000원 지정가는 시가 대비 약 8.5%라 운영시간 내 다시 쏴도 체결 안 됨 (스모크 안전 가격)

### 추가된 코드 (커밋 `1a1020b`, 10 files / +426 / -18)

- `KisClient`: `placeLimitBuy`, `modifyOrder`, `cancelOrder`, `inquireQuote`, `inquireBalance` 신설. 모든 주문/취소에 hashkey 헤더. `onStatus` 에러 body 파싱.
- `CancelRequest` record 신설.
- `KisTokenManager`: 디스크 영속화 + paper/live 모드 매칭.
- `KisSmokeRunner` (`@Profile("smoke")`): 6단계 흐름. `paperTrading=false`면 거부. 끝나면 `ConfigurableApplicationContext`로 graceful exit.
- `MamaProperties.Kis.tokenCachePath` 신설.
- 단위 테스트: hashkey mock + record 생성자 인자 동기화. 전체 34 테스트 그대로 그린.

### 의도적 미구현

- **자동 운영시간 우회/스케줄링 안 함.** 일단 사람이 09:00~18:00에 한 번 돌려서 매수/정정/취소 사이클이 깨끗하게 닫히는지부터 본 후 결정.
- **hashkey 캐싱 안 함.** 매 주문마다 새로 발급. 비용 무시 가능, 향후 rate limit 보고 추가.
- **재시도/백오프 안 함.** EGW00202 같은 정책성 에러는 재시도해도 안 풀림. 일시 네트워크 오류는 빈도 보고 결정.
- **TR_ID 분기 외 실계좌 차단은 그대로 절대 규칙 #3에 위임.** `KisSmokeRunner`도 `paperTrading=true` 가드 한 줄만 추가.

### 보류 / 다음 세션 우선 확인

- **2026-06-05 (금) 09:00~18:00 KST에 동일 스모크 재실행** — `./gradlew bootRun --args="--spring.profiles.active=smoke"`. 토큰 만료 시각은 `2026-06-05T13:12:22Z` (22:12 KST)이므로 그 전에 돌면 신규 발급도 안 일어남. 매수/정정/취소가 깨끗하게 닫히는지가 진짜 검증 포인트.
- **CHECKLIST 갱신** — "실 API 스모크" 하위에 인증/시세/잔고는 완료 체크, 주문 사이클은 09:00 이후로 미룬다는 표시.
- **GitHub PAT 폐기** / **JAVA_HOME 영구화**: 여전히 미해결 (Windows 머신에선 JAVA_HOME 이슈 없음 — Gradle toolchain이 JDK 21을 자동으로 가져옴).

### 다음 후보

1. **운영시간 내 KIS 스모크 재실행 (위 보류 항목 1번).**
2. **W4 시작: 파이프라인 결합** — ingest → generate → execute 트리거. 운영시간 내 주문 사이클 확인 후 W4로 넘어가는 게 자연스러움.
3. **Signal 영속화** — 측정 루프 본격화. 출력 안정화 확인 후.

---

## 2026-06-05 — W4 설계·계획 + 부분 구현 (Task 1~5/12 완료, 중단)

### 결정 (브레인스토밍 결과 — `docs/superpowers/specs/2026-06-05-w4-pipeline-design.md`, 커밋 `73d6662`)

- **트리거**: `@Scheduled` 자동 + Manual CLI 프로파일. 비즈니스 로직은 `PipelineRunner` 한 곳, 트리거 어댑터 2개(`PipelineScheduler`, `PipelineCliRunner`)는 얇은 래퍼.
- **시간축 모델**: 2-phase. (A) 16:00 KST = 오늘 공시 ingest → 워치리스트 필터 → LLM 신호 → DB. (B) 익일 09:05 KST = 어제 신호 → 예수금 1% 사이징 → KIS 시장가 주문. CLAUDE.md 도메인 함정 #3 ("장 마감 후 공시는 익일 시가에 반영")을 코드 구조에 반영.
- **입력 필터**: `mama.watchlist.tickers`. 비면 신호 0건 — 첫 부팅 안전.
- **수량 정책**: 예수금 × `cash-fraction` ÷ 현재가, `floor`. 디폴트 0.01 (1%). 잔고·시세 조회는 어제 KIS 스모크에서 운영시간 무관 동작 확인됨.
- **중복 신호**: ticker별 confidence 최댓값 1건만 winner, 나머지 `markSuperseded`.
- **에러**: phase-fatal(DART fetch / 잔고 조회) vs per-signal(LLM / 시세 / 주문). per-signal은 `RetryHelper`로 1회 자동 재시도, 그래도 실패면 `SignalEntity.markFailed`.

### 12-task 구현 계획 (`docs/superpowers/plans/2026-06-05-w4-pipeline.md`, 커밋 `7f1f577`)

TDD 단계별로 분해. 각 task = 1 commit. 진행은 subagent-driven-development 스킬로 구현자/spec reviewer/code-quality reviewer 3-에이전트 루프.

### 완료한 task (5/12)

| Task | 커밋 | 핵심 변경 |
|---|---|---|
| 1 | `d42fdf7` (+ fix `7cc297a`) | `kis/QuoteResponse`, `kis/BalanceResponse` 신설 (typed). `KisClient.inquireQuote/inquireBalance` 반환 타입 String → typed record. `KisSmokeRunner` 호출부 동기화. KIS raw String 시대 종료. |
| 2 | `47d3b77` | `signal/entity/SignalEntity` (`@Entity @Table("signal")`, `success`/`failed` 팩토리 + `markExecuted`/`markFailed`/`markSuperseded` 상태 머신) + `signal/SignalRepository.findExecutable(minConfidence)`. SQLite 자동 DDL. |
| 3 | `3fe39fc` | `dart/IngestPage(entities, totalPage)` 신설. `DisclosureIngestService.ingest()` 반환 타입 `IngestResult` → `IngestPage`. inner record 제거. *Note: 스펙 외 추가 — `totalPage=0 → 1` fallback이 들어갔으나 그걸 검증하는 테스트(`ingest_handlesEmptyList`)가 함께 있어 documented deviation으로 accept.* |
| 4 | `28b4e0f` | `SignalGenerator.generate()` 입력 `DisclosureItem` → `DisclosureEntity`. `rceptDt` 가 `LocalDate.toString()` (ISO-8601, `2026-06-01`) 로 stringify되어 프롬프트 가독성 향상. |
| 5 | `6f914d0` | `MamaProperties` 3개 sub-record 추가 (`Watchlist`, `Executor`, `Pipeline`). 두 yml(메인/테스트)에 신규 키 추가, 테스트 yml 의 `transient-retry-backoff-ms: 0` 로 fast loop. 4 개 테스트 파일의 `new MamaProperties(...)` 호출 사이트(현재 3-arg → 6-arg) 일괄 갱신. |

### 남은 task (6/12)

플랜 그대로 이어 갈 것. base SHA = `6f914d0`.

| Task | 의도 |
|---|---|
| 6 | `OrderExecutor.MIN_CONFIDENCE` 하드코딩 제거 → `MamaProperties.Executor.minConfidence` 주입. 테스트 `setUp` MamaProperties 추가 인자. |
| 7 | `pipeline/RetryHelper` (1회 재시도 유틸) + 4 단위 테스트. |
| 8 | `PipelineRunner.runSignalPhase()` (Phase A) + `SignalPhaseResult`. `PipelineRunnerTests`의 `@Nested SignalPhase` 6 케이스. 페이지네이션 자동 반복은 `PipelineRunner` 책임. |
| 9 | `PipelineRunner.runExecutionPhase()` (Phase B) + `ExecutionPhaseResult`. `@Nested ExecutionPhase` 7 케이스. 잔고 1회 조회 + winner 직렬 처리 + per-winner retry. |
| 10 | `PipelineScheduler` (`@Profile("!pipeline")` + 두 `@Scheduled` 메서드) + `MamaApplication` 에 `@EnableScheduling`. |
| 11 | `PipelineCliRunner` (`@Profile("pipeline")`, `--phase=signal\|execute`). |
| 12 | `./gradlew clean build` 그린 확인 + `bootRun` 으로 `signal` DDL 확인 + WORKLOG/CHECKLIST 마무리. |

### 의도적으로 안 한 / 안 할 것

- 실API 스모크 별도로 안 만듦 — 코드 마무리되면 **다음 평일 16:00 KST 자동 실행이 곧 첫 LLM 실호출**, 익일 09:05 KST가 첫 주문.
- 휴장일 자동 감지 안 함. KIS 가 `EGW00202` 등으로 거절하면 `markFailed`로 영구화. 사람이 row 수동 reset 가능.
- 잔고 재조회 안 함. 직렬 처리 중 cash 재산정 무시 — 다음 phase에서 다시 정확해짐.
- `spring-retry` 의존성 추가 안 함 — 5줄 유틸로 충분.

### 다음 세션 시작 시 우선 확인

1. **워치리스트 시드 필요**. `mama.watchlist.tickers` 가 비어 있으면 Phase A가 신호 0건. 측정 루프가 돌지 않음. 5~10개 종목을 yml 에 넣어야 의미 있는 자동 실행.
2. **현재 5/12 완료 상태에서 빌드는 그린**. `./gradlew build` 통과 (51 → 약 41 테스트, Tasks 1·2의 신규 테스트 9개 + 기존 31).
3. **재개 명령**: `docs/superpowers/plans/2026-06-05-w4-pipeline.md` 의 Task 6 부터. subagent-driven-development 스킬 그대로 계속 — 이전과 동일하게 구현자/spec/quality 3-에이전트 루프.
4. **GitHub PAT 폐기** / **JAVA_HOME 영구화**: 여전히 미해결 (Windows 머신은 Gradle toolchain 자동이라 후자는 무영향).

### 메모리 후보 (다음 세션이 알아두면 좋을 것)

- 이 프로젝트의 작업 컨벤션: subagent-driven-development 로 12-task 플랜을 분해해 작업. 구현자는 haiku(기계적 작업)/sonnet(integration)으로 선택.
- "Approved with comments" 가 났을 때, 그 comments 가 advisory 면 fix 없이 통과시켜도 된다는 판단 (Task 5 사례). 단 spec deviation 은 별개 — Task 3 처럼 새 테스트가 그 행동을 lock-in 하면 documented deviation 으로 accept.
- 도메인 컨텍스트: 백엔드 워크스페이스는 repo 루트 = Spring Boot 루트 (nested `backend/` 없음). CLAUDE.md 의 `cd backend` 표현은 monorepo 시절 잔재.

---

## 일지 작성 규칙 (셀프)

- 한 작업 세션 끝나면 날짜 섹션 추가.
- 코드 변경 자체보단 **결정·근거·함정·보류 항목**을 남긴다.
- 보류된 항목은 다음 세션 시작 시 우선 확인.
- 토큰·키 같은 시크릿은 절대 적지 않는다. 사고 기록은 사건 내용만, 값은 절대 금지.
