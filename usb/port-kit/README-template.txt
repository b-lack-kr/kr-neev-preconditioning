프리컨디셔닝 — 차량 호환성 수집 USB

이 USB는 차량 프로필 추가에 필요한 정보를 읽어 USB의
preconditioning-port-report.txt에 기록합니다. 읽기 전용 수집 도구이며 앱을 설치하거나
차량 파일을 바꾸거나 배터리 컨디셔닝을 시작·중지하지 않습니다.
한국어·섭씨판 2.0.0-ko.1 / 원본 2.0.0 기반입니다.

준비
NEMESIS가 먼저 설치되어 있어야 합니다. 이 단계에 엔지니어링 모드는 사용하지 않습니다.

1. 빈 FAT32 또는 exFAT USB를 준비하거나 기존 내용을 백업합니다.
2. 기존 NEMESIS.data, NEMESIS.sh, ota_package.tar, preconditioning, preconditioning-port,
   packages를 USB 밖으로 옮깁니다.
3. 바깥쪽 ZIP을 풀어 폴더 안의 내용물을 USB 최상위에 복사합니다.
   USB를 열었을 때 NEMESIS.data와 README.txt가 바로 보여야 합니다.
4. USB를 안전하게 제거합니다.

차량에서 수집
1. 주차하고 차량을 켠 뒤 화면이 완전히 켜질 때까지 기다립니다.
2. 유선 Android Auto/CarPlay용 USB 데이터 포트에 연결합니다.
3. NEMESIS USB 실행 안내를 수락합니다. 시간이 만료되면 USB를 다시 연결합니다.
4. 선택 사항: 안내를 수락한 직후 오른쪽 TUNE 노브를 약 2초 눌렀다 놓는 동작을
   결과가 나올 때까지 반복합니다. 앱 실행 바로가기에 사용할 버튼을 파악하기
   위한 자료입니다. 해당 노브가 없으면 생략합니다.
5. 약 30초 뒤 결과가 나옵니다. 재시작은 필요하지 않습니다.

결과 확인
1. 컴퓨터에서 preconditioning-port-report.txt를 열어 내용을 확인합니다.
   원본 수집기는 VIN·일련번호·위치를 수집하지 않습니다.
2. 지원 추가를 요청하려면 원본 저장소의 Vehicle port request 양식을 사용합니다.
   https://github.com/paytondevs/gen5w-preconditioning/issues/new?template=vehicle-port.yml
3. 차종·연식·트림·지역과 설정 화면의 전체 소프트웨어 버전을 적고 보고서를 첨부합니다.
4. 수집 실패 시 preconditioning-collect.log도 함께 확인합니다.

보고서를 수집했다고 해당 차량 지원이 보장되지는 않습니다. 개발자가 프로필을
검토할 때 사용하는 자료입니다. 한국 아이오닉5 260902는 원본에서 TUNE 바로가기가
미지원이며, 별도 검증 없이 버튼 프로필을 활성화하지 않습니다.

원본: https://github.com/paytondevs/gen5w-preconditioning
