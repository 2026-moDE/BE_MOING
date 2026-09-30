# 📍 MOING — Server

> **지금 그 장소에 있는 사람만.**<br/>
> 주변 장소의 현장 사진과 혼잡도를 실시간으로 공유하는 서비스, MOING의 백엔드 서버입니다.

<br/>

## 📖 About the Project

**MOING**은 그 장소가 *지금* 어떤지 보여줍니다. 사용자가 현장에서 사진과 혼잡도를 올리면, 같은 장소를 찾는 사람이 지금 이 순간의 상황을 확인할 수 있습니다.

신뢰도가 서비스의 전부이기 때문에, 서버는 리뷰가 올라올 때마다 사용자와 장소 사이의 실제 거리를 검증합니다. 클라이언트 검증은 우회할 수 있지만 서버 검증은 그렇지 않습니다.

<br/>

## 🛠️ Tech Stack

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4479A1?style=for-the-badge&logo=postgresql&logoColor=white)
![AWS](https://img.shields.io/badge/AWS_EC2_·_S3_·_RDS-232F3E?style=for-the-badge&logo=amazonwebservices&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase_FCM-DD2C00?style=for-the-badge&logo=firebase&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)

<br/>

## 🏗️ Architecture

```
┌─────────────────────┐
│  React Native (iOS) │
└──────────┬──────────┘
           │  REST API · JWT
┌──────────▼──────────────────────────────┐
│        Spring Boot  (EC2 · systemd)     │
│                                         │
│     Controller → Service → Repository   │
│     Scheduler (5분) → 혼잡도 집계         │
└───┬─────────────┬─────────────┬─────────┘
    │             │             │
┌───▼────────┐ ┌──▼──────┐ ┌────▼─────┐
│ PostgreSQL │ │   S3    │ │   FCM    │
│    RDS     │ │  이미지  │ │   푸시   │
└────────────┘ └─────────┘ └──────────┘
```

<br/>

## 🗃️ Domain

전체 13개 테이블 중 핵심 엔티티입니다.

| Table | 설명 | 주요 컬럼 |
| :--- | :--- | :--- |
| `users` | 사용자 | `social_provider` · `fcm_token` |
| `places` | 장소 | `latitude` · `longitude` · `category` |
| `reviews` | 현장 리뷰 | `congestion_level` · `verification_distance` · `is_mock_location` |
| `place_congestion_cache` | 혼잡도 집계 캐시 | `congestion_index` · `updated_at` |
| `place_subscriptions` | 장소 구독 | `user_id` · `place_id` |
| `follows` | 친구 관계 | `follower_id` · `following_id` · `status` |

<br/>

## 🔌 API

| Method | 기능 | 설명 |
| :--- | :--- | :--- |
| `POST` | 리뷰 작성 | 서버 측 위치 검증 |
| `GET` | 리뷰 목록 | 커서 기반 페이지네이션 |
| `GET` | 주변 장소 조회 | 캐시 테이블에서 혼잡도 조회 |
| `POST` | Presigned URL 발급 | S3 직접 업로드용 |
| `POST` | 장소 구독 | 신규 리뷰 시 푸시 대상 등록 |
| `POST` | FCM 토큰 등록 | 디바이스 토큰 저장 |

<br/>

## ⚙️ Implementation

### 위치 검증

서버는 클라이언트가 보낸 좌표를 그대로 믿지 않습니다.

```
요청 좌표 + 장소 좌표  →  거리 계산  →  200m 초과 시 거부
                                   └→  verification_distance 저장
```

측정한 거리와 함께 위치 조작 여부를 `is_mock_location`에 기록합니다.

### 혼잡도 집계

집계를 요청 경로 밖으로 빼내 캐시 테이블로 옮겼습니다.

<table>
<tr><td><b>갱신</b></td><td>스케줄러가 5분 주기로 <code>place_congestion_cache</code> 갱신</td></tr>
<tr><td><b>계산식</b></td><td>최근 3시간 리뷰의 시간 가중 평균 — 최신 리뷰일수록 가중치가 큼</td></tr>
<tr><td><b>조회</b></td><td>장소 목록의 캐시를 <code>IN</code> 절 한 번으로 조회</td></tr>
</table>

### 쿼리 개선 — `1 + 2N` → `1 + 1 + N`

주변 장소 조회는 지도를 움직일 때마다 호출됩니다. 초기 구현은 장소마다 혼잡도와 대표 사진을 각각 조회해, 장소가 N개면 `1 + 2N`개의 쿼리가 발생했습니다.

| | 쿼리 수 | 방식 |
| :--- | :---: | :--- |
| **Before** | `1 + 2N` | 장소별 혼잡도 · 대표 사진 개별 조회 |
| **After** | `1 + 1 + N` | `EXISTS` 선필터 + 혼잡도 일괄 조회 |

- `EXISTS` 서브쿼리로 최근 72시간 내 리뷰가 있는 장소만 반환해 N 자체를 줄였습니다
- 혼잡도는 캐시 테이블에서 `IN` 절 한 번으로 가져옵니다

> **TODO** — 대표 사진은 아직 장소별로 조회합니다. 장소 ID 목록으로 일괄 조회한 뒤 그룹핑하도록 옮기는 것이 다음 작업입니다.

### 이미지 업로드

이미지는 서버를 거치지 않습니다.

```
Client  ──►  POST /presigned-url     서버가 URL 발급
Client  ──►  PUT  (S3로 직접 업로드)
Client  ──►  POST /reviews           image_url 포함
```

업로드 전 얼굴 블러 처리를 적용합니다.

### 커서 기반 페이지네이션

리뷰는 실시간으로 계속 삽입되기 때문에 `offset` 방식은 페이지 경계에서 중복과 누락이 생깁니다. 마지막 항목의 커서를 기준으로 다음 페이지를 조회합니다.

<br/>

## 🖥️ Infrastructure

<table>
<tr><td><b>Runtime</b></td><td>EC2(Ubuntu)에서 systemd 서비스로 구동하며, 프로세스가 종료되면 자동 재시작합니다.</td></tr>
<tr><td><b>Database</b></td><td>RDS를 사용해 애플리케이션 인스턴스와 분리했습니다. 디스크가 가득 차도 DB가 함께 멈추지 않습니다.</td></tr>
<tr><td><b>Logging</b></td><td><code>logrotate</code>(100MB)와 <code>journald</code>(200MB) 용량 제한, 주간 정리 cron을 두었습니다.</td></tr>
<tr><td><b>Timezone</b></td><td>JVM을 UTC로 고정하고 DB 커넥션과 JSON 직렬화의 타임존을 명시해, 변환이 한 곳에서만 일어나도록 했습니다.</td></tr>
</table>

<br/>

## 🚀 Getting Started

모든 설정값은 환경변수로 주입합니다. **저장소에는 실제 값이 포함되어 있지 않습니다.**

```
DB_URL · DB_USERNAME · DB_PASSWORD
JWT_SECRET
AWS_ACCESS_KEY · AWS_SECRET_KEY · AWS_S3_BUCKET
FCM_CREDENTIALS
```

```bash
./gradlew bootRun
```

<br/>

## 🌱 Git Convention

```
main              배포 — 직접 푸시 금지, PR만 허용
develop           개발 통합 브랜치
├── feature/*     기능 개발  → develop
└── hotfix/*      긴급 수정  → main · develop
```

| Type | 설명 | Type | 설명 |
| :--- | :--- | :--- | :--- |
| `feat` | 새 기능 추가 | `chore` | 의존성 · 설정 변경 |
| `fix` | 버그 수정 | `style` | 코드 포맷 |
| `refactor` | 리팩토링 | `test` | 테스트 코드 |
| `docs` | 문서 수정 | | |

```bash
git checkout develop
git checkout -b feature/기능명
git commit -m "feat: 기능 설명"
git push origin feature/기능명
# feature → develop PR 생성, 머지 후 브랜치 삭제
```
