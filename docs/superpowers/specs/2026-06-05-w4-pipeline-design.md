# W4 — 파이프라인 결합 (ingest → generate → execute)

- 작성일: 2026-06-05
- 스코프: W4 (`pipeline` 패키지 신설) + Signal 영속화 (`SignalEntity` 신설) + KIS typed 응답 보강
- 선행 상태: W1(DART ingest)·W2(LLM signal)·W3(KIS execute) 모듈 완성, 모듈 간 결합·트리거 없음

---

## 1. 목표와 범위

DART 공시 수집·LLM 신호 생성·KIS 주문 세 모듈을 두 개의 자동 phase로 결합한다. 핵심은 **CLAUDE.md 도메인 함정 #3 — 장 마감 후 공시는 익일 시가에 반영**을 코드 구조에 그대로 반영하는 것:

- **Phase A (16:00 KST)** — 오늘 DART 공시 → 워치리스트 필터 → LLM 신호 → DB 영속화
- **Phase B (09:05 KST, 익일)** — 어제 신호 로드 → 예수금 1% 사이징 → KIS 모의 주문 실행

스코프 안: 비즈니스 로직 1개 + 트리거 어댑터 2개 (`@Scheduled`, CLI), `SignalEntity`·`SignalRepository`, KIS 잔고·시세 typed 파싱, 워치리스트/사이징/임계값 외부화.

스코프 밖: 실계좌 전환, 휴장일 자동 감지, 백오프 라이브러리(`spring-retry`), 자동 알림, 신호 품질 회고(W5 후보).

---

## 2. 결정 요약 (브레인스토밍 결과)

| 결정 | 값 |
|---|---|
| 트리거 | `@Scheduled` 자동 + Manual CLI 프로파일. 비즈니스 로직은 `PipelineRunner` 한 곳, 트리거 두 어댑터는 얇은 래퍼 |
| 시간축 모델 | 2-phase: 16:00 신호 생성·영속화 / 익일 09:05 주문 실행 |
| 입력 필터 | 워치리스트 종목만 (`mama.watchlist.tickers`). 비면 신호 0건 — 첫 부팅 안전 |
| 주문 수량 | 예수금 × `cash-fraction` ÷ 현재가 후 `floor`. 디폴트 `cash-fraction=0.01` (1%) |
| 신호 dedupe 키 | `rcept_no` (= `DisclosureEntity` PK와 동일값) |
| 중복 신호 정책 | Phase B에서 같은 ticker 신호 N개면 confidence 최댓값 1건만 실행, 나머지는 `markSuperseded` |
| 에러 정책 | 건별 격리 + transient(`OpenAiClientException` / `KisException`) 1회 자동 재시도, phase-fatal(`DartIngestException`, `inquireBalance` 실패)만 phase 전체 중단 |

---

## 3. 아키텍처 — 모듈·책임 분할

새 패키지 `com.serveone.mama.pipeline`. 기존 모듈은 비파괴 변경(시그니처 1~2건) 외 그대로.

```
pipeline/
├── PipelineRunner            (@Service, 비즈니스 로직 1개)
│   ├── runSignalPhase()      ← 16:00 진입점
│   └── runExecutionPhase()   ← 09:05 진입점
├── PipelineScheduler         (@Component, @Scheduled, @Profile("!pipeline"))
│   ├── 16:00 KST cron → runner.runSignalPhase()
│   └── 09:05 KST cron → runner.runExecutionPhase()
├── PipelineCliRunner         (@Component, ApplicationRunner, @Profile("pipeline"))
│   └── --phase=signal|execute 인자로 분기 → runner.*() 1회 후 graceful exit
├── RetryHelper               (static util, no Spring dep)
└── PhaseResult records       (PhaseResult, SignalAttempt, ExecutionAttempt)
```

`PipelineRunner`만 비즈니스 로직을 갖는다. 두 트리거(Scheduler·Cli)는 얇은 어댑터 — 인자 파싱·로깅·종료 처리만.

### 기존 모듈 변경

| 위치 | 변경 | 이유 |
|---|---|---|
| `DisclosureIngestService.ingest()` | 반환 타입 `IngestResult` → `IngestPage(entities, totalPage)` | Phase A가 ingest 결과를 그대로 generate에 흘리려면 entities가 필요하고, 페이지네이션 루프 종료 조건에 `totalPage`가 필요. `IngestResult` record 제거 |
| `SignalGenerator.generate()` | 입력 타입 `DisclosureItem` → `DisclosureEntity` | ingest 결과를 직접 받음. 같은 5필드(`corpName`, `stockCode`, `reportNm`, `rceptDt`, `flrNm`)만 사용 |
| `KisClient.inquireQuote()` | `String` → `QuoteResponse(currentPrice: long)` | typed 파싱. 어제 raw 로깅으로 스모크 검증 끝 |
| `KisClient.inquireBalance()` | `String` → `BalanceResponse(deposit: long, holdings: List<Holding>)` | typed 파싱 |
| `OrderExecutor.MIN_CONFIDENCE` | 하드코딩 → `mama.executor.min-confidence` (디폴트 0.6) | 튜닝 가능. CHECKLIST 백로그 항목 흡수 |

### 설정 확장 (`MamaProperties` + `application.yml`)

```yaml
mama:
  watchlist:
    tickers: []                          # 6자리 종목코드 리스트. 비면 Phase A는 신호 0건
  executor:
    cash-fraction: 0.01                  # 예수금 사이징 (1%)
    min-confidence: 0.6                  # OrderExecutor 임계값
  pipeline:
    signal-phase-cron: "0 0 16 * * MON-FRI"
    execution-phase-cron: "0 5 9 * * MON-FRI"
    transient-retry-backoff-ms: 1000     # 테스트에서 0으로 오버라이드
```

`mama.kis.paper-trading=false`일 때 `PipelineScheduler`/`PipelineCliRunner`가 부팅 시 거부 — 절대 규칙 #3 강화.

---

## 4. Phase A — 16:00 신호 생성 데이터 흐름

```
@Scheduled 16:00 KST  ─┐
                       ├─► PipelineRunner.runSignalPhase()
CLI --phase=signal    ─┘
                              │
                              ▼
        1. today = LocalDate.now(clock, ZoneId.of("Asia/Seoul"))
        2. 자동 페이지네이션 ingest:
              pageNo = 1
              entities = []
              loop:
                  page = ingestService.ingest(today, today, pageNo, 100)  // IngestPage(entities, totalPage)
                  entities.addAll(page.entities())
                  if pageNo >= page.totalPage(): break
                  pageNo++
        3. candidates = entities.filter(e ->
              watchlist.tickers.contains(e.stockCode)
              && !signalRepo.existsById(e.rceptNo)
           )
        4. for entity in candidates (직렬):
              try {
                  signal = RetryHelper.withRetry(
                      () -> generator.generate(entity),
                      OpenAiClientException.class,
                      backoff
                  )
                  signalRepo.save(SignalEntity.success(entity.rceptNo, signal, now))
              } catch (Exception e) {
                  signalRepo.save(SignalEntity.failed(entity.rceptNo, entity.stockCode, e.getMessage(), now))
              }
        5. return PhaseResult(fetched, candidates, succeeded, failed)
```

**Dedupe**: `signalRepo.existsById(rceptNo)`로 자연 멱등성. 16:05 수동 재실행해도 LLM 콜 추가 0.

**페이지네이션**: `PipelineRunner` 책임. CLAUDE.md W1 결정 ("페이지네이션 자동 반복은 호출부 책임")과 일관. `DisclosureIngestService`는 single-page 유지. `totalPage` 활용을 위해 `DisclosureIngestService.ingest()`의 반환이 `(entities, totalPage)` 쌍이 되어야 함 — 시그니처를 `List<DisclosureEntity>` 단순화 대신 새 record `IngestPage(List<DisclosureEntity> entities, int totalPage)` 반환으로 조정.

**Phase-fatal vs per-signal**: DART fetch 실패(`DartIngestException`)는 phase 전체 throw. LLM 실패는 retry 후 `SignalEntity.failed` 저장하고 다음 candidate.

---

## 5. Phase B — 09:05 주문 실행 데이터 흐름

```
@Scheduled 09:05 KST  ─┐
                       ├─► PipelineRunner.runExecutionPhase()
CLI --phase=execute   ─┘
                              │
                              ▼
        1. pending = signalRepo.findExecutable(minConfidence)
        2. pending이 비면 PhaseResult.empty() 반환
        3. winners, losers = groupByTicker(pending)
              ticker별 confidence 최대 1건만 winner
              나머지는 markSuperseded(winnerRceptNo, now) 후 save
        4. balance = kis.inquireBalance()       ← 1회 호출
              cash = balance.deposit
              holdings = Map<ticker, qty>
        5. 각 winner에 대해 (직렬):
              quote = RetryHelper.withRetry(
                  () -> kis.inquireQuote(winner.ticker).currentPrice,
                  KisException.class,
                  backoff
              )
              targetQty = floor(cash * cashFraction / quote)
              if action=BUY && targetQty < 1:
                  winner.markFailed("qty=0 (cash=" + cash + " price=" + quote + ")", now)
                  continue
              if action=SELL:
                  held = holdings.getOrDefault(ticker, 0)
                  qty = min(targetQty, held)
                  if qty < 1:
                      winner.markFailed("no position", now)
                      continue
              else qty = targetQty
              try {
                  resp = RetryHelper.withRetry(
                      () -> action=BUY ? kis.placeMarketBuy(ticker, qty) : kis.placeMarketSell(ticker, qty),
                      KisException.class,
                      backoff
                  )
                  winner.markExecuted(resp.output.orderNo, qty, now)
              } catch (KisException e) {
                  winner.markFailed(e.getMessage(), now)
              }
              signalRepo.save(winner)
        6. return PhaseResult(pending, winners, executed, skipped, failed)
```

**잔고 1회 호출**: 직렬 처리 중 cash 재조회 안 함. 첫 winner가 cash를 다 써도 둘째의 qty 계산은 시작 시 cash로 진행 — 다음 phase에서 다시 정확해짐. N+1 회피 우선.

**휴장일/운영시간 외 주문**: KIS가 `EGW00202` 등으로 거절하면 `markFailed`로 영구화. 다음 09:05에 재시도 안 함 (`executed_at` 박힘). 사람이 수동 reset 가능.

**SELL no-position 가드**: `held=0`이면 skip "no position". 보유 없는데 매도 신호 와도 안전.

---

## 6. 영속 모델

### `SignalEntity` (테이블 `signal`)

| 컬럼 | 타입 | 제약 | 의미 |
|---|---|---|---|
| `rcept_no` | TEXT | **PK** | DART 공시 ID, `DisclosureEntity.rceptNo`와 동일값 (FK 안 박음 — SQLite/JPA 단순화) |
| `ticker` | TEXT(16) | NOT NULL | 6자리 종목코드, 조회 편의용 denormalize |
| `action` | TEXT(8) | NOT NULL | `BUY` / `SELL` / `HOLD` — HOLD도 측정 데이터로 저장 |
| `confidence` | REAL | NOT NULL | LLM 출력, 0~1 클램프됨 |
| `reasoning` | TEXT | nullable | LLM 한국어 근거 |
| `error_message` | TEXT | nullable | 생성 실패 / superseded / 실행 실패 사유 |
| `generated_at` | INTEGER | NOT NULL | `Instant` epoch — Phase A 종료 시각 |
| `executed_at` | INTEGER | nullable | Phase B가 이 신호를 "더 이상 안 집음" 시각 (성공·실패·supersede 공통) |
| `order_no` | TEXT(32) | nullable | KIS `output.ODNO`, 성공 시에만 |
| `executed_qty` | INTEGER | nullable | 실제 주문 수량, 성공 시에만 |

### 상태 전이

```
[generate 성공]   → executed_at=NULL, error=NULL              ← Phase B 후보
[generate 실패]   → executed_at=NULL, error="..."             ← Phase B 후보 아님 (error not null)
[markExecuted]    → executed_at=now, order_no, executed_qty
[markFailed]      → executed_at=now, error="..."
[markSuperseded]  → executed_at=now, error="superseded by ..."
```

**핵심 불변식**: `executed_at IS NULL AND error_message IS NULL` 인 row만 Phase B가 다시 집는다. 사람이 row를 수동 reset(`UPDATE signal SET executed_at=NULL, error_message=NULL WHERE rcept_no=...`)하면 다음 phase가 재시도.

### `SignalRepository`

```java
public interface SignalRepository extends JpaRepository<SignalEntity, String> {
    @Query("""
        SELECT s FROM SignalEntity s
        WHERE s.action IN (com.serveone.mama.signal.Action.BUY,
                           com.serveone.mama.signal.Action.SELL)
          AND s.confidence >= :minConfidence
          AND s.executedAt IS NULL
          AND s.errorMessage IS NULL
        """)
    List<SignalEntity> findExecutable(double minConfidence);
}
```

`existsById(rceptNo)`는 `JpaRepository` 기본 제공 — Phase A dedupe에 사용.

### 팩토리 (정적·인스턴스 메서드)

```java
static SignalEntity success(String rceptNo, Signal signal, Instant now)
static SignalEntity failed(String rceptNo, String ticker, String errorMessage, Instant now)

void markExecuted(String orderNo, int executedQty, Instant now)
void markFailed(String errorMessage, Instant now)
void markSuperseded(String winnerRceptNo, Instant now)
```

세터를 외부 노출하지 않고 의미 단위로만. `setExecutedAt` 같은 raw setter 없음.

### 스키마 적용

`hibernate.ddl-auto=update` 유지 → `signal` 테이블 자동 생성, `disclosure` 변동 없음. Flyway 도입은 CHECKLIST 백로그로.

---

## 7. 에러 처리 (요약 표)

| 에러 지점 | 분류 | 동작 |
|---|---|---|
| Phase A DART fetch (`DartIngestException`) | phase-fatal | phase 전체 중단, ERROR 로그, 상위 throw |
| Phase A LLM generate (`OpenAiClientException`) | per-signal | retry 1회 → 실패 시 `SignalEntity.failed` 저장 + 다음 candidate |
| Phase B `inquireBalance` 실패 | phase-fatal | phase 전체 중단 — 잔고 모르면 qty 계산 불가 |
| Phase B `inquireQuote` 실패 (per ticker) | per-signal | retry 1회 → `markFailed("quote: ...")` + 다음 winner |
| Phase B 주문 실패 (`KisException`) | per-signal | retry 1회 → `markFailed(rt_cd+msg_cd+msg)` + 다음 winner |
| Phase B SELL no-position / BUY qty=0 | normal skip | `markFailed("no position")` / `markFailed("qty=0")` |

### `RetryHelper`

```java
public final class RetryHelper {
    private static final Logger log = ...;
    private RetryHelper() {}

    public static <T> T withRetry(Supplier<T> call, Class<? extends Exception> retryable, Duration backoff) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            if (!retryable.isInstance(e)) throw e;
            log.warn("retrying after {}ms: {}", backoff.toMillis(), e.getMessage());
            try { Thread.sleep(backoff.toMillis()); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new RuntimeException(ie); }
            return call.get();   // 2번째 실패는 그대로 throw
        }
    }
}
```

테스트에서 `backoff=Duration.ZERO`로 결정성.

---

## 8. 테스트 전략 (W4 추가 ~17개)

| 파일 | 케이스 |
|---|---|
| `PipelineRunnerTests` (Phase A) | (1) watchlist 빈 리스트 → 신호 0건 |
| | (2) candidates 5건 중 2건 이미 신호 있음 → skip |
| | (3) generate 1건 retry 후 성공 |
| | (4) generate 1건 retry도 실패 → `SignalEntity.failed` |
| | (5) DART `totalPage=3` → ingest 3회 호출 |
| | (6) DART fetch throw → phase 전체 중단 |
| `PipelineRunnerTests` (Phase B) | (7) pending 0 → no-op 종료 |
| | (8) 같은 ticker 3건 → 최대 conf만 winner, 2건 `markSuperseded` |
| | (9) BUY targetQty=0 → `markFailed("qty=0")` |
| | (10) SELL no-position → `markFailed("no position")` |
| | (11) 정상 BUY → `placeMarketBuy` 호출 + `markExecuted` |
| | (12) KisException → retry 후 `markFailed` |
| | (13) `inquireBalance` throw → phase 전체 중단 |
| `SignalEntityTests` | (14) `success`/`failed` 매핑, `markExecuted`/`markFailed`/`markSuperseded` 상태 전이 |
| `SignalRepositoryTests` (`@DataJpaTest`) | (15) `findExecutable`의 4조건 필터 (action/conf/executed_at/error) |
| `KisClientTests` | (16) `inquireQuote` raw → `QuoteResponse(currentPrice)` 추출 (기존 String 테스트 대체) |
| | (17) `inquireBalance` raw → `BalanceResponse(deposit, holdings)` 추출 (기존 String 테스트 대체) |

빌드 검증: `cd backend && ./gradlew build` 통과, 전체 ~51 테스트 그린, `bootRun` (default 프로파일)으로 `signal` DDL 생성 로그 확인.

---

## 9. 실API 스모크는 W4 스코프 밖

코드가 다 그린되면 **다음 평일 16:00 KST**에 첫 Phase A가 자동 실행 — 그게 자연스러운 LLM 실호출 스모크 (CHECKLIST의 "Anthropic 신호 생성 1회" 항목 자연 해소). 익일 09:05에 첫 주문 — KIS 운영시간 안이므로 어제 EGW00202 함정 회피.

별도 `Pipeline*SmokeRunner`는 만들지 않는다. 어제 `KisSmokeRunner`(@Profile("smoke"))가 인증·시세·잔고를 검증하는 용도로 이미 존재. W4는 그 위에서 정상 동작이 기대됨.

---

## 10. 변경 영향 (요약)

신설:
- `pipeline/PipelineRunner`, `pipeline/PipelineScheduler`, `pipeline/PipelineCliRunner`, `pipeline/RetryHelper`, `pipeline/PhaseResult`
- `signal/entity/SignalEntity`, `signal/SignalRepository`
- `kis/QuoteResponse`, `kis/BalanceResponse` (+ inner `Holding`)
- `config/MamaProperties.Watchlist`, `MamaProperties.Pipeline`, `MamaProperties.Executor` 확장

수정:
- `DisclosureIngestService.ingest()` 반환 타입 변경 (`IngestResult` → `IngestPage`)
- `SignalGenerator.generate()` 파라미터 타입 변경 (`DisclosureItem` → `DisclosureEntity`)
- `KisClient.inquireQuote/inquireBalance` 반환 타입 변경
- `OrderExecutor.MIN_CONFIDENCE` 하드코딩 제거, 프로퍼티 주입
- `application.yml` (본/테스트) 신규 설정 키 추가
- 기존 테스트 일괄 갱신 (이전 시그니처 사용 케이스)

삭제:
- `DisclosureItem`을 generator 입력으로 쓰던 케이스 (테스트만 영향)
- `IngestResult` record

영향 없음: `DartClient`, `KisTokenManager`, `OpenAiClient`, `DisclosureEntity`, 기존 `DisclosureRepository`, `KisSmokeRunner`.
