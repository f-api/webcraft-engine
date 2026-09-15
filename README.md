# WebCraft Engine

WebCraft 게임 알고리즘과 실행 리소스 라이브러리입니다. JDK 21을 사용합니다.

`./gradlew jar sourcesJar`로 빌드합니다. 빌드는 GitHub Releases에서 고정 버전의 생성기 묶음을 받고 해시를 검사합니다. 학생은 별도 빌드 없이 릴리스 JAR을 의존성으로 사용합니다. Spring Boot 4.1의 JPA, Redis, Web MVC, WebSocket, Validation 스타터와 함께 사용합니다.

게임 화면, DB 초기화 SQL, 지형 데이터와 생성기 묶음이 JAR에 포함됩니다. 생성기는 처음 사용할 때 사용자 홈의 `.webcraft/engine` 아래에 해시별로 준비하며, 동시 실행 시 파일 잠금으로 중복 추출을 방지합니다. 기존 개별 JAR 해시 검증과 격리 ClassLoader를 유지합니다.

과제용 Entity 4개(`Player`, `World`, `ChatMessage`, `WorldTrialSite`)와 CRUD, 채팅, 라우팅, 세션 및 Redis 처리는 학생 프로젝트에서 관리합니다. 게임 전용 Entity 73개, 게임 핸들러, 전송 큐, 월드 생성과 저장 구현은 라이브러리에 포함됩니다.

라이브러리는 학생 구현 클래스에 컴파일 의존하지 않습니다. `WorldStore`, `PlayerStore`, `SessionRegistry`, `PresenceOperations`, `TrialStorage` 계약으로 연결합니다. `META-INF/orm.xml`은 게임 Entity의 연관관계 18개를 학생의 실제 `World`와 `Player` Entity에 연결하며 기존 외래 키를 유지합니다.

빌드가 생성한 `META-INF/webcraft-components.txt`에 포함된 엔진 Bean만 자동 구성에서 등록합니다. 학생 애플리케이션의 컴포넌트 스캔은 `EngineComponentFilter`로 이 목록을 제외합니다. JPA Entity와 Repository는 애플리케이션의 `com.gameexpert` 스캔 범위에서 한 번 등록합니다.

일반 의존성 사용 시 실행 JAR만 필요합니다. 소스 JAR과 생성기 ZIP은 유지보수 및 재현 빌드용 릴리스 첨부 파일입니다.

엔진용 SQL 초기화, 컬럼 명명 규칙, Open-in-View, Redis Repository 비활성화와 HTTP 압축은 라이브러리가 기본값으로 제공합니다. 애플리케이션 설정과 환경변수가 우선합니다. MySQL 및 Redis 접속 정보와 JPA `ddl-auto`는 제공하지 않습니다. `./gradlew verifyDefaults`로 기본값과 덮어쓰기를 확인할 수 있습니다.
