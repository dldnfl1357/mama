# CLAUDE.md

이 파일은 Claude Code(또는 모든 AI 코딩 도구)가 `mama` 프로젝트에서 작업할 때 반드시 따라야 하는 규칙·구조·컨벤션을 정의한다.

---

## 1. 절대 규칙 (Absolute Rules)

**이 규칙들은 어떤 이유로도 무시·우회할 수 없다. 위반이 필요해 보이면 작업을 중단하고 사용자에게 확인을 요청한다.**

1. **프로덕션 DB 직접 쿼리 금지.** `psql`/`mysql`/JDBC URL을 통한 실 DB 접근은 사용자가 명시 승인한 경우에만 허용. 마이그레이션·디버깅 목적이라도 먼저 묻는다.
2. **시크릿 파일 커밋 금지.** `.env`, `application-local.yml`, `*.pem`, `*.p12`, API 키·토큰이 들어간 어떤 파일도 절대 `git add` 하지 않는다. `.gitignore`에 누락된 시크릿 파일을 발견하면 즉시 알린다.
3. **실계좌(`KIS_PAPER_TRADING=false`) 호출 금지.** 기본값은 모의투자. 실계좌 모드로 전환하는 코드·설정 변경은 사용자가 명시적으로 요청한 경우에만 적용한다.

---

## 2. 아키텍처

`mama` backend는 **독립 Spring Boot 프로젝트**다. 이 워크스페이스의 범위는 `backend/` 한 폴더로 한정되며, 다른 영역(웹/모바일/인프라 등)은 별도 리포에서 관리된다. **이 폴더에서 작업할 때는 다른 영역을 고려하지 않는다.**

```
backend/                           ← 이 리포 루트 (.git 위치)
├── build.gradle.kts               # Gradle Kotlin DSL, Spring Boot 3.5, Java 21
├── settings.gradle.kts
├── gradlew, gradlew.bat
├── gradle/wrapper/
├── .gitattributes
├── .env.example                   # 환경 변수 템플릿 (KIS / DART / Anthropic)
├── .env                           # 실제 시크릿 — gitignored, 절대 커밋 금지
├── CLAUDE.md                      ← 이 파일
└── src/
    ├── main/
    │   ├── java/com/serveone/mama/
    │   │   ├── MamaApplication.java        # 진입점, @ConfigurationPropertiesScan
    │   │   ├── config/                     # @ConfigurationProperties record들
    │   │   │   └── MamaProperties.java     # KIS / DART / Anthropic 설정 묶음
    │   │   ├── dart/                       # DART 공시 OpenAPI 클라이언트 (W1)
    │   │   ├── kis/                        # KIS Developers API 클라이언트 (W3)
    │   │   ├── llm/                        # Claude 호출 + 프롬프트 (W2)
    │   │   ├── signal/                     # 공시 → 매매 신호 변환 (W2)
    │   │   ├── executor/                   # 신호 → 주문 실행 (W3)
    │   │   └── scheduler/                  # 배치/스케줄러 (W4)
    │   └── resources/
    │       └── application.yml             # 설정 구조 + .env 자동 import
    └── test/
        ├── java/com/serveone/mama/
        │   └── MamaApplicationTests.java
        └── resources/
            └── application.yml             # 테스트용 더미 시크릿 (실제 호출 안 함)
```

**모듈 책임 원칙:**
- 모든 Spring/Gradle 작업은 `backend/` 안에서 수행한다. `./gradlew` 호출 시 working dir는 `backend/`.
- `dart/`·`kis/`·`llm/`은 외부 시스템 어댑터. 도메인 로직은 들어가지 않는다.
- `signal/`은 순수 도메인. 외부 호출 없음. 입력(공시) → 출력(신호)만 책임.
- `executor/`는 신호를 받아 KIS 어댑터로 위임. 자체 비즈니스 결정 없음.
- 새 모듈을 추가하기 전에 위 분류 안에 들어가는지 먼저 확인.

---

## 3. 빌드 & 테스트

### 작업 디렉터리

**모든 gradle 명령은 `backend/`에서 실행한다.**

```bash
cd /Users/woori/projects/mama/backend
```

### 환경 변수 (필수)

시스템 Java가 25이고 Gradle 8.14.5는 24까지만 지원하므로 **JDK 21을 명시 지정**해야 한다.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
```

위 두 줄을 `~/.zshrc`에 영구화하거나, 매 세션 시작 시 export 해야 `./gradlew` 가 동작한다.

### 명령어 (모두 `backend/`에서 실행)

| 명령 | 용도 |
|---|---|
| `./gradlew build` | 컴파일 + 테스트 + 패키징 (가장 자주 쓰는 풀 검증) |
| `./gradlew test` | 테스트만 실행 |
| `./gradlew bootRun` | 앱 실행 (개발 중 로컬 구동) |
| `./gradlew check` | 정적 분석 + 테스트 (커밋 전 권장) |
| `./gradlew clean build` | 캐시 무시하고 처음부터 빌드 |
| `./gradlew dependencies` | 의존성 트리 확인 |

`application.yml`의 `spring.config.import: optional:file:./.env[.properties]`는 working dir 기준이므로, **`backend/`에서 실행해야 `backend/.env`가 로드**된다.

### 작업 완료 기준

코드 변경 후 다음을 모두 통과한 것을 확인하고 보고한다:
1. `cd backend && ./gradlew build` 성공
2. 새 기능에 대한 테스트 추가 (단위 테스트는 외부 호출 모킹)
3. `application.yml` 변경 시 `backend/src/test/resources/application.yml`도 동기화

---

## 4. 데이터 영속성 (Persistence)

### 스택

- **DB**: SQLite (로컬 파일 기반)
- **드라이버**: `org.xerial:sqlite-jdbc`
- **ORM**: Spring Data JPA + Hibernate
- **Dialect**: `org.hibernate.community.dialect.SQLiteDialect` (community 모듈 — Hibernate가 SQLite를 공식 지원하지 않아 community-dialects 의존성 필요)
- **연결 풀**: HikariCP (Spring Boot 기본)

### 위치 & 생명주기

| 환경 | URL | 파일 위치 | 생명주기 |
|---|---|---|---|
| 로컬 실행 | `jdbc:sqlite:./data/mama.db` | `backend/data/mama.db` | 영구 (gitignored) |
| 테스트 | `jdbc:sqlite::memory:` | (메모리) | 컨텍스트마다 새로 생성·종료 |

`./data/` 디렉터리는 `.gitignore`에 포함됨. 절대 커밋 금지.

### 스키마 관리

- 현재 **`hibernate.ddl-auto=update`** — 엔티티 정의로부터 스키마 자동 생성/변경 (개인 MVP 단계 한정)
- 추후 스키마가 안정화되면 **Flyway 마이그레이션으로 전환**한다. 그 시점에 `ddl-auto=validate`로 바꾸고 `db/migration/V*__*.sql` 파일들로 관리.
- 엔티티 추가/변경 시 의도하지 않은 컬럼·인덱스 변경이 일어날 수 있으니, `hibernate.show_sql=true`로 SQL을 한 번 확인 후 커밋한다.

### 선택 근거

- **왜 SQLite인가**: 단일 사용자, 단일 프로세스 워크로드. 외부 DB 운영 비용·복잡도 0. 1년치 공시/신호/주문 데이터도 수백MB 미만으로 충분.
- **언제 마이그레이션**: (a) 다중 프로세스 동시 쓰기가 필요할 때, (b) 클라우드 대시보드 등 원격 접근이 필요할 때, (c) 데이터가 GB 단위로 커질 때. 그 전엔 SQLite 유지.
- **이전 후보**: Neon, Supabase, CockroachDB. 클라우드로 옮긴다면 Postgres 호환이므로 Hibernate dialect만 갈아끼우면 됨.

### 영속성 컨벤션

- 엔티티는 `com.serveone.mama.<module>.entity` 패키지에 둔다 (예: `dart.entity.DisclosureEntity`).
- 외부 시스템 응답 record(DTO)와 영속 엔티티는 **분리**한다. 변환은 `<module>/mapper` 또는 명시적 of-메서드로.
- Repository는 `Spring Data JPA`의 `JpaRepository` 또는 더 간단하면 `CrudRepository` 사용.
- 트랜잭션 경계는 `@Service` 메서드에서 `@Transactional`로 명시. Repository 직접 호출에서는 트랜잭션 만들지 않는다.
- 절대 규칙 #1("프로덕션 DB 직접 쿼리 금지")은 현재 로컬 SQLite엔 사실상 적용 안 되지만, **클라우드 전환 후엔 즉시 발효**한다.

---

## 5. 도메인 컨텍스트

### What

`mama`는 **개인용 한국 주식 자동매매 서버**다. 매일 공시(DART)를 수집해 LLM(Claude)으로 분석하고, 매매 신호를 생성해 KIS API로 주문을 낸다.

```
DART 공시 ─► Claude 분석 ─► 매매 신호 ─► KIS 주문 ─► 측정/회고
  (W1)         (W2)            (W2)         (W3)        (W4)
```

### Why this exists

사용자가 "엔지니어 → 빌더"로 전환하는 첫 프로젝트. **수익 극대화가 아니라 "MVP 출시 → 측정 → 개선" 사이클을 한 바퀴 도는 것**이 목표. 기능을 늘리기보다 루프 완주를 우선한다.

### 외부 시스템

| 시스템 | 용도 | 비고 |
|---|---|---|
| **KIS Developers** (한국투자증권) | 시세 조회, 주문 실행 | OAuth 2.0. 기본 모의투자 (`paperTrading=true`). 실계좌 전환은 절대 규칙 #3 참조. |
| **DART OpenAPI** (금감원) | 공시 데이터 | 공식·무료. 뉴스 스크래핑 대신 이것만 사용 (TOS 안전). |
| **Anthropic Claude** | 공시 → 신호 변환 | 기본 모델 `claude-haiku-4-5-20251001` (속도/비용 균형). |

### 사용자·규제 컨텍스트

- 사용자 = 본인 1명. 타인에게 신호·서비스 제공 없음 → 투자자문업 라이선스 무관.
- 만약 향후 "타인에게 제공" 요구가 생기면 **반드시 사용자에게 규제 검토를 먼저 알리고 코드 변경 전에 합의**한다.

### 도메인 함정 (코드를 짤 때 항상 의식할 것)

1. **LLM 신호는 백테스팅 불가** — 학습 데이터에 미래 정보가 누출됨. 백테스팅 프레임워크를 짜자는 제안은 거부. **forward test(모의투자 실시간)만 유효**.
2. **뉴스→가격 반영은 초 단위** — 데이트레이딩 전제는 슬리피지로 실패. 기본 전략은 **스윙(1-5일 보유)**.
3. **공시 발표 시각 ≠ 시장 반영 시각** — 장 마감 후 공시는 다음날 시가에 반영. 신호 생성 로직은 이 시간차를 고려해야 함.
4. **모의투자 환경의 호가/체결은 실제와 다름** — 모의에서 잘 되더라도 실계좌 첫 가동 전 별도 검증 필요.

---

## 6. 코딩 컨벤션

표준 Java + Spring Boot 관행을 따른다. 특수한 규칙을 만들지 않는다.

### 네이밍

| 대상 | 규칙 | 예 |
|---|---|---|
| 클래스 | PascalCase | `DartClient`, `SignalGenerator` |
| 메서드·필드·로컬 변수 | camelCase | `fetchDisclosures`, `appKey` |
| 상수 (static final) | UPPER_SNAKE_CASE | `MAX_RETRIES`, `DEFAULT_PAGE_SIZE` |
| 패키지 | 소문자, dot-separated | `com.serveone.mama.dart` |
| YAML 키 | kebab-case | `app-key`, `paper-trading` |
| 환경 변수 | UPPER_SNAKE_CASE | `KIS_APP_KEY`, `DART_API_KEY` |
| 테스트 클래스 | `<클래스명>Tests` | `DartClientTests` |
| 테스트 메서드 | camelCase, 상황 설명형 | `returnsEmptyListWhenNoDisclosures` |

### 컴포넌트 패턴

- **DTO·설정·외부 응답은 `record` 우선.** mutable 가변 객체는 정당한 이유가 있을 때만.
- **외부 설정은 `@ConfigurationProperties` record로 묶는다.** `@Value` 분산 금지.
- **HTTP 클라이언트는 Spring Boot 3 `RestClient`.** 신규 코드에서 `RestTemplate`/`WebClient`(블로킹 용도) 사용 금지.
- **Spring 스테레오타입 명확히 분리:** `@RestController` / `@Service` / `@Repository` / `@Component`. 한 클래스가 두 역할 겸하지 않도록.
- **생성자 주입.** `@Autowired` 필드 주입 금지. record나 final 필드 + 단일 생성자 사용.
- **Lombok은 보조 도구.** `@Slf4j` 정도만 권장. `@Data`/`@Builder`로 도메인 객체를 만들지 말고 record로.
- **체크 예외 던지지 말 것.** RuntimeException(또는 그 하위)만 throw.
- **로깅:** SLF4J. `log.info(...)` 형태. `System.out` 금지. 프로덕션 로그는 PII/시크릿 마스킹.

### 테스트

- **JUnit 5 + AssertJ + Mockito** (Spring Boot starter-test 기본).
- 단위 테스트는 외부 호출(KIS, DART, Claude) **반드시 모킹**.
- 통합 테스트가 필요하면 별도 `@SpringBootTest` 클래스로 분리하고 파일명에 `IntegrationTests` 접미사.
- 새 기능에는 최소 1개의 테스트 동반.

### 커밋 메시지 (Conventional Commits)

```
<type>(<scope>): <subject>

[optional body]
```

- `type`: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `build`, `perf`
- `scope`: 모듈명 또는 영역 (`dart`, `kis`, `llm`, `signal`, `executor`, `config`, `deps`)
- `subject`: 50자 이내, 명령형, 마침표 없음, 한국어 가능
- `body`: 필요할 때만, 변경 이유(Why)를 적는다. 무엇을 했는지는 diff가 말해준다.

**예:**
```
feat(dart): add disclosure fetch client
fix(kis): handle access token expiry with retry
refactor(signal): extract score normalization into pure function
chore(deps): bump spring-boot to 3.5.1
```

### 주석

- 기본은 **주석을 쓰지 않는다.** 잘 지은 이름·작은 함수가 우선.
- 다음 경우에만 주석을 단다:
  - 외부 시스템의 비명시적 제약 (예: "KIS는 동일 토큰으로 1초 1회 호출 제한")
  - 직관과 다른 비즈니스 결정의 이유 (예: "장 마감 후 공시는 익일 처리")
  - 우회 코드의 근거 (예: "DART 응답이 한국시간 기준이라 명시 변환 필요")
- "이 함수는 X를 한다" 같은 What 주석 금지.
