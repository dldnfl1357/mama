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

## 일지 작성 규칙 (셀프)

- 한 작업 세션 끝나면 날짜 섹션 추가.
- 코드 변경 자체보단 **결정·근거·함정·보류 항목**을 남긴다.
- 보류된 항목은 다음 세션 시작 시 우선 확인.
- 토큰·키 같은 시크릿은 절대 적지 않는다. 사고 기록은 사건 내용만, 값은 절대 금지.
