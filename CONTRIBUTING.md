# 리플핏 협업 가이드

팀 리플핏의 브랜치 전략·커밋 컨벤션·PR 규칙입니다. `replyfit-backend` / `replyfit-frontend` / `replyfit-infra` 세 저장소에 공통 적용됩니다.

## 브랜치 전략

git-flow를 팀 규모(3인)에 맞게 간소화한 구조를 사용합니다.

```
main ────●─────────────────●──▶  배포/릴리즈 (태그 vX.Y.Z)
          \               / 릴리즈 PR
develop ───●──●──●──●──●─▶      통합 브랜치 (기본 작업 대상)
            \    /
feat/○○○ ────●──▶                기능 단위 작업 브랜치
```

| 브랜치 | 용도 | 분기 원점 | 머지 대상 |
|---|---|---|---|
| `main` | 배포 가능한 릴리즈 상태. 직접 커밋 금지 | — | — |
| `develop` | 기능 통합. 모든 작업 PR의 기본 대상 | `main` | `main` (릴리즈 PR) |
| `feat/*` | 새 기능 | `develop` | `develop` |
| `fix/*` | 버그 수정 | `develop` | `develop` |
| `chore/*` | 빌드·설정·의존성 | `develop` | `develop` |
| `docs/*` | 문서 | `develop` | `develop` |
| `hotfix/*` | 운영 긴급 수정 | `main` | `main` + `develop` 양쪽 |

- 브랜치 이름은 `타입/케밥케이스` (예: `feat/voc-report`, `fix/search-null-param`)
- 마일스톤 단위로 `develop` → `main` 릴리즈 PR을 올리고, 머지 후 `vX.Y.Z` 태그와 릴리즈 노트를 남깁니다.
- 머지 방식은 **merge commit** — 기능 브랜치의 세부 커밋을 히스토리에 보존합니다.

> 이력 참고: 프로젝트 부트스트랩 단계(스캐폴드 ~ MVP 코어 기능)는 속도를 위해
> `main` 직행 트렁크 방식으로 진행했고, MVP 기능 완성 시점부터 본 전략(develop 통합)을 적용합니다.

## 커밋 컨벤션 (Conventional Commits)

```
<타입>(<스코프>): <제목 — 한국어, 명령형>

- 본문은 불릿으로 '무엇을·왜' 중심 서술 (선택)

Closes #<이슈번호>   ← 이슈를 닫는 마지막 커밋에
```

| 타입 | 용도 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 구조 개선 |
| `chore` | 빌드·설정·의존성 |
| `docs` | 문서 |
| `test` | 테스트 |
| `perf` | 성능 |

예시:

```
feat(ai): OpenAI API 연동 (공식 openai-java SDK)

- chat completions 기반 분류/초안/리포트 인사이트
- API 오류 시 rule-based로 자동 폴백 (파이프라인 무중단)

Closes #6
```

## 이슈 → PR 흐름

1. **이슈 생성** — 배경/작업 내용 체크리스트, 라벨(`feat`·`bug`·`infra`·`ai`·`documentation`)과 마일스톤 지정
2. `develop`에서 브랜치 분기 (`feat/...`)
3. 기능 단위로 잘게 커밋 (컨벤션 준수)
4. **PR 생성** — 대상 `develop`, 템플릿(개요/변경 사항/테스트) 작성, `Closes #N` 연결
5. **리뷰** — 최소 1인 코멘트 리뷰 후 머지 (셀프 머지는 리뷰 코멘트 남긴 뒤)
6. 릴리즈 시점에 `develop` → `main` PR + 태그

## PR 규칙

- 하나의 PR은 하나의 이슈(기능)만 다룹니다 — 리뷰 가능한 크기 유지
- PR 본문에 설계 결정·트레이드오프를 남깁니다 (나중에 "왜 이렇게 했지"의 답)
- 모든 PR에 CI(GitHub Actions)가 자동으로 빌드·테스트를 수행합니다 — **CI 통과 후 머지**
- 푸시 전 로컬 확인 권장: `./gradlew test` (백엔드) / `npm run test && npm run build` (프론트)
- `v*` 태그를 푸시하면 CI가 GHCR에 Docker 이미지를 자동 발행합니다
