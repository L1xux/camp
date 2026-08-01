# Backlog 초안 (검토용 — GitHub 등록 전)

MVP 범위(docs/PLAN.md)를 실행 단위로 쪼갠 이슈 초안 17건.
UiJin 검토 후 GitHub Issues로 등록한다. 등록 전까지 커밋하지 않는다.

## 작성 기준

- 처음 보는 사람이 이 문서만으로 이해 가능하게 (내부 문서 절 번호 인용 금지)
- 요구사항은 API 시그니처, 수치, 표로 구체화
- 예외 케이스와 테스트 케이스는 실무 수준으로 (경계값, 동시성, 장애 주입 포함)
- 이슈 간 의존은 머리말의 "선행" 항목으로 명시

## 목록

| # | 파일 | 제목 | 마일스톤 | 비고 |
|---|---|---|---|---|
| 01 | 01-build-skeleton.md | Gradle 멀티모듈 골격 — 헥사고날 아키텍처, 의존성 규칙 강제 | D1 | |
| 02 | 02-local-env.md | 로컬 실행 환경 — docker-compose(Oracle 23ai), DB 마이그레이션 체계 | D1 | ADR-002 유발 |
| 03 | 03-repo-gates.md | 레포 운영 규칙 — 브랜치 보호, 템플릿, pre-commit, CI | D1 | |
| 04 | 04-observability.md | 관측 기반 — 구조화 로깅, 에러 트래킹, LLM 트레이싱, 외부 호출 정책 | D1 | ADR-003 유발 |
| 05 | 05-domain-model.md | 도메인 모델 — 캠페인 상태머신, 결재 규칙, 예산 불변식 | D1 | |
| 06 | 06-influencer-pool.md | 인플루언서 풀 — 등록, 검색, 프로필, 채널 1:N | D2 | |
| 07 | 07-campaign.md | 캠페인 — CRUD, 상태 전이 API, 후보 관리, 브랜드 데이터 격리 | D2 | 격리 컴포넌트가 13의 토대 |
| 08 | 08-contract-approval.md | 계약과 결재 워크플로우 — 승인/반려/재상신, 예산 동시성 제어 | D2 | |
| 09 | 09-react-ops-ui.md | React 운영 화면 — 목록/상세, 결재함, 역할 전환 | D2 | |
| 10 | 10-metrics-batch.md | 성과 수집 배치 — 수집 포트, 시뮬레이터, 일별 스냅샷 적재 | D3 | |
| 11 | 11-youtube-adapter.md | YouTube Data API 수집 어댑터 | D3 | 축소 2순위 |
| 12 | 12-seeding-tuning.md | 대용량 시드 + 조회 튜닝 (파티셔닝, 인덱스, 실행계획) | D3 | 축소 1순위(심화분) |
| 13 | 13-mcp-validator.md | MCP 도구 서버 + SQL Validator | D4 | 핵심 기능 |
| 14 | 14-hybrid-rag.md | 하이브리드 문서 검색 (RAG) — 벡터 + 키워드, 브랜드 필터 선적용 | D4 | 축소 3순위(코퍼스) |
| 15 | 15-ai-dashboard.md | 대화형 AI 대시보드 — SSE 스트리밍, 동적 차트 | D4 | 핵심 기능 |
| 16 | 16-eval-harness.md | 골든셋 평가 하네스 — LLM 품질 회귀 게이트를 CI에 | D5 | |
| 17 | 17-demo-docs.md | 데모 리허설 + README, RUNBOOK, 운영 문서 정비 | D5 | |

시간 부족 시 축소 순서: 12(튜닝 심화) → 11(YouTube 실API) → 14(문서 코퍼스 축소).
13(Validator)과 15(스트리밍 대시보드)는 이 프로젝트의 핵심 기능이므로 축소 대상이 아니다.

## 등록 시 제안

- 마일스톤: D1(08/01) ~ D5(08/05), 각 이슈를 해당 마일스톤에 배정
- 라벨: `mvp`(기능), `setup`(01~04), `core`(13, 15), `cuttable`(11, 12, 14)
