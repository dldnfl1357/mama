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

### 모노레포 → 단일 리포

처음엔 모노레포로 시도:
1. 모든 backend 파일 `backend/`로 이동 → CLAUDE.md 아키텍처·빌드 섹션 갱신.
2. 사용자가 별도로 `frontend/` (React + Vite) 생성·커밋.

이후 사용자 결정: **"backend와 frontend는 별개 프로젝트"**.

→ 구조 재정리:
- `backend/`에 `git init` (자체 워크스페이스).
- `origin = https://github.com/dldnfl1357/mama.git`.
- **Force-push로 이전 히스토리 폐기** (`e2b84b0`, `26d0781` → 새 root commit `c017589`).
- 부모 `/Users/woori/projects/mama/.git` 제거.
- `frontend/`는 별도 리포(`mama-site` 등)로 분리 예정 — 현재 ungoverned.

**결과 레이아웃:**

```
/Users/woori/projects/mama/   ← 그냥 폴더
├── backend/                   ← git workspace (origin: mama.git)
└── frontend/                  ← 별도 프로젝트 (git 없음)
```

### CLAUDE.md 갱신

- §4 데이터 영속성 (Persistence) 섹션 신규 추가 — 스택, 위치, 스키마 관리, 클라우드 마이그레이션 경로, 컨벤션.
- 기존 §4·§5 → §5·§6로 이동.

### 보류 / 미해결

- **`frontend/` 분리**: 별도 리포(`mama-site` 등) 발행 + 초기 커밋 필요.
- **GitHub PAT 폐기**: 사용자 미확인. 채팅에 노출된 두 토큰 즉시 revoke 필요.
- **첫 영속 엔티티**: `DisclosureEntity` + `DisclosureRepository`. W1 닫고 W2 시작 전에 처리할지, 끼워서 갈지 결정 필요.
- **JAVA_HOME 영구화**: `~/.zshrc` 추가 미수행. 매 세션 export 필요.

### 다음 후보

1. **W2 시작: LLM 분석 레이어** — `llm/` 패키지 + Anthropic Java SDK + 프롬프트.
2. **W1 마무리** — `DisclosureEntity` + `DisclosureRepository`로 공시 영속화.
3. **`frontend/` 분리** — 별도 리포 정리.

---

## 일지 작성 규칙 (셀프)

- 한 작업 세션 끝나면 날짜 섹션 추가.
- 코드 변경 자체보단 **결정·근거·함정·보류 항목**을 남긴다.
- 보류된 항목은 다음 세션 시작 시 우선 확인.
- 토큰·키 같은 시크릿은 절대 적지 않는다. 사고 기록은 사건 내용만, 값은 절대 금지.
