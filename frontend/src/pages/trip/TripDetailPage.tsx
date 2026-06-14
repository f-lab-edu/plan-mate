import { useEffect, useMemo, useRef, useState } from 'react'
import type { AuthUser } from '../../api/auth'
import { API_BASE_URL, ApiError } from '../../api/client'
import { getTripDetail } from '../../api/trips'
import type { BudgetSummary, ItineraryDay, ItineraryItem, TripDetail, TripMember } from '../../api/trips'
import './TripDetailPage.css'

type TripDetailPageProps = {
  accessToken: string
  tripId: string
  user: AuthUser | null
  onBackToMain: () => void
  onLogout: () => void
}

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'

type VoteProposal = {
  title: string
  description: string
  agreeCount: number
  disagreeCount: number
}

type GoogleMapsLike = {
  maps: {
    Map: new (element: HTMLElement, options: Record<string, unknown>) => GoogleMapInstance
    Marker: new (options: Record<string, unknown>) => GoogleMarkerInstance
    Polyline: new (options: Record<string, unknown>) => unknown
    LatLngBounds: new () => GoogleBoundsInstance
    Size: new (width: number, height: number) => unknown
    SymbolPath: { CIRCLE: unknown }
  }
}

type GoogleMapInstance = {
  fitBounds: (bounds: GoogleBoundsInstance, padding?: number) => void
  setCenter: (position: MapPoint) => void
  setZoom: (zoom: number) => void
}

type GoogleMarkerInstance = {
  addListener: (eventName: string, handler: () => void) => void
}

type GoogleBoundsInstance = {
  extend: (position: MapPoint) => void
}

type MapPoint = {
  lat: number
  lng: number
}

declare global {
  interface Window {
    google?: GoogleMapsLike
    initPlanMateGoogleMaps?: () => void
  }
}

const GOOGLE_MAPS_API_KEY = import.meta.env.VITE_GOOGLE_MAPS_API_KEY ?? ''
let googleMapsScriptPromise: Promise<void> | null = null

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

  return (
    <main className="trip-detail-page">
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
          description={status === 'error' ? errorMessage : '여행 기본 정보와 AI 생성 일정을 조회하고 있습니다.'}
          actionLabel={status === 'error' ? '다시 시도' : undefined}
          onAction={status === 'error' ? () => setReloadKey((value) => value + 1) : undefined}
          secondaryActionLabel="메인으로 돌아가기"
          onSecondaryAction={onBackToMain}
        />
      )}

      {accessToken && status === 'success' && trip && (
        <TripPlanningWorkspace
          trip={trip}
          currentUser={user}
          onBackToMain={onBackToMain}
          onLogout={onLogout}
        />
      )}
    </main>
  )
}

function TripPlanningWorkspace({
  trip,
  currentUser,
  onBackToMain,
  onLogout,
}: {
  trip: TripDetail
  currentUser: AuthUser | null
  onBackToMain: () => void
  onLogout: () => void
}) {
  const days = useMemo(() => trip.itinerary?.days ?? [], [trip.itinerary])
  const firstDay = days[0]?.day ?? 1
  const [activeDay, setActiveDay] = useState(firstDay)
  const [selectedPlaceId, setSelectedPlaceId] = useState('')
  const [editingPlaceId, setEditingPlaceId] = useState<string | null>(null)
  const resolvedActiveDay = days.some((day) => day.day === activeDay) ? activeDay : firstDay

  const activeDayPlan = useMemo(
    () => days.find((day) => day.day === resolvedActiveDay) ?? days[0] ?? null,
    [resolvedActiveDay, days],
  )
  const activeItems = useMemo(() => activeDayPlan?.items ?? [], [activeDayPlan])
  const fallbackSelectedPlace = activeItems.find(isMapVisibleItem) ?? activeItems[0] ?? null
  const selectedPlace = activeItems.find((place) => place.id === selectedPlaceId) ?? fallbackSelectedPlace
  const effectiveSelectedPlaceId = selectedPlace?.id ?? ''
  const editingPlace = activeItems.find((place) => place.id === editingPlaceId) ?? null
  const activeVote: VoteProposal | null = null
  const requiresInitialSetup = false

  function handleDayChange(day: number) {
    const nextDay = days.find((candidate) => candidate.day === day)
    const nextPlace = nextDay?.items.find(isMapVisibleItem) ?? nextDay?.items[0]
    setActiveDay(day)
    setSelectedPlaceId(nextPlace?.id ?? '')
    setEditingPlaceId(null)
  }

  return (
    <section className="planning-board" aria-label="여행 상세 플래닝 보드">
      <PlanningHeader trip={trip} members={trip.members} onBackToMain={onBackToMain} onLogout={onLogout} />
      <div className="planning-layout">
        <ItinerarySidebar
          days={days}
          activeDay={resolvedActiveDay}
          activeDayPlan={activeDayPlan}
          items={activeItems}
          selectedPlaceId={effectiveSelectedPlaceId}
          budgetSummary={trip.itinerary?.budgetSummary ?? null}
          onDayChange={handleDayChange}
          onSelectPlace={setSelectedPlaceId}
        />
        <MapStage
          activeDay={resolvedActiveDay}
          items={activeItems}
          selectedPlace={selectedPlace}
          editingPlace={editingPlace}
          onSelectPlace={setSelectedPlaceId}
          onClosePlace={() => setSelectedPlaceId('')}
          onOpenAiEdit={(placeId) => setEditingPlaceId(placeId)}
          onCloseAiEdit={() => setEditingPlaceId(null)}
          activeVote={activeVote}
        />
        <TripChatPanel members={trip.members} currentUser={currentUser} />
      </div>
      {trip.itinerary && <VerificationWarnings warnings={trip.itinerary.verificationWarnings} />}
      {requiresInitialSetup && <InitialSetupFloatingPanel />}
    </section>
  )
}

function PlanningHeader({
  trip,
  members,
  onBackToMain,
  onLogout,
}: {
  trip: TripDetail
  members: TripMember[]
  onBackToMain: () => void
  onLogout: () => void
}) {
  return (
    <header className="planning-header">
      <button className="icon-back-button" type="button" onClick={onBackToMain} aria-label="메인으로 돌아가기">
        <span aria-hidden="true">←</span>
      </button>
      <div className="planning-title-block">
        <span className="planning-kicker">PlanMate itinerary</span>
        <h1>{trip.title}</h1>
        <p>{formatDate(trip.startDate)} - {formatDate(trip.endDate)} · {trip.destination}</p>
      </div>
      <div className="planning-header-actions">
        <div className="member-avatar-stack" aria-label="참여자 목록">
          <span className="member-count-icon" aria-hidden="true">팀</span>
          {members.slice(0, 3).map((member) => (
            <MemberAvatar member={member} key={member.userId} />
          ))}
          <span className="member-total">{members.length}명</span>
        </div>
        <button className="outline-action" type="button" disabled>
          공유
        </button>
        <button className="solid-action" type="button" disabled>
          저장
        </button>
        <button className="logout-text-button" type="button" onClick={onLogout}>
          로그아웃
        </button>
      </div>
    </header>
  )
}

function ItinerarySidebar({
  days,
  activeDay,
  activeDayPlan,
  items,
  selectedPlaceId,
  budgetSummary,
  onDayChange,
  onSelectPlace,
}: {
  days: ItineraryDay[]
  activeDay: number
  activeDayPlan: ItineraryDay | null
  items: ItineraryItem[]
  selectedPlaceId: string
  budgetSummary: BudgetSummary | null
  onDayChange: (day: number) => void
  onSelectPlace: (placeId: string) => void
}) {
  const visibleItems = items.filter(isMapVisibleItem)
  const transportMinutes = items.reduce((sum, item) => sum + item.transport.fromPreviousMinutes, 0)

  return (
    <aside className="itinerary-sidebar" aria-label="일차별 일정 목록">
      <div className="sidebar-heading">
        <span>AI route</span>
        <h2>{activeDay}일차 동선</h2>
        <p>{activeDayPlan?.theme ?? '선택한 샘플 일정이 없습니다.'}</p>
      </div>
      <div className="day-tab-list" role="tablist" aria-label="일차 선택">
        {days.map((day) => (
          <button
            className={activeDay === day.day ? 'active' : ''}
            key={day.day}
            type="button"
            role="tab"
            aria-selected={activeDay === day.day}
            onClick={() => onDayChange(day.day)}
          >
            {day.day}일차
          </button>
        ))}
      </div>
      <button className="add-place-button" type="button" disabled>
        + 장소 추가
      </button>
      <div className="route-summary-card">
        <span>예상 이동</span>
        <strong>{visibleItems.length}개 마커 · 약 {transportMinutes}분</strong>
        <p>현재는 샘플 좌표를 직선으로 연결합니다. 실제 도로 경로는 Routes API 연동 후 교체합니다.</p>
      </div>
      {budgetSummary && (
        <div className="route-summary-card budget-summary-card">
          <span>예상 예산</span>
          <strong>{formatMoneyRange(budgetSummary.totalMin, budgetSummary.totalMax)}</strong>
          <p>1인 기준 {formatMoneyRange(budgetSummary.perPersonMin, budgetSummary.perPersonMax)}</p>
        </div>
      )}
      <div className="itinerary-card-list">
        {items.map((item) => (
          <button
            className={`itinerary-card ${selectedPlaceId === item.id ? 'active' : ''}`}
            key={item.id}
            type="button"
            onClick={() => onSelectPlace(item.id)}
          >
            <span className="itinerary-order">{item.order}</span>
            <span className="itinerary-content">
              <strong>{item.placeName}</strong>
              <span className="itinerary-time">{item.startTime} - {item.endTime} · {durationLabel(item.startTime, item.endTime)}</span>
              <span className="category-chip">{item.category}</span>
              {item.needsVerification && <span className="itinerary-memo">방문 전 확인 필요</span>}
            </span>
          </button>
        ))}
      </div>
    </aside>
  )
}

function MapStage({
  activeDay,
  items,
  selectedPlace,
  editingPlace,
  onSelectPlace,
  onClosePlace,
  onOpenAiEdit,
  onCloseAiEdit,
  activeVote,
}: {
  activeDay: number
  items: ItineraryItem[]
  selectedPlace: ItineraryItem | null
  editingPlace: ItineraryItem | null
  onSelectPlace: (placeId: string) => void
  onClosePlace: () => void
  onOpenAiEdit: (placeId: string) => void
  onCloseAiEdit: () => void
  activeVote: VoteProposal | null
}) {
  const mapItems = useMemo(() => items.filter(isMapVisibleItem), [items])

  return (
    <section className="map-stage" aria-label={`${activeDay}일차 지도`}>
      <GoogleRouteMap items={mapItems} selectedPlaceId={selectedPlace?.id ?? ''} onSelectPlace={onSelectPlace} />
      <div className="map-status-pill">
        <span>{activeDay}일차</span>
        <strong>{mapItems.length}개 장소 표시 중</strong>
      </div>
      <div className="map-layer-selector" aria-label="지도 레이어">
        <button type="button" disabled>일정</button>
        <button type="button" disabled>주차</button>
        <button type="button" disabled>혼잡</button>
      </div>
      {selectedPlace && (
        <PlaceFloatingCard
          place={selectedPlace}
          onClose={onClosePlace}
          onOpenAiEdit={() => onOpenAiEdit(selectedPlace.id)}
        />
      )}
      {editingPlace && <AiEditFloatingPanel place={editingPlace} onClose={onCloseAiEdit} />}
      {activeVote && <VoteFloatingPanel proposal={activeVote} />}
    </section>
  )
}

function GoogleRouteMap({
  items,
  selectedPlaceId,
  onSelectPlace,
}: {
  items: ItineraryItem[]
  selectedPlaceId: string
  onSelectPlace: (placeId: string) => void
}) {
  const mapRef = useRef<HTMLDivElement | null>(null)
  const [mapStatus, setMapStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle')

  useEffect(() => {
    const element = mapRef.current
    if (!element || items.length === 0 || !GOOGLE_MAPS_API_KEY) {
      return
    }

    let cancelled = false
    setMapStatus('loading')

    loadGoogleMapsScript(GOOGLE_MAPS_API_KEY)
      .then(() => {
        if (cancelled || !window.google || !element) {
          return
        }

        const maps = window.google.maps
        const first = toMapPoint(items[0])
        const map = new maps.Map(element, {
          center: first,
          zoom: 13,
          disableDefaultUI: true,
          zoomControl: true,
          mapId: import.meta.env.VITE_GOOGLE_MAP_ID || undefined,
        })
        const bounds = new maps.LatLngBounds()
        const path = items.map(toMapPoint)

        path.forEach((point) => bounds.extend(point))

        new maps.Polyline({
          path,
          geodesic: true,
          strokeColor: '#1b4e42',
          strokeOpacity: 0.88,
          strokeWeight: 5,
          map,
        })

        items.forEach((item) => {
          const active = item.id === selectedPlaceId
          const marker = new maps.Marker({
            position: toMapPoint(item),
            map,
            title: item.placeName,
            label: {
              text: String(item.order),
              color: active ? '#ffffff' : '#1b4e42',
              fontWeight: '900',
            },
            icon: {
              path: maps.SymbolPath.CIRCLE,
              scale: active ? 14 : 11,
              fillColor: active ? '#e9672b' : '#fff8e7',
              fillOpacity: 1,
              strokeColor: active ? '#fff8e7' : '#1b4e42',
              strokeWeight: 3,
            },
          })
          marker.addListener('click', () => onSelectPlace(item.id))
        })

        if (items.length === 1) {
          map.setCenter(first)
          map.setZoom(14)
        } else {
          map.fitBounds(bounds, 76)
        }
        setMapStatus('ready')
      })
      .catch(() => {
        if (!cancelled) {
          setMapStatus('error')
        }
      })

    return () => {
      cancelled = true
    }
  }, [items, onSelectPlace, selectedPlaceId])

  if (!GOOGLE_MAPS_API_KEY) {
    return (
      <div className="google-map-fallback">
        <strong>Google Maps API 키가 필요합니다.</strong>
        <p>frontend/.env.local에 VITE_GOOGLE_MAPS_API_KEY를 추가한 뒤 프론트 dev 서버를 재시작하세요.</p>
      </div>
    )
  }

  if (items.length === 0) {
    return (
      <div className="google-map-fallback">
        <strong>표시할 지도 좌표가 없습니다.</strong>
        <p>이동 항목만 있는 일정은 마커와 동선에서 제외됩니다.</p>
      </div>
    )
  }

  return (
    <>
      <div className="google-map-canvas" ref={mapRef} aria-label="Google 지도" />
      {mapStatus === 'loading' && <div className="google-map-loading">지도를 불러오는 중입니다.</div>}
      {mapStatus === 'error' && <div className="google-map-loading error">Google 지도를 불러오지 못했습니다.</div>}
    </>
  )
}

function PlaceFloatingCard({
  place,
  onClose,
  onOpenAiEdit,
}: {
  place: ItineraryItem
  onClose: () => void
  onOpenAiEdit: () => void
}) {
  return (
    <article className="place-floating-card api-place-card">
      <button className="floating-close" type="button" onClick={onClose} aria-label="장소 정보 닫기">×</button>
      <span className="place-index">{place.order}번째 장소</span>
      <h2>{place.placeName}</h2>
      <p>{place.category} · {place.startTime} - {place.endTime} · {place.areaHint}</p>
      <dl className="place-info-list">
        <div>
          <dt>운영시간</dt>
          <dd>{place.needsVerification ? '방문 전 확인 필요' : '샘플 데이터 기준 별도 확인 없음'}</dd>
        </div>
        <div>
          <dt>주차</dt>
          <dd>{place.parking.required ? place.parking.notes : '주차 필요 없음'}</dd>
        </div>
        <div>
          <dt>비용</dt>
          <dd>{formatMoneyRange(place.estimatedCost.min, place.estimatedCost.max)} {formatIncluded(place.estimatedCost.included)}</dd>
        </div>
        <div>
          <dt>이동</dt>
          <dd>{place.transport.mode} · 이전 일정에서 약 {place.transport.fromPreviousMinutes}분</dd>
        </div>
        <div>
          <dt>추천 이유</dt>
          <dd>{place.whyRecommended}</dd>
        </div>
        <div>
          <dt>장소 요약</dt>
          <dd>{place.description}</dd>
        </div>
      </dl>
      <button className="ai-edit-open-button" type="button" onClick={onOpenAiEdit}>
        이 일정 AI로 수정 요청
      </button>
    </article>
  )
}

function AiEditFloatingPanel({ place, onClose }: { place: ItineraryItem; onClose: () => void }) {
  return (
    <section className="ai-edit-floating-panel" aria-label="AI 일정 수정 요청">
      <button className="floating-close" type="button" onClick={onClose} aria-label="AI 수정 요청 닫기">×</button>
      <p className="floating-eyebrow">AI edit request</p>
      <h2>{place.placeName} 수정 요청</h2>
      <p>AI가 짜준 일정 중 선택한 슬롯을 어떻게 바꿀지 입력하는 영역입니다.</p>
      <textarea placeholder="예: 이 장소 대신 반경 2km 안에서 주차 가능한 카페 3개 추천해줘." disabled />
      <div className="radius-chip-row">
        <span>1km</span>
        <span>2km</span>
        <span>3km</span>
      </div>
      <button type="button" disabled>후보 추천 API 연결 예정</button>
    </section>
  )
}

function VoteFloatingPanel({ proposal }: { proposal: VoteProposal }) {
  return (
    <section className="vote-floating-panel" aria-label="변경 투표">
      <p className="floating-eyebrow">Vote</p>
      <h2>{proposal.title}</h2>
      <p>{proposal.description}</p>
      <div className="vote-result-row">
        <span>찬성 {proposal.agreeCount}</span>
        <span>반대 {proposal.disagreeCount}</span>
      </div>
      <div className="vote-button-row">
        <button type="button" disabled>찬성</button>
        <button type="button" disabled>반대</button>
      </div>
    </section>
  )
}

function TripChatPanel({
  members,
  currentUser,
}: {
  members: TripMember[]
  currentUser: AuthUser | null
}) {
  return (
    <aside className="chat-dock" aria-label="팀 채팅">
      <header className="chat-header">
        <div>
          <span aria-hidden="true">팀</span>
          <h2>팀 채팅</h2>
        </div>
        <button type="button" disabled>나가기</button>
      </header>
      <div className="online-member-row" aria-label="현재 접속한 유저">
        {members.map((member) => (
          <MemberAvatar member={member} key={member.userId} />
        ))}
        {currentUser && <span className="current-user-label">{currentUser.nickname} 접속 중</span>}
      </div>
      <div className="chat-message-list pending-chat-list">
        <article className="chat-message">
          <span className="chat-avatar">PM</span>
          <div>
            <p className="chat-meta"><strong>PlanMate</strong><time>준비 중</time></p>
            <p className="chat-bubble">실시간 채팅과 변경 투표는 다음 단계에서 연결합니다.</p>
          </div>
        </article>
      </div>
      <form className="chat-input-row">
        <input type="text" placeholder="메시지를 입력하세요..." disabled />
        <button type="button" disabled aria-label="메시지 전송">전송</button>
      </form>
    </aside>
  )
}

function VerificationWarnings({ warnings }: { warnings: string[] }) {
  if (warnings.length === 0) {
    return null
  }

  return (
    <aside className="verification-warning-strip" aria-label="검증 필요 안내">
      {warnings.slice(0, 4).map((warning) => (
        <span key={warning}>{warning}</span>
      ))}
    </aside>
  )
}

function InitialSetupFloatingPanel() {
  return (
    <section className="initial-setup-floating-panel" aria-label="방 생성자 맞춤 질문">
      <p className="floating-eyebrow">Initial setup</p>
      <h2>방 생성자 기본 질문</h2>
      <p>이 영역은 첫 방 생성 시에만 노출됩니다. 상세페이지 기본 화면에는 상시 노출하지 않습니다.</p>
    </section>
  )
}

function MemberAvatar({ member }: { member: TripMember }) {
  return (
    <span className="member-avatar" aria-label={member.nickname}>
      {member.profileImageUrl ? <img src={resolveBackendAssetUrl(member.profileImageUrl)} alt="" /> : member.nickname.slice(0, 1)}
    </span>
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
      <h1>{title}</h1>
      <p>{description}</p>
      <div className="trip-state-actions">
        {actionLabel && onAction && <button type="button" onClick={onAction}>{actionLabel}</button>}
        {secondaryActionLabel && onSecondaryAction && <button type="button" onClick={onSecondaryAction}>{secondaryActionLabel}</button>}
      </div>
    </section>
  )
}

function loadGoogleMapsScript(apiKey: string) {
  if (window.google?.maps) {
    return Promise.resolve()
  }
  if (googleMapsScriptPromise) {
    return googleMapsScriptPromise
  }

  googleMapsScriptPromise = new Promise<void>((resolve, reject) => {
    window.initPlanMateGoogleMaps = () => resolve()
    const script = document.createElement('script')
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}&callback=initPlanMateGoogleMaps&v=weekly`
    script.async = true
    script.defer = true
    script.dataset.planmateGoogleMaps = 'true'
    script.onerror = () => reject(new Error('Google Maps script load failed'))
    document.head.appendChild(script)
  })

  return googleMapsScriptPromise
}

function isMapVisibleItem(item: ItineraryItem) {
  return item.mapVisible && item.lat !== null && item.lng !== null
}

function toMapPoint(item: ItineraryItem): MapPoint {
  return { lat: item.lat ?? 0, lng: item.lng ?? 0 }
}

function formatDate(value: string) {
  return value.replaceAll('-', '.')
}

function durationLabel(startTime: string, endTime: string) {
  const startMinutes = toMinutes(startTime)
  const endMinutes = toMinutes(endTime)
  if (startMinutes === null || endMinutes === null || endMinutes <= startMinutes) {
    return '시간 확인 필요'
  }

  const diff = endMinutes - startMinutes
  const hours = Math.floor(diff / 60)
  const minutes = diff % 60
  if (hours > 0 && minutes > 0) {
    return `${hours}시간 ${minutes}분`
  }
  if (hours > 0) {
    return `${hours}시간`
  }
  return `${minutes}분`
}

function toMinutes(value: string) {
  const [hour, minute] = value.split(':').map(Number)
  if (Number.isNaN(hour) || Number.isNaN(minute)) {
    return null
  }
  return hour * 60 + minute
}

function formatMoneyRange(min: number, max: number) {
  if (min === 0 && max === 0) {
    return '무료 또는 별도 비용 없음'
  }
  return `${min.toLocaleString('ko-KR')}원 - ${max.toLocaleString('ko-KR')}원`
}

function formatIncluded(included: string[]) {
  if (included.length === 0) {
    return ''
  }
  return `(${included.join(', ')})`
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
