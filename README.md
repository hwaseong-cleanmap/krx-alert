# 국내주식 포착 · Android 소스 시험판 v0.1

**설치 APK가 아닙니다. 전체 Android 빌드와 한국투자 실계정 접속 및 실기기 시험은 완료되지 않았습니다.**

## 들어 있는 기능

- 설치형 Android 앱 프로젝트(Java, Android API 26 이상, target/compile 35).
- 한국투자 실전 OpenAPI의 HTTPS 접속 승인키 발급 및 KRX 실시간 체결가 H0STCNT0 WebSocket 연결 코드.
- 최대 40종목을 직접 등록. 보유 종목을 우선 요청. 전체 시장 자동 발굴 기능은 아직 없습니다.
- 살 것: 감시 종목, 최근 체결가, 직접 지정한 매수 감시가, 조건 포착 기록.
- 팔 것: 보유 종목 중 직접 지정한 손절가 이하/목표가 이상 조건이 포착된 기록.
- 보유: 종목명, 실제 매수가와 매수 시각. ‘샀어요’와 ‘팔았어요’로 직접 기록.
- 새 유효 체결 수신 때 가격 조건을 평가하며 별도 정기 스캔 대기 시간을 두지 않음.
- 같은 거래일·종목·매매 구분·조건·설정 버전의 중복 신호 알림을 억제. 연결 재시작 후에도 유지.
- 알림 스위치 OFF는 신호 알림만 중지. 감시 중지 버튼은 연결을 종료.
- 날짜, KRX 정규/종가 장 구분, 시각 검증. 20초 초과 지연 체결은 신호에서 제외.
- 연결 끊김 재시도, 90초 메시지 무응답 재연결. 인증 오류는 60초 간격 재시도.
- AppKey/AppSecret은 Android Keystore AES-GCM으로 암호화 저장. 앱 백업 비활성화. 주문/계좌조회 API 없음.

가격 조건은 ‘도달’ 방식입니다. 연결 시작 시 이미 감시가 이상/손절가 이하라면 첫 유효 체결에서 알립니다. 반드시 직전 가격을 지나가는 순간만 잡는 교차 조건은 아닙니다. 포착 기록은 현재 매수·매도 권유가 아니며 가격이 되돌아와도 기록은 남습니다. 사용자가 가격 조건을 수정하면 기록과 중복 방지 상태를 초기화합니다.

## 구현되지 않은 것

- 한국 주식 전 종목 자동 수집·차트 스캔, 후보 자동 선정.
- 기존 PC 연구 엔진의 모든 조건, 시장 급락/종목 개별 급락 분리, BA4 등 이전 연구 전략 이식.
- 수익·손실 가능 확률. 검증된 예측 모델을 연결하기 전에는 숫자를 표시하지 않습니다.
- 자동 매매, 증권계좌의 실제 체결 동기화, 차트 화면, 공시 분석.
- 장 시작/마감 매수 후 다음 날 매도 전략의 검증 및 시간 매도 알림.
- API 키 없이 사용할 수 있는 시세. 앱 설정에서 본인 실전 OpenAPI 키를 직접 입력해야 합니다.

이 앱은 기존 PC 프로그램을 대체하는 완성품이 아니라 모바일 시세·알림 기반을 시험하기 위한 별도 프로젝트입니다.

## 서버 없는 감시의 범위

사용자가 앱을 열고 ‘감시 시작’을 누르면 foreground dataSync 서비스가 시세 연결을 유지하도록 작성했습니다. 화면이 닫혀도 서비스가 살아 있고 통신이 유지되는 동안 수신·알림이 가능합니다. 강제 종료, 배터리 제한, 네트워크 장애, 알림 권한/채널 차단, 방해금지 모드에서는 즉시 수신/소리/화면 표시가 보장되지 않습니다. 재부팅 후 자동 시작은 구현하지 않았습니다.

Android 15 이상을 대상으로 하는 dataSync foreground 서비스는 백그라운드 실행에 24시간 중 합계 6시간 한도가 있습니다. 앱을 다시 전면으로 가져오면 한도가 재설정되지만, 이를 무중단 장 전체 감시 보장으로 해석하면 안 됩니다. 한도 콜백에서 서비스를 종료하고 앱 상태를 갱신합니다. 감시 중지 상태는 앱을 다시 열어 확인해야 합니다. 중지된 앱이 새 신호 알림을 전달할 수는 없습니다.

현재 정규장 감시 시간은 한국시간 평일 09:00~15:30으로 제한했습니다. 휴장일에는 유효한 당일 체결이 없으므로 가격 신호가 발생하지 않습니다. 실제 거래소 공지에 따른 개장 지연·시간 변경을 자동 반영하는 달력 기능은 없습니다. NXT·KRX 프리/애프터 거래는 제외합니다.

한국투자 공식 예제의 실시간 주소는 `ws://ops.koreainvestment.com:21000`입니다. 따라서 이 WebSocket 구간은 TLS 암호화가 아닙니다. 앱은 해당 도메인에만 평문 예외를 허용합니다. AppKey/AppSecret 인증은 HTTPS에서 수행하며, WebSocket에는 발급된 접속 승인키가 사용됩니다. 앱 전체 배포용 공용 키를 넣지 마세요.

## 빌드

### Android Studio 환경

1. 이 폴더를 프로젝트로 열고 JDK 17, Android SDK Platform 35와 Build Tools 35.0.0을 설치합니다.
2. Gradle 8.9를 사용합니다. 현재 ZIP에는 Gradle Wrapper 바이너리가 없습니다. 로컬 Gradle 8.9의 `gradle wrapper --gradle-version 8.9`로 생성하거나 Gradle 8.9로 직접 빌드합니다.
3. 프로젝트 루트에서 `gradle testDebugUnitTest assembleDebug` 실행.
4. 생성 경로: `app/build/outputs/apk/debug/app-debug.apk`.

### GitHub Actions 환경

프로젝트 내용을 본인 GitHub 저장소 루트에 올리면 `.github/workflows/android.yml`이 테스트와 debug APK 빌드를 수행하도록 설정되어 있습니다. 직접 게시하거나 워크플로를 실행한 상태는 아닙니다. 성공한 실행의 `KRX-Android-debug-APK` 아티팩트를 내려받아 압축을 풀면 APK가 나옵니다. 별도 상시 운영 서버는 필요하지 않습니다. Actions 이용 가능 여부와 비용은 본인 계정 정책에 따릅니다.

디버그 APK는 개발 시험용입니다. CI 실행마다 임시 서명키가 달라질 수 있어 재설치 때 기존 앱과 서명이 충돌할 수 있습니다. 제거하면 로컬 매수 기록과 키가 지워집니다. 배포·지속 업데이트 전에는 고정 서명키와 릴리스 절차가 필요합니다. 저장소/Actions에 실제 API 키를 올리지 않습니다.

## 첫 실기기 시험

1. 본인 한국투자 계좌에서 OpenAPI를 신청하고 실전 AppKey/AppSecret 발급.
2. 앱 알림 권한 허용 → 설정에서 두 키를 입력.
3. 국내 6자리 숫자 종목코드와 이름, 확인할 가격을 직접 등록. 잘못된 코드/미지원 종목은 수신되지 않을 수 있습니다.
4. 정규장 ‘감시 시작’ → 종목별 실제 가격 수신 확인. 다른 프로그램에서 같은 키의 WebSocket을 사용하면 세션 한도에 걸릴 수 있습니다.
5. 조건 포착/중복 억제/알림 OFF·ON/화면 끄기/통신 끊김·복구를 각각 시험.
6. 보유 기록 등록 후 손절·목표가 알림이 해당 보유 종목에만 나오는지 확인.
7. Android 15/16 장시간 대기와 서비스 한도 종료 시험. 이 시험 전에는 실사용 알림 수단으로 신뢰하지 않습니다.

## 검증 기록 (2026-11-05)

- 실제 Java 컴파일러로 Rules와 RulesSelfTest 컴파일 및 실행: **26개 검사 통과**.
- Java 파일 문법 파싱, Manifest/network security XML 파싱: 통과.
- SDK·Gradle 없음, 공식 SDK 다운로드 네트워크 접속 시간 초과로 전체 Android APK 빌드: **미실행**.
- API 키 미입력으로 실계정 접속·실제 시세/권한/배터리·알림 지연 시험: **미실행**.
- 문법 파싱은 Android SDK를 사용한 전체 컴파일을 대체하지 않습니다.

독립 도메인 검사 예시(JDK17):

```sh
mkdir -p /tmp/krx-domain
java com.sun.tools.javac.Main -d /tmp/krx-domain app/src/main/java/krx/alert/Rules.java app/src/test/java/krx/alert/RulesSelfTest.java
java -cp /tmp/krx-domain krx.alert.RulesSelfTest
```

## 공식 근거

- 한국투자 공식 설정, REST/실시간 서버 주소: https://github.com/koreainvestment/open-trading-api/blob/main/kis_devlp.yaml
- 접속 승인키 및 WebSocket 메시지: https://github.com/koreainvestment/open-trading-api/blob/main/examples_user/kis_auth.py
- H0STCNT0 필드 순서: https://github.com/koreainvestment/open-trading-api/blob/main/examples_user/domestic_stock/domestic_stock_functions_ws.py
- 최신 API/시장 구분 변경 공지 확인: https://apiportal.koreainvestment.com/
- Android foreground 서비스: https://developer.android.com/develop/background-work/services/fgs
- Android 서비스 시간 한도: https://developer.android.com/develop/background-work/services/fgs/timeout

구독 상한은 한국투자 정책에 따라 달라질 수 있어 앱에서는 보수적으로 40종목까지만 허용합니다. 전체 시장 스캔 후보와 실제 실시간 감시 범위를 같은 것으로 표시하지 않습니다.
