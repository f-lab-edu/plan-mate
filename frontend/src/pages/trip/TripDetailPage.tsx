import { useEffect, useMemo, useState } from 'react'
import type { AuthUser } from '../../api/auth'
import { ApiError } from '../../api/client'
import { createItineraryGeneration, getItineraryPlaceViews, getLatestItineraryGeneration, getTripDetail } from '../../api/trips'
import type { ItineraryPlaceView, ItineraryGenerationDetailResponse, TripDetail, TripMember, TripPlanningProfile } from '../../api/trips'
import { connectTripRealtimeEvents, ITINERARY_GENERATION_STATUS_CHANGED } from '../../api/realtime'
import './TripDetailPage.css'

type TripDetailPageProps = {
  accessToken: string
  tripId: string
  user: AuthUser | null
  onBackToMain: () => void
  onLogout: () => void
}

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'

type ItineraryPlace = {
  id: string
  day: number
  order: number
  title: string
  category: string
  time: string
  duration: string
  memo?: string
  x: number
  y: number
  hours: string
  parking: string
  price: string
  rating: string
  contentSummary: string
  photoTip: string
}

export function TripDetailPage({
  accessToken,
  tripId,
  user,
  onBackToMain,
  onLogout,
}: TripDetailPageProps) {
  const [trip, setTrip] = useState<TripDetail | null>(null)
  const [placeViews, setPlaceViews] = useState<ItineraryPlaceView[]>([])
  const [latestGeneration, setLatestGeneration] = useState<ItineraryGenerationDetailResponse | null>(null)
  const [status, setStatus] = useState<AsyncStatus>('idle')
  const [errorMessage, setErrorMessage] = useState('')
  const [generationActionStatus, setGenerationActionStatus] = useState<AsyncStatus>('idle')
  const [generationActionMessage, setGenerationActionMessage] = useState('')

  useEffect(() => {
    let active = true

    async function loadTrip() {
      setStatus('loading')
      setErrorMessage('')
      try {
        const [response, generation, views] = await Promise.all([
          getTripDetail(accessToken, tripId),
          getLatestItineraryGeneration(accessToken, tripId),
          getItineraryPlaceViews(accessToken, tripId),
        ])
        if (!active) {
          return
        }
        setTrip(response)
        setPlaceViews(views)
        setLatestGeneration(generation)
        setStatus('success')
      } catch (error: unknown) {
        if (!active) {
          return
        }
        setStatus('error')
        setErrorMessage(errorMessageFrom(error))
      }
    }

    void loadTrip()
    return () => {
      active = false
    }
  }, [accessToken, tripId])

  useEffect(() => {
    if (!accessToken || !tripId) {
      return undefined
    }

    let active = true

    async function refetchLatestGeneration() {
      try {
        const generation = await getLatestItineraryGeneration(accessToken, tripId)
        if (active) {
          setLatestGeneration(generation)
        }
      } catch (error: unknown) {
        if (active) {
          setGenerationActionStatus('error')
          setGenerationActionMessage(errorMessageFrom(error))
        }
      }
    }

    async function refetchTrip() {
      const [response, views] = await Promise.all([
        getTripDetail(accessToken, tripId),
        getItineraryPlaceViews(accessToken, tripId),
      ])
      if (active) {
        setTrip(response)
        setPlaceViews(views)
        setStatus('success')
      }
    }

    const connection = connectTripRealtimeEvents({
      accessToken,
      tripId,
      onConnect: () => {
        void refetchLatestGeneration()
      },
      onError: (message) => {
        if (active) {
          setGenerationActionMessage(message)
        }
      },
      onEvent: (event) => {
        if (event.type !== ITINERARY_GENERATION_STATUS_CHANGED) {
          return
        }
        void refetchLatestGeneration()
        setLatestGeneration((current) => {
          if (!current || current.generationId !== event.payload.generationId) {
            return current
          }
          return {
            ...current,
            status: event.payload.status,
            candidateCount: event.payload.candidateCount,
            failureReason: event.payload.failureReason,
            updatedAt: event.payload.updatedAt,
          }
        })
        if (event.payload.status === 'READY_FOR_PLANNING') {
          setGenerationActionStatus('success')
          setGenerationActionMessage('일정 생성이 진행 중입니다.')
        }
        if (event.payload.status === 'FAILED') {
          setGenerationActionStatus('error')
          setGenerationActionMessage(event.payload.failureReason ?? '일정 생성에 실패했습니다.')
        }
        if (event.payload.status === 'COMPLETED') {
          setGenerationActionStatus('success')
          setGenerationActionMessage('일정 생성이 완료되었습니다.')
          void refetchTrip().catch((error: unknown) => {
            if (active) {
              setGenerationActionStatus('error')
              setGenerationActionMessage(errorMessageFrom(error))
            }
          })
        }
      },
    })

    return () => {
      active = false
      connection.disconnect()
    }
  }, [accessToken, tripId])

  async function handleRetryGeneration() {
    setGenerationActionStatus('loading')
    setGenerationActionMessage('')
    try {
      const generation = await createItineraryGeneration(accessToken, tripId, true)
      setLatestGeneration({
        ...generation,
        tripId,
        promptVersion: '',
        failureReason: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      })
      setGenerationActionStatus('success')
      setGenerationActionMessage('일정 재생성 요청을 접수했습니다.')
    } catch (error: unknown) {
      setGenerationActionStatus('error')
      setGenerationActionMessage(errorMessageFrom(error))
    }
  }

  if (status === 'loading' || status === 'idle') {
    return (
      <main className="trip-detail-page">
        <section className="trip-detail-loading">
          <p>여행 정보를 불러오고 있습니다.</p>
        </section>
      </main>
    )
  }

  if (status === 'error' || !trip) {
    return (
      <main className="trip-detail-page">
        <section className="trip-detail-error">
          <h1>여행 정보를 불러오지 못했습니다.</h1>
          <p>{errorMessage}</p>
          <button type="button" onClick={onBackToMain}>메인으로 돌아가기</button>
        </section>
      </main>
    )
  }

  return (
    <main className="trip-detail-page">
      <TripPlanningWorkspace
        currentUser={user}
        onBackToMain={onBackToMain}
        onLogout={onLogout}
        latestGeneration={latestGeneration}
        generationActionStatus={generationActionStatus}
        generationActionMessage={generationActionMessage}
        onRetryGeneration={handleRetryGeneration}
        placeViews={placeViews}
        trip={trip}
      />
    </main>
  )
}

function TripPlanningWorkspace({
  trip,
  currentUser,
  latestGeneration,
  generationActionStatus,
  generationActionMessage,
  onBackToMain,
  onLogout,
  onRetryGeneration,
  placeViews,
}: {
  trip: TripDetail
  placeViews: ItineraryPlaceView[]
  currentUser: AuthUser | null
  latestGeneration: ItineraryGenerationDetailResponse | null
  generationActionStatus: AsyncStatus
  generationActionMessage: string
  onBackToMain: () => void
  onLogout: () => void
  onRetryGeneration: () => void
}) {
  const latestItinerary = trip.itineraries[0] ?? null
  const dayOptions = latestItinerary?.days.map((day) => day.day) ?? []
  const [activeDay, setActiveDay] = useState(1)
  const [selectedPlaceId, setSelectedPlaceId] = useState('')
  const resolvedActiveDay = dayOptions.includes(activeDay) ? activeDay : dayOptions[0] ?? 1

  const allPlaces = useMemo(() => toItineraryPlaces(placeViews), [placeViews])
  const activePlaces = useMemo(() => allPlaces.filter((place) => place.day === resolvedActiveDay), [resolvedActiveDay, allPlaces])
  const selectedPlace = activePlaces.find((place) => place.id === selectedPlaceId) ?? activePlaces[0] ?? null

  function handleDayChange(day: number) {
    const firstPlace = allPlaces.find((place) => place.day === day)
    setActiveDay(day)
    setSelectedPlaceId(firstPlace?.id ?? '')
  }

  return (
    <section className="planning-board" aria-label="여행 상세 플래닝 보드">
      <PlanningHeader trip={trip} members={trip.members} onBackToMain={onBackToMain} onLogout={onLogout} />
      {trip.planningProfile && <PlanningProfileSummary profile={trip.planningProfile} />}
      {latestItinerary ? (
        <div className="planning-layout">
          <ItinerarySidebar
            activeDay={resolvedActiveDay}
            dayOptions={dayOptions}
            places={activePlaces}
            selectedPlaceId={selectedPlace?.id ?? ''}
            onDayChange={handleDayChange}
            onSelectPlace={setSelectedPlaceId}
          />
          <MapStage
            activeDay={resolvedActiveDay}
            places={activePlaces}
            selectedPlace={selectedPlace}
            onSelectPlace={setSelectedPlaceId}
            onClosePlace={() => setSelectedPlaceId('')}
          />
          <TripChatPanel members={trip.members} currentUser={currentUser} />
        </div>
      ) : (
        <section className="planning-empty-state">
          <TripGenerationRecoveryPanel
            generation={latestGeneration}
            generationActionMessage={generationActionMessage}
            generationActionStatus={generationActionStatus}
            onRetryGeneration={onRetryGeneration}
          />
          <h2>저장된 일정이 아직 없습니다.</h2>
          <p>AI가 일정을 생성하는 중이면 완료 후 자동으로 일정 목록이 표시됩니다.</p>
        </section>
      )}
    </section>
  )
}

function TripGenerationRecoveryPanel({
  generation,
  generationActionMessage,
  generationActionStatus,
  onRetryGeneration,
}: {
  generation: ItineraryGenerationDetailResponse | null
  generationActionMessage: string
  generationActionStatus: AsyncStatus
  onRetryGeneration: () => void
}) {
  if (!generation) {
    return null
  }

  const isFailed = generation.status === 'FAILED'
  const statusMessage = isFailed
    ? generation.failureReason ?? '일정 생성에 실패했습니다.'
    : generation.status === 'COMPLETED'
      ? '일정 생성이 완료되었습니다.'
      : 'AI가 장소와 동선을 검토해 일정을 구성하고 있습니다.'

  return (
    <section className="trip-generation-recovery" aria-live="polite">
      <div>
        <span>Generation</span>
        <strong>{generation.status}</strong>
        <p>{statusMessage}</p>
      </div>
      <div className="trip-generation-actions">
        <button type="button" onClick={onRetryGeneration} disabled={!isFailed || generationActionStatus === 'loading'}>
          다시 생성
        </button>
      </div>
      {generationActionMessage && <p className={`trip-generation-message ${generationActionStatus}`}>{generationActionMessage}</p>}
    </section>
  )
}

function PlanningProfileSummary({ profile }: { profile: TripPlanningProfile }) {
  const accommodationTitle = profile.accommodationMode === 'PLACE_SEARCH'
    ? profile.accommodationName ?? '선택된 숙소'
    : '숙소 미정'
  const accommodationDescription = profile.accommodationMode === 'PLACE_SEARCH'
    ? profile.accommodationFormattedAddress ?? 'Google Places에서 선택한 숙소'
    : accommodationAreaLabel(profile.accommodationArea)

  return (
    <section className="planning-profile-summary" aria-label="저장된 여행 설정">
      <article>
        <span>숙소</span>
        <strong>{accommodationTitle}</strong>
        <p>{accommodationDescription}</p>
      </article>
      <article>
        <span>하루 일정 시간</span>
        <strong>{formatTime(profile.dailyStartTime)} ~ {formatTime(profile.dailyEndTime)}</strong>
        <p>일정을 배치할 수 있는 하루 기준 시간입니다.</p>
      </article>
      <article>
        <span>꼭 가고 싶은 곳</span>
        <strong>{profile.mustVisitPlaces.length}개 선택</strong>
        <p>{profile.mustVisitPlaces.map((place) => place.name).join(' · ') || '선택한 장소 없음'}</p>
      </article>
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
        <p>{formatDate(trip.startDate)} - {formatDate(trip.endDate)}</p>
      </div>
      <div className="planning-header-actions">
        <div className="member-avatar-stack" aria-label="참여자 목록">
          <span className="member-count-icon" aria-hidden="true">팀</span>
          {members.slice(0, 3).map((member) => (
            <MemberAvatar member={member} key={member.userId} />
          ))}
          <span className="member-total">{members.length}명</span>
        </div>
        <button className="outline-action" type="button" disabled>초대 준비 중</button>
        <button className="ghost-action" type="button" onClick={onLogout}>로그아웃</button>
      </div>
    </header>
  )
}

function ItinerarySidebar({
  activeDay,
  dayOptions,
  places,
  selectedPlaceId,
  onDayChange,
  onSelectPlace,
}: {
  activeDay: number
  dayOptions: number[]
  places: ItineraryPlace[]
  selectedPlaceId: string
  onDayChange: (day: number) => void
  onSelectPlace: (placeId: string) => void
}) {
  return (
    <aside className="itinerary-sidebar">
      <div className="sidebar-heading">
        <span>Itinerary</span>
        <h2>Day {activeDay}</h2>
      </div>
      <div className="day-tabs" role="tablist" aria-label="일자 선택">
        {dayOptions.map((day) => (
          <button
            className={day === activeDay ? 'active' : ''}
            key={day}
            type="button"
            onClick={() => onDayChange(day)}
          >
            Day {day}
          </button>
        ))}
      </div>
      <ol className="place-timeline">
        {places.map((place) => (
          <li key={place.id}>
            <button
              className={place.id === selectedPlaceId ? 'active' : ''}
              type="button"
              onClick={() => onSelectPlace(place.id)}
            >
              <span className="timeline-order">{place.order}</span>
              <div>
                <strong>{place.title}</strong>
                <small>{place.time} · {place.duration}</small>
                {place.memo && <p>{place.memo}</p>}
              </div>
            </button>
          </li>
        ))}
      </ol>
    </aside>
  )
}

function MapStage({
  activeDay,
  places,
  selectedPlace,
  onSelectPlace,
  onClosePlace,
}: {
  activeDay: number
  places: ItineraryPlace[]
  selectedPlace: ItineraryPlace | null
  onSelectPlace: (placeId: string) => void
  onClosePlace: () => void
}) {
  return (
    <section className="map-stage" aria-label={`Day ${activeDay} 지도`}>
      <div className="map-canvas">
        {places.map((place) => (
          <button
            aria-label={place.title}
            className={`map-pin ${selectedPlace?.id === place.id ? 'active' : ''}`}
            key={place.id}
            style={{ left: `${place.x}%`, top: `${place.y}%` }}
            type="button"
            onClick={() => onSelectPlace(place.id)}
          >
            <span>{place.order}</span>
          </button>
        ))}
        {selectedPlace && <PlaceInsightCard place={selectedPlace} onClose={onClosePlace} />}
      </div>
    </section>
  )
}

function PlaceInsightCard({ place, onClose }: { place: ItineraryPlace; onClose: () => void }) {
  return (
    <article className="place-insight-card">
      <div>
        <span>{place.category}</span>
        <button type="button" onClick={onClose} aria-label="장소 정보 닫기">×</button>
      </div>
      <h3>{place.title}</h3>
      <p>{place.time} · {place.duration}</p>
      <dl>
        <div>
          <dt>위치</dt>
          <dd>{place.hours}</dd>
        </div>
        <div>
          <dt>평점</dt>
          <dd>{place.rating}</dd>
        </div>
        <div>
          <dt>메모</dt>
          <dd>{place.contentSummary}</dd>
        </div>
      </dl>
    </article>
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
    <aside className="trip-chat-panel">
      <div className="chat-heading">
        <span>Trip room</span>
        <h2>멤버</h2>
      </div>
      <div className="chat-message-list">
        {members.map((member) => (
          <div className="chat-message" key={member.userId}>
            <MemberAvatar member={member} />
            <div>
              <strong>{member.nickname}{currentUser?.id === member.userId ? ' · 나' : ''}</strong>
              <p>{member.role}</p>
            </div>
          </div>
        ))}
      </div>
    </aside>
  )
}

function MemberAvatar({ member }: { member: TripMember }) {
  if (member.profileImageUrl) {
    return <img className="member-avatar" src={member.profileImageUrl} alt={`${member.nickname} 프로필`} />
  }
  return <span className="member-avatar fallback">{member.nickname.slice(0, 1)}</span>
}

function toItineraryPlaces(placeViews: ItineraryPlaceView[]): ItineraryPlace[] {
  return withMapPositions(placeViews.map(toItineraryPlace))
}

function toItineraryPlace(item: ItineraryPlaceView): ItineraryPlace {
  const title = item.display.displayName ?? item.display.fallbackMessage ?? '장소 정보를 불러오지 못했습니다'
  const locationText = item.display.location
    ? `${item.display.location.latitude.toFixed(5)}, ${item.display.location.longitude.toFixed(5)}`
    : '위치 정보를 불러오지 못했습니다'

  return {
    id: item.itemId.toString(),
    day: item.dayNo,
    order: item.sequence,
    title,
    category: '일정',
    time: item.startTime.slice(0, 5),
    duration: formatDuration(item.durationMinutes),
    x: 50,
    y: 50,
    hours: locationText,
    parking: '',
    price: '',
    rating: item.display.googleMapsUri ? 'Google Maps에서 보기' : '조회 시점 표시 정보',
    contentSummary: item.display.resolved ? '조회 시점에 불러온 장소 정보입니다.' : title,
    photoTip: '',
  }
}

function withMapPositions(places: ItineraryPlace[]) {
  if (places.length === 0) {
    return places
  }
  return places.map((place, index) => ({
    ...place,
    x: 18 + ((index * 23) % 64),
    y: 22 + ((index * 17) % 58),
  }))
}

function formatDuration(minutes: number) {
  if (minutes < 60) {
    return `${minutes}분`
  }
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(new Date(`${value}T00:00:00`))
}

function formatTime(value: string) {
  return value.slice(0, 5)
}

function accommodationAreaLabel(value: TripPlanningProfile['accommodationArea']) {
  switch (value) {
    case 'TOURIST_CENTER':
      return '중심 관광지 근처'
    case 'TRANSIT':
      return '대중교통이 편한 곳'
    case 'QUIET':
      return '조용한 지역'
    case 'ANYWHERE':
      return '지역 상관없음'
    default:
      return '선호 숙소 지역 없음'
  }
}

function errorMessageFrom(error: unknown) {
  if (error instanceof ApiError) {
    return error.message
  }
  return '요청 처리 중 오류가 발생했습니다.'
}
