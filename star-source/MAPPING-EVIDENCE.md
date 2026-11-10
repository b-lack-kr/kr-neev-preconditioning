# 국내 아이오닉5 260902 순정 별표 설정 매핑

목표는 순정 별표 버튼 설정의 선택 목록에 `프리컨디셔닝`을 추가하는 것입니다. 현재 지정은 사용자가 설정 화면에서 선택할 때만 바뀝니다. 헤드유닛과 핸들 별표 설정 화면에 같은 선택지가 추가됩니다. 기존 순정 선택지는 유지합니다.

연결 앱은 `프리컨디셔닝 1.0.0`이며 Android·NEMESIS ID는 `kr.neev.preconditioning`입니다. 실행 대상은 `kr.neev.preconditioning/kr.neev.preconditioning.MainActivity`입니다. 별표 연결 모듈의 내부 ID·버전은 별도로 `neev-star-precondition`·`0.1.0`을 유지합니다.

`payload/maps`에는 ButtonSettings 31개 지점만 있습니다. `launcher`와 `homesetting`은 포함하지 않습니다. 최소 `car.json`은 기존 전체 맵에 병합할 메타데이터이며 전체 맵 대체용이 아닙니다. 기존 375개 맵에 전부 추가되면 406개가 됩니다.

## 출처

로컬 원본 보고서 `analysis/vehicle-reports/car-report-neev-kor-260902-20231104-1101.json`의 `map_car.points` 중 `buttonsettings.` 접두사의 31개 항목과 완전히 일치합니다. 같은 내용의 기존 후보 `neev-carplay/versions/v0.1.11/source/maps/buttonsettings.json`을 바이트 그대로 사용했습니다.

- 원본 보고서 SHA-256: `f2de6e4664a5ef4d12092e15fa62e201828e33c34e00f00e6c82b37410dedae8`
- `buttonsettings.json` SHA-256: `c63d59112c0abdd196968aba74321d69edd5f740eff821d6d49ad6a980627ed1`
- 원본 `/system/app/ButtonSettings.odex` MD5: `ea2e733ea011d05246c76e1b1a9116ea`
- 패키지: `com.android.buttonsettings`
- 보고서의 해당 지점은 모두 구조로 발견됐으며 `by_reference`와 `not_found`에 포함되지 않습니다.

보고서 날짜 문자열은 차량 시계 값입니다. 현재 실차 검증 날짜로 해석하지 않습니다. 이 자료와의 일치는 실제 설정 화면 표시·선택·실행 성공을 대신하지 않습니다.

## 적용 전 읽기 전용 검사

정확한 제품 식별자, 플랫폼과 API 19를 확인하고 현재 ButtonSettings ODEX의 MD5를 대조합니다. `PathClassLoader(/system/app/ButtonSettings.apk, boot)`로 클래스를 읽어 31개 클래스·생성자·메서드·필드를 모두 확인해야 합니다. 메서드 반환형, 매개변수, 필드형과 정적 여부를 포함합니다. 배열형 `boolean[]`, `int[]` 처리도 필요합니다.

HMG-API와 프리컨디셔닝 앱의 정확한 Activity가 설치·활성화돼 있어야 합니다. 기존 `framework.resources.get_drawable`, `framework.resources.get_text`도 필요합니다. `launcher` 또는 `homesetting` 매핑이 켜진 맵에는 이 후보를 덧씌우지 않습니다. 먼저 이전 홈 아이콘 변경의 복구를 확인합니다.

## 런타임과 복구

HMG-API 1.0.2의 `HmgApi.appLoaded`는 런처 목록 훅 사용 여부와 별개로 `StarKey.start`를 호출합니다. 따라서 홈 메뉴 매핑 없이 별표 액션 수신기를 사용할 수 있습니다. ButtonSettings가 자체적으로 아이콘·문자열 리소스 훅을 등록합니다.

`provides.star_key`는 액션을 zygote 시작 때 읽습니다. 이 모듈 설치 후 정상 재시작이 필요하며 `input`의 실시간 갱신과 혼동하면 안 됩니다. 예상 로그는 `star key: N mod actions offered in button settings`, `star key: buttonsettings.head_screen shows N mod actions`, `[neev-star-precondition] star key: precondition`입니다.

순정 설정의 원래 목록 생성 후 새 행을 추가합니다. 저장된 지정이 새 액션과 이미 일치할 때만 선택 표시를 보정하며 새 액션을 자동 선택하지 않습니다. 실제 목록, 순정 항목 선택, 새 항목 선택과 실행은 별도 실차 검증 대상입니다.

새 액션을 사용자가 선택한 뒤 모듈을 제거하면 다음 시작 때 HMG-API의 정리 코드가 그 지정을 `없음`으로 바꿉니다. 이전 순정 지정을 자동 복원하지 않습니다. 복구 도구는 다른 모듈·키 지정·프로필 DB를 덮어쓰지 않습니다.
