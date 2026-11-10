# 프리컨디셔닝 1.0.0 빌드

Android applicationId·namespace 및 NEMESIS ID는 `kr.neev.preconditioning`입니다.
`VERSION`의 1.0.0에서 versionCode 10000을 계산합니다. Java 소스와 테스트는
`app/src/*/java/kr/neev/preconditioning/`에 있습니다.

## 도구

- 전체 검사에 사용한 JDK: 25 (JDK 17에서는 최신 Android 린트 실행 문제 가능)
- Android SDK platform 37.0, build-tools 36.0.0
- Gradle wrapper 9.5.0 / Android Gradle Plugin 9.3.1
- USB 패키징·호스트 테스트: Python 3.10 이상, Bash
- 대상 Android: 4.4 / API 19

`JAVA_HOME`, `ANDROID_HOME`을 별도로 지정하세요. 서명에는 기존 빌드 스크립트의
`LAUNCHPAD_KEYSTORE`, `LAUNCHPAD_STORE_PASSWORD`, 필요하면
`LAUNCHPAD_KEY_PASSWORD`, `LAUNCHPAD_KEY_ALIAS`를 사용합니다.
기본 별칭은 `preconditioning`입니다. 서명 키와 비밀번호는 소스·배포물에 없습니다.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
python3 tools/test_ev6_scripts.py
python3 tools/test_temperature_binder.py
python3 tools/test_service_rebind.py
python3 tools/test_usb_bundles.py
sh tools/build_apk.sh
python3 tools/build_usb_bundles.py
```

`build_apk.sh`는 테스트·린트·빌드 후 API 19용 v1 서명, 정렬, 서명 검증을 수행합니다.
이 명령은 차량에 연결하지 않습니다.

출력은 `dist/Preconditioning.apk`, `dist/kr.neev.preconditioning.zip`,
`dist/Preconditioning-install-usb.zip`, `dist/Preconditioning-manual-install-usb.zip`,
`dist/Preconditioning-port-kit-usb.zip`입니다. `*-signing.apk`는 중간 산출물입니다.

기존 원본 2.x 앱과 패키지 ID가 다릅니다. 기존 앱 제거 후 새 앱을 설치합니다.
원본 1.x의 `/system/app/Launchpad.apk`만 명시적인 이전 대상으로 남겨 두었습니다.
앞으로 이 앱 자체 업데이트에서는 `kr.neev.preconditioning`의 백업·복원 절차를 사용합니다.
호환성 검사, 차량 요청·취소 명령, 섭씨 판단 기준은 원본을 유지합니다.

원본 빌드 문서는 [BUILDING-upstream.md](BUILDING-upstream.md)에 그대로 보존했습니다.
원본 문서의 옛 패키지·파일 이름을 현재 변경판에 그대로 적용하지 마세요.
