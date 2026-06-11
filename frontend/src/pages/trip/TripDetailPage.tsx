import { useEffect, useMemo, useState } from 'react'
import type { AuthUser } from '../../api/auth'
import { API_BASE_URL, ApiError } from '../../api/client'
import { getTripDetail } from '../../api/trips'
import type { TripDetail, TripMember, TripStatus } from '../../api/trips'
import './TripDetailPage.css'

type TripDetailPageProps = {
  accessToken: string
  tripId: string
  user: AuthUser | null
  onBackToMain: () => void
  onLogout: () => void
}

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'

type QuestionGroup = {
  title: string
  description: string
  items: string[]
}

type MockPlace = {
  id: string
  day: number
  order: number
  time: string
  title: string
  category: string
  area: string
  x: number
  y: number
  hours: string
  parking: string
  price: string
  rating: string
  review: string
  photoTip: string
}

const QUESTION_GROUPS: QuestionGroup[] = [
  {
    title: '동반자와 일정',
    description: '누구와 언제 움직이는지 먼저 확정합니다.',
    items: ['동반자 유형', '여행 인원', '출발지', '여행 기간', '다중 숙소 여부'],
  },
  {
    title: '여행 스타일',
    description: 'AI가 일정 밀도와 장소 성격을 결정하는 기준입니다.',
    items: ['여유/빡빡함', '관광/맛집/휴식', '실내/실외', '사진 스팟 중요도'],
  },
  {
    title: '예산과 비용',
    description: '식비, 입장료, 체험 비용의 상한을 정합니다.',
    items: ['전체 예산', '1인 예산', '식비 범위', '유료 장소 허용 여부'],
  },
  {
    title: '이동수단',
    description: '동선 현실성을 판단하는 핵심 입력입니다.',
    items: ['자차', '대중교통', '도보', '택시', '주차 중요도'],
  },
  {
    title: '식사와 제약',
    description: '피해야 할 조건과 반드시 넣을 장소를 관리합니다.',
    items: ['선호 음식', '피해야 할 음식', '아이/부모님 동반', '반려동물', '필수 방문지'],
  },
]

const MOCK_PLACES: MockPlace[] = [
  {
    id: 'day1-1',
    day: 1,
    order: 1,
    time: '09:30 - 10:40',
    title: '도착지 브런치',
    category: '식사',
    area: '중심가',
    x: 22,
    y: 66,
    hours: '08:00 - 15:00',
    parking: '전용 주차 없음, 반경 400m 공영주차장 후보 필요',
    price: '1인 12,000 - 18,000원 예상',
    rating: '평점/리뷰 연동 예정',
    review: '블로그 후기와 방문 팁 요약 영역입니다.',
    photoTip: '창가 좌석, 매장 입구, 대표 메뉴 구도 정보를 요약할 예정입니다.',
  },
  {
    id: 'day1-2',
    day: 1,
    order: 2,
    time: '11:10 - 12:30',
    title: '대표 관광지',
    category: '관광',
    area: '해변/전망',
    x: 48,
    y: 38,
    hours: '상시 개방 여부 확인 예정',
    parking: '주변 주차장과 도보 거리 비교 예정',
    price: '입장료 확인 예정',
    rating: '외부 장소 API 연동 예정',
    review: '혼잡 시간, 체류 시간, 날씨 영향을 요약할 예정입니다.',
    photoTip: '해질녘 역광, 전망 포인트, 인기 포즈 정보를 제공할 예정입니다.',
  },
  {
    id: 'day1-3',
    day: 1,
    order: 3,
    time: '13:00 - 14:00',
    title: '후보 카페',
    category: '카페',
    area: '관광지 인근',
    x: 68,
    y: 54,
    hours: '10:00 - 21:00',
    parking: '매장 앞 2대 가능 여부 확인 필요',
    price: '음료 5,500 - 8,000원 예상',
    rating: '평점/리뷰 연동 예정',
    review: '웨이팅, 좌석, 시그니처 메뉴 요약 영역입니다.',
    photoTip: '테라스, 디저트 테이블샷, 창가 자리 정보를 요약할 예정입니다.',
  },
  {
    id: 'day2-1',
    day: 2,
    order: 1,
    time: '10:00 - 11:40',
    title: '로컬 산책 코스',
    category: '산책',
    area: '구도심',
    x: 28,
    y: 42,
    hours: '상시 가능',
    parking: '시작 지점 주변 공영주차장 추천 예정',
    price: '무료',
    rating: '콘텐츠 기반 만족도 요약 예정',
    review: '코스 난이도와 체류 시간 요약 영역입니다.',
    photoTip: '골목 간판, 벽화, 길 끝 구도 정보를 요약할 예정입니다.',
  },
  {
    id: 'day2-2',
    day: 2,
    order: 2,
    time: '12:10 - 13:20',
    title: '점심 후보지',
    category: '식사',
    area: '시장 인근',
    x: 54,
    y: 60,
    hours: '11:00 - 20:00',
    parking: '시장 공영주차장 혼잡도 확인 예정',
    price: '1인 10,000 - 16,000원 예상',
    rating: '평점/리뷰 연동 예정',
    review: '메뉴 추천, 대기 시간, 회전율을 요약할 예정입니다.',
    photoTip: '대표 메뉴 근접샷과 시장 입구 인증샷 정보를 제공할 예정입니다.',
  },
  {
    id: 'day3-1',
    day: 3,
    order: 1,
    time: '09:30 - 11:00',
    title: '체크아웃 후 가벼운 코스',
    category: '휴식',
    area: '숙소 주변',
    x: 36,
    y: 58,
    hours: '운영시간 확인 예정',
    parking: '숙소 주차 연장 가능 여부 확인 예정',
    price: '무료 또는 소액 지출 예상',
    rating: '외부 장소 API 연동 예정',
    review: '마지막 날 피로도를 고려한 코스 설명 영역입니다.',
    photoTip: '짐 들고 이동하기 편한 포토존을 요약할 예정입니다.',
  },
]

const DAY_OPTIONS = [1, 2, 3]

export function TripDetailPage({
  accessToken,
  tripId,
  user,
  onBackToMain,
  onLogout,
}: TripDetailPageProps) {
  const [trip, setTrip] = useState<TripDetail | null>(null)
  const [status, setStatus] = useState<AsyncStatus>('idle')
  const [errorMessage, setErrorMessage] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!accessToken || !tripId) {
      return
    }

    let ignore = false
    const timeoutId = window.setTimeout(() => {
      setStatus('loading')
      setErrorMessage('')

      void getTripDetail(accessToken, tripId)
        .then((response) => {
          if (ignore) {
            return
          }
          setTrip(response)
          setStatus('success')
        })
        .catch((error: unknown) => {
          if (ignore) {
            return
          }
          setStatus('error')
          setErrorMessage(toUserMessage(error))
        })
    }, 0)

    return () => {
      ignore = true
      window.clearTimeout(timeoutId)
    }
  }, [accessToken, tripId, reloadKey])

  const displayName = user?.nickname ?? resolveOwnerNickname(trip?.members) ?? '여행자'

  return (
    <main className="trip-detail-page">
      <div className="trip-detail-grid-bg" aria-hidden="true" />
      <TripDetailHeader displayName={displayName} onBackToMain={onBackToMain} onLogout={onLogout} />

      {!accessToken && (
        <DetailStateCard
          title="로그인 확인 중"
          description="상세 페이지를 열기 위해 로그인 세션을 확인하고 있습니다."
          actionLabel="메인으로 돌아가기"
          onAction={onBackToMain}
        />
      )}

      {accessToken && status !== 'success' && (
        <DetailStateCard
          title={status === 'error' ? '여행 상세를 불러오지 못했습니다' : '여행 상세를 불러오는 중'}
          description={status === 'error' ? errorMessage : '여행 기본 정보와 참여자 정보를 조회하고 있습니다.'}
          actionLabel={status === 'error' ? '다시 시도' : undefined}
          onAction={status === 'error' ? () => setReloadKey((value) => value + 1) : undefined}
          secondaryActionLabel="메인으로 돌아가기"
          onSecondaryAction={onBackToMain}
        />
      )}

      {accessToken && status === 'success' && trip && (
        <section className="trip-detail-shell" aria-label="여행 상세 화면">
          <TripDetailHero trip={trip} />
          <div className="trip-detail-workspace">
            <aside className="trip-detail-left-column" aria-label="여행 준비 입력">
              <PreparationPanel />
              <PromptPanel />
            </aside>
            <section className="trip-detail-center-column" aria-label="지도와 일정">
              <ItineraryMapWorkspace />
            </section>
            <aside className="trip-detail-right-column" aria-label="협업 패널">
              <MembersPanel members={trip.members} currentUserId={user?.id} />
              <ProposalPanel />
              <ChatPanel members={trip.members} />
            </aside>
          </div>
        </section>
      )}
    </main>
  )
}

function TripDetailHeader({
  displayName,
  onBackToMain,
  onLogout,
}: {
  displayName: string
  onBackToMain: () => void
  onLogout: () => void
}) {
  return (
    <nav className="trip-detail-nav" aria-label="여행 상세 내비게이션">
      <button className="trip-back-button" type="button" onClick={onBackToMain}>
        메인으로
      </button>
      <div className="trip-detail-brand">
        <span className="trip-detail-brand-mark" aria-hidden="true">PM</span>
        <strong>PlanMate</strong>
      </div>
      <div className="trip-detail-user-menu">
        <span>{displayName}</span>
        <button className="trip-ghost-button" type="button" onClick={onLogout}>
          로그아웃
        </button>
      </div>
    </nav>
  )
}

function DetailStateCard({
  title,
  description,
  actionLabel,
  onAction,
  secondaryActionLabel,
  onSecondaryAction,
}: {
  title: string
  description: string
  actionLabel?: string
  onAction?: () => void
  secondaryActionLabel?: string
  onSecondaryAction?: () => void
}) {
  return (
    <section className="trip-detail-state-card" aria-live="polite">
      <p className="trip-eyebrow">Trip detail</p>
      <h1>{title}</h1>
      <p>{description}</p>
      <div className="trip-state-actions">
        {actionLabel && onAction && (
          <button className="trip-primary-button" type="button" onClick={onAction}>
            {actionLabel}
          </button>
        )}
        {secondaryActionLabel && onSecondaryAction && (
          <button className="trip-secondary-button" type="button" onClick={onSecondaryAction}>
            {secondaryActionLabel}
          </button>
        )}
      </div>
    </section>
  )
}

function TripDetailHero({ trip }: { trip: TripDetail }) {
  return (
    <section className="trip-detail-hero">
      <div>
        <p className="trip-eyebrow">Collaborative itinerary workspace</p>
        <h1>{trip.title}</h1>
        <p>
          {trip.destination} 여행의 질문, AI 일정, 지도 마커, 장소 상세 정보, 투표와 채팅을 한 화면에서 다루는
          상세 페이지 구조입니다.
        </p>
      </div>
      <div className="trip-hero-summary" aria-label="여행 요약">
        <SummaryPill label="여행지" value={trip.destination} />
        <SummaryPill label="기간" value={`${formatDate(trip.startDate)} - ${formatDate(trip.endDate)}`} />
        <SummaryPill label="일수" value={durationLabel(trip.startDate, trip.endDate)} />
        <SummaryPill label="상태" value={tripStatusLabel(trip.status)} tone={trip.status.toLowerCase()} />
        <SummaryPill label="참여자" value={`${trip.memberCount}명`} />
      </div>
    </section>
  )
}

function SummaryPill({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div className={`trip-summary-pill ${tone ?? ''}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function PreparationPanel() {
  return (
    <section className="trip-panel trip-preparation-panel" aria-labelledby="preparation-title">
      <PanelHeading eyebrow="Trip questions" title="맞춤 질문" description="AI 일정 생성 전에 필요한 기본 입력값입니다." />
      <div className="question-group-list">
        {QUESTION_GROUPS.map((group) => (
          <article className="question-group-card" key={group.title}>
            <div>
              <h3>{group.title}</h3>
              <p>{group.description}</p>
            </div>
            <div className="question-chip-list" aria-label={`${group.title} 항목`}>
              {group.items.map((item) => (
                <span key={item}>{item}</span>
              ))}
            </div>
          </article>
        ))}
      </div>
      <button className="trip-secondary-button full-width" type="button" disabled>
        질문 저장 API 연결 예정
      </button>
    </section>
  )
}

function PromptPanel() {
  return (
    <section className="trip-panel" aria-labelledby="prompt-title">
      <PanelHeading eyebrow="Direct prompt" title="AI 직접 요청" description="기본 질문 외에 원하는 조건을 직접 추가하는 영역입니다." />
      <textarea
        className="trip-prompt-box"
        placeholder="예: 부모님이 오래 걷기 힘들어서 이동 거리를 줄이고, 바다 전망 카페를 하루에 하나씩 넣어줘."
        disabled
      />
      <div className="prompt-rule-list">
        <span>여행 조건만 허용</span>
        <span>위험/불법 요청 제외</span>
        <span>이동 가능성 검증 필요</span>
      </div>
      <button className="trip-primary-button full-width" type="button" disabled>
        AI 일정 생성 API 연결 예정
      </button>
    </section>
  )
}

function ItineraryMapWorkspace() {
  const [activeDay, setActiveDay] = useState(1)
  const dayPlaces = useMemo(() => MOCK_PLACES.filter((place) => place.day === activeDay), [activeDay])
  const [selectedPlaceId, setSelectedPlaceId] = useState(dayPlaces[0]?.id ?? '')
  const selectedPlace = dayPlaces.find((place) => place.id === selectedPlaceId) ?? dayPlaces[0]

  return (
    <>
      <section className="trip-panel trip-map-panel" aria-labelledby="map-title">
        <div className="map-panel-topline">
          <PanelHeading eyebrow="Map route" title="일차별 지도" description="선택한 일차의 마커와 이동 순서를 표시합니다." />
          <DayTabs activeDay={activeDay} onChange={setActiveDay} />
        </div>
        <div className="map-canvas" aria-label={`${activeDay}일차 지도 미리보기`}>
          <div className="map-route-line" aria-hidden="true" />
          {dayPlaces.map((place) => (
            <button
              className={`map-marker ${selectedPlace?.id === place.id ? 'active' : ''}`}
              key={place.id}
              type="button"
              style={{ left: `${place.x}%`, top: `${place.y}%` }}
              onClick={() => setSelectedPlaceId(place.id)}
              aria-label={`${place.order}번 마커 ${place.title}`}
            >
              <span>{place.order}</span>
            </button>
          ))}
          <div className="map-legend-card">
            <strong>{activeDay}일차 동선</strong>
            <span>지도 SDK 연결 전 UI 구조</span>
          </div>
        </div>
      </section>

      <section className="trip-panel itinerary-panel" aria-labelledby="itinerary-title">
        <PanelHeading eyebrow="AI itinerary" title={`${activeDay}일차 타임라인`} description="AI가 생성할 시간대별 일정 슬롯 구조입니다." />
        <div className="timeline-list">
          {dayPlaces.map((place) => (
            <article className={`timeline-card ${selectedPlace?.id === place.id ? 'active' : ''}`} key={place.id}>
              <span className="timeline-order">{place.order}</span>
              <div>
                <time>{place.time}</time>
                <h3>{place.title}</h3>
                <p>{place.category} · {place.area}</p>
              </div>
              <button className="slot-change-button" type="button" disabled>
                후보 찾기
              </button>
            </article>
          ))}
        </div>
      </section>

      {selectedPlace && <PlaceInsightPanel place={selectedPlace} />}
    </>
  )
}

function DayTabs({ activeDay, onChange }: { activeDay: number; onChange: (day: number) => void }) {
  return (
    <div className="day-tab-list" role="tablist" aria-label="일차 선택">
      {DAY_OPTIONS.map((day) => (
        <button
          className={activeDay === day ? 'active' : ''}
          key={day}
          type="button"
          role="tab"
          aria-selected={activeDay === day}
          onClick={() => onChange(day)}
        >
          {day}일차
        </button>
      ))}
    </div>
  )
}

function PlaceInsightPanel({ place }: { place: MockPlace }) {
  return (
    <section className="trip-panel place-insight-panel" aria-labelledby="place-insight-title">
      <div className="place-insight-heading">
        <div>
          <p className="trip-eyebrow">Marker insight</p>
          <h2 id="place-insight-title">{place.title}</h2>
          <p>{place.category} · {place.area} · {place.time}</p>
        </div>
        <span className="place-category-badge">마커 {place.order}</span>
      </div>
      <div className="place-insight-grid">
        <InsightCard title="운영시간" value={place.hours} />
        <InsightCard title="주차" value={place.parking} />
        <InsightCard title="비용" value={place.price} />
        <InsightCard title="평점/리뷰" value={place.rating} />
        <InsightCard title="후기 요약" value={place.review} />
        <InsightCard title="사진 스팟" value={place.photoTip} />
      </div>
      <div className="place-action-row">
        <button className="trip-secondary-button" type="button" disabled>
          반경 후보 검색 예정
        </button>
        <button className="trip-primary-button" type="button" disabled>
          변경 투표 올리기 예정
        </button>
      </div>
    </section>
  )
}

function InsightCard({ title, value }: { title: string; value: string }) {
  return (
    <article className="insight-card">
      <span>{title}</span>
      <p>{value}</p>
    </article>
  )
}

function MembersPanel({ members, currentUserId }: { members: TripMember[]; currentUserId?: number }) {
  return (
    <section className="trip-panel" aria-labelledby="members-title">
      <PanelHeading eyebrow="Members" title="참여자" description="프로필 이미지는 채팅과 지도 마커에도 재사용됩니다." />
      <div className="member-list">
        {members.map((member) => (
          <article className="member-card" key={member.userId}>
            <MemberAvatar member={member} />
            <div>
              <strong>{member.nickname}{member.userId === currentUserId ? ' · 나' : ''}</strong>
              <span>{member.role === 'OWNER' ? '방장' : '참여자'}</span>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

function MemberAvatar({ member }: { member: TripMember }) {
  return (
    <div className="member-avatar" aria-hidden="true">
      {member.profileImageUrl ? (
        <img src={resolveBackendAssetUrl(member.profileImageUrl)} alt="" />
      ) : (
        <span>{member.nickname.slice(0, 1) || 'P'}</span>
      )}
    </div>
  )
}

function ProposalPanel() {
  return (
    <section className="trip-panel proposal-panel" aria-labelledby="proposal-title">
      <PanelHeading eyebrow="Votes" title="변경 투표" description="참여자가 제안한 일정 변경을 투표로 확정합니다." />
      <article className="proposal-preview-card">
        <span className="proposal-status">OPEN 예정</span>
        <h3>13:00 카페 변경 후보</h3>
        <p>선택한 장소 주변 1km/2km/3km 후보를 받은 뒤 투표 안건으로 올리는 흐름입니다.</p>
        <div className="vote-meter" aria-hidden="true">
          <span style={{ width: '62%' }} />
        </div>
        <div className="vote-actions">
          <button type="button" disabled>찬성</button>
          <button type="button" disabled>반대</button>
        </div>
      </article>
    </section>
  )
}

function ChatPanel({ members }: { members: TripMember[] }) {
  const ownerName = resolveOwnerNickname(members) ?? '방장'

  return (
    <section className="trip-panel chat-panel" aria-labelledby="chat-title">
      <PanelHeading eyebrow="Realtime chat" title="여행방 채팅" description="WebSocket 연결 전 카카오톡방 형태의 UI 자리입니다." />
      <div className="chat-message-list" aria-label="채팅 미리보기">
        <article className="chat-bubble system">여행 상세 페이지가 생성되었습니다.</article>
        <article className="chat-bubble">
          <strong>{ownerName}</strong>
          <p>AI 일정이 나오면 여기서 장소 변경 의견을 모으면 됩니다.</p>
        </article>
        <article className="chat-bubble event">투표와 일정 변경 이벤트도 같은 흐름에 표시됩니다.</article>
      </div>
      <div className="chat-input-row">
        <input type="text" placeholder="채팅 API 연결 예정" disabled />
        <button type="button" disabled>전송</button>
      </div>
    </section>
  )
}

function PanelHeading({ eyebrow, title, description }: { eyebrow: string; title: string; description: string }) {
  return (
    <div className="trip-panel-heading">
      <p className="trip-eyebrow">{eyebrow}</p>
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  )
}

function resolveOwnerNickname(members?: TripMember[]) {
  return members?.find((member) => member.role === 'OWNER')?.nickname ?? members?.[0]?.nickname
}

function tripStatusLabel(status: TripStatus) {
  const labels: Record<TripStatus, string> = {
    PLANNING: '계획중',
    UPCOMING: '예정',
    COMPLETED: '완료',
  }
  return labels[status]
}

function formatDate(value: string) {
  return value.replaceAll('-', '.')
}

function durationLabel(startDate: string, endDate: string) {
  const start = new Date(`${startDate}T00:00:00`)
  const end = new Date(`${endDate}T00:00:00`)
  const diff = Math.floor((end.getTime() - start.getTime()) / 86_400_000) + 1

  if (Number.isNaN(diff) || diff <= 0) {
    return '기간 확인 필요'
  }
  return `${diff}일`
}

function resolveBackendAssetUrl(path: string) {
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path
  }
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`
}

function toUserMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return '로그인이 만료되었습니다. 다시 로그인해 주세요.'
    }
    if (error.status === 404) {
      return '여행을 찾을 수 없거나 접근 권한이 없습니다.'
    }
    return error.message
  }
  return '요청 처리 중 오류가 발생했습니다.'
}
