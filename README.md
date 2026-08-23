# replyfit-backend

리플핏(ReplyFit) 백엔드 — 의류·잡화 온라인 셀러를 위한 문의 응대 자동화 B2B SaaS.

**Java 21 · Spring Boot 3.5 · JPA · PostgreSQL · Redis · Kafka · OpenAI API**

## 역할

| 프로필 | 역할 |
|---|---|
| `api` | REST API 서버 — 인증, 정책 관리, CSV 업로드(PII 마스킹), 초안 승인 흐름, 대시보드, 구독 |
| `worker` | Kafka 컨슈머 — 문의 유형 자동분류 → 정책 기반 답변 초안 생성 → 정책 검증, 주간 VOC 리포트, 보존기간 삭제 배치 |

같은 이미지를 `SPRING_PROFILES_ACTIVE`만 바꿔 두 역할로 배포한다.

## 처리 파이프라인

```
CSV 업로드 ─▶ PII 마스킹 ─▶ 저장 ─▶ Kafka(replyfit.inquiries.uploaded)
                                        │
     worker ◀───────────────────────────┘
       ├─ ① 유형 자동분류 (OpenAI / rule-based 폴백)
       ├─ ② 카테고리 관련 정책 로드
       ├─ ③ 정책 문구만 인용한 답변 초안 생성
       ├─ ④ PolicyGuard — 출처 없는 수치 감지 시 '검토 필요' 전환
       └─ ⑤ 저장 + Redis 진행률 갱신 + 대시보드 캐시 무효화
```

- 컨슈머 실패 시 지수 백오프 3회 재시도 후 Dead Letter Topic(`*.DLT`)으로 이동
- `OPENAI_API_KEY` 미설정 시 rule-based 폴백 엔진으로 전체 파이프라인 동작 (개발·데모용)

## 실행

```bash
# 인프라 (replyfit-infra 저장소의 docker-compose 사용)
docker compose up -d postgres redis kafka

# API 서버
SPRING_PROFILES_ACTIVE=api ./gradlew bootRun

# 워커
SPRING_PROFILES_ACTIVE=worker SERVER_PORT=8081 ./gradlew bootRun
```

Swagger UI: http://localhost:8080/swagger-ui.html

## 주요 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | localhost/5432/replyfit/replyfit/replyfit | PostgreSQL |
| `REDIS_HOST` / `REDIS_PORT` | localhost/6379 | Redis |
| `KAFKA_BOOTSTRAP_SERVERS` | localhost:29092 | Kafka |
| `JWT_SECRET` | (dev 기본값) | 운영에서 반드시 교체 |
| `OPENAI_API_KEY` | (없음) | 미설정 시 rule-based 폴백 |
| `REPLYFIT_AI_MODEL` | gpt-5-mini | OpenAI 모델 |
| `REPLYFIT_SEED_DEMO` | true | 데모 계정·샘플 데이터 시드 |
| `REPLYFIT_RETENTION_DAYS` | 180 | 개인정보 보관기간(일) |

## 패키지 구조

```
co.replyfit
├── auth/        JWT 인증 · Redis 리프레시 토큰(회전) · 로그인 레이트리밋
├── user/        회원
├── policy/      스토어 정책 (AI 초안 인용 원천)
├── upload/      CSV 파싱 · PII 마스킹 · 작업 진행률(Redis)
├── inquiry/     문의 도메인 · 조회 API
├── draft/       답변 초안 · 승인/발송 흐름
├── review/      리뷰 도메인 · 조회 API
├── ai/          LlmClient(OpenAI/rule-based) · 프롬프트 템플릿 · PolicyGuard
├── kafka/       토픽 · 이벤트 · 프로듀서 · DLT 에러 핸들러
├── worker/      Kafka 컨슈머 (AI 파이프라인 · 리포트 · 보존기간 삭제)
├── report/      주간 VOC 리포트 집계·생성
├── dashboard/   통계 API · Redis 캐시
├── billing/     요금제 · 구독 (토스페이먼츠 스텁, '26.12 정기결제 연동 예정)
└── common/      예외 처리 · PII 마스킹 유틸
```

## 보안·컴플라이언스

- 이름·주문번호·연락처는 업로드 즉시 마스킹, 원본 미저장
- 보관기간 제한(기본 180일) — 매일 04:30 자동 삭제 배치
- 전자상거래법 법정 안내와 판매자 정책 분리
- AI는 초안·추천 도구 — 직접 발송하지 않음 (셀러 승인 구조)
