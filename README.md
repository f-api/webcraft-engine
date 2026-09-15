# WebCraft Engine

WebCraft 게임 알고리즘과 실행 리소스 라이브러리입니다. JDK 21을 사용합니다.

`./gradlew jar sourcesJar`로 빌드합니다. 빌드는 GitHub Releases에서 고정 버전의 생성기 묶음을 받고 해시를 검사합니다. 학생은 별도 빌드 없이 릴리스 JAR을 의존성으로 사용합니다. Spring Boot 4.1의 Jackson, Spring Transaction, SLF4J 런타임과 함께 사용합니다.

게임 화면, DB 초기화 SQL, 지형 데이터와 생성기 묶음이 JAR에 포함됩니다. 생성기는 처음 사용할 때 사용자 홈의 `.webcraft/engine` 아래에 해시별로 준비하며, 동시 실행 시 파일 잠금으로 중복 추출을 방지합니다. 기존 개별 JAR 해시 검증과 격리 ClassLoader를 유지합니다.

과제용 Entity, Controller와 Service, WebSocket 및 Redis 구현은 학생 프로젝트에서 관리합니다. 관련 Entity는 이 라이브러리에 포함하지 않습니다.

일반 의존성 사용 시 실행 JAR만 필요합니다. 소스 JAR과 생성기 ZIP은 유지보수 및 재현 빌드용 릴리스 첨부 파일입니다.
