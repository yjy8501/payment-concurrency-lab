<!--
Sync Impact Report
- Version change: 1.0.0 → 1.0.1
- Modified principles: 없음
- Modified sections: 개발 흐름 (clarify를 필요할 때만 거치는 선택 단계로 명시)
- Added sections: 없음
- Removed sections: 없음
- Follow-up TODOs: 없음
-->

# Payment Concurrency Lab Constitution

## Core Principles

### I. 실제 DB로 경쟁시킨다 (Real Contention, No Mocks)

- 동시성 검증 테스트는 MUST 실제 데이터베이스·캐시 컨테이너를 띄워서 실행한다.
- 저장소·락을 mock으로 대체한 테스트는 동시성 결과의 근거로 쓰지 않는다.
- 이유: 이중 처리는 DB 격리 수준·락·제약 조건이 실제로 부딪칠 때만 재현된다.

### II. 결과는 숫자로 증명한다 (Measured Results)

- 모든 처리 방식은 MUST 같은 조건(같은 결제, 같은 동시 요청 수)으로 측정한다.
- 측정 결과(승인 건수, 중복 응답 수, 오류 수)는 README 결과 표에 기록한다.
- 표의 숫자는 MUST 저장소의 테스트를 다시 실행해서 재현할 수 있어야 한다.

### III. 실패한 방식도 남긴다 (Show the Failures)

- 이중 처리가 일어나는 방식도 삭제하지 않고 비교 대상으로 유지한다.
- 실패하는 방식에는 MUST 「왜 뚫리는지」를 보여주는 테스트가 있어야 한다.
- 이유: 올바른 방식의 가치는 실패한 방식과 나란히 놓을 때 드러난다.

### IV. 결정에는 이유를 남긴다 (Decisions Are Recorded)

- 처리 방식 선택처럼 결과에 영향을 주는 설계 결정은 MUST `docs/adr/`에 기록한다.
- ADR에는 배경, 검토한 대안, 채택 근거, 기각 사유를 포함한다.

### V. 처음부터 새로 작성한다 (Clean-Room Code)

- 모든 코드·문서는 MUST 이 저장소에서 새로 작성한다. 다른 조직의 코드·내부 문서·식별자를
  복사하거나 참조하지 않는다.
- 외부 결제대행사(PG)는 MUST 가짜 PG로 재현한다. 실제 PG 연동은 하지 않는다.

### VI. 단순하게 유지한다 (Simplicity)

- 실험에 필요 없는 기능(회원, 화면, 인증, 환불)은 만들지 않는다.
- 새 추상화나 의존성은 실험 결과를 더 명확하게 만들 때만 추가한다.

## 기술 제약

- 언어·프레임워크: Kotlin, Spring Boot
- 저장소: PostgreSQL, Redis
- 테스트: JUnit 5, Testcontainers
- 로컬 실행 조건: JDK와 Docker만 있으면 테스트 전체를 실행할 수 있어야 한다.

## 개발 흐름

- 작업은 Spec Kit 순서를 따른다: spec → plan → tasks → implement → converge.
  clarify는 spec에 확인이 필요한 항목이 남아 있을 때만 거친다.
- 구현 전에 spec의 검증 기준이 테스트로 옮겨질 수 있는지 확인한다.
- 각 단계의 결과물은 별도 커밋으로 남겨 작업 과정이 히스토리에 드러나게 한다.
- 머지 전에 구현이 spec의 요구사항·성공 기준과 일치하는지 대조한다.

## Governance

- 이 원칙은 다른 모든 작업 관행보다 우선한다.
- 원칙 변경은 이 문서를 수정하고 버전과 수정일을 갱신한다.
  - MAJOR: 원칙 삭제·재정의 / MINOR: 원칙·섹션 추가 / PATCH: 문구 수정
- plan 단계의 Constitution Check에서 위반 여부를 확인하고, 예외가 필요하면 plan에 사유를 적는다.

**Version**: 1.0.1 | **Ratified**: 2026-10-13 | **Last Amended**: 2026-10-13
