import { useEffect, useMemo, useRef, useState } from 'react'
import type { ChangeEvent, CompositionEvent, FormEvent } from 'react'
import type { AuthUser } from '../../api/auth'
import { ApiError } from '../../api/client'
import { autocompletePlaces } from '../../api/places'
import type { PlaceAutocompleteItem } from '../../api/places'
import { createTrip } from '../../api/trips'
import type { CreateTripRequest } from '../../api/trips'
import './TripCreatePage.css'

type TripCreatePageProps = {
  accessToken: string
  user: AuthUser | null
  onBackToMain: () => void
  onCreatedTrip: (tripId: string) => void
  onLogout: () => void
}

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'
type DestinationMode = 'search' | 'region' | 'undecided'
type DestinationTypeKey = 'country' | 'region' | 'city' | 'district' | 'place' | 'airport' | 'station' | 'food' | 'lodging' | 'unknown'
type RecommendationStepId = 'area' | 'duration' | 'companion' | 'style'

type StarterDestination = {
  name: string
  reason: string
  tags: string[]
  difficulty: string
  titleHint: string
}

type BroadRegion = {
  id: string
  name: string
  description: string
  destinations: StarterDestination[]
}

type RecommendationStep = {
  id: RecommendationStepId
  question: string
  options: string[]
}

const DESTINATION_MODES: Array<{
  mode: DestinationMode
  icon: string
  title: string
  description: string
}> = [
  {
    mode: 'region',
    icon: 'AREA',
    title: '지역으로 고르기',
    description: '국가나 광역 지역처럼 넓게 시작할 수 있어요.',
  },
  {
    mode: 'search',
    icon: 'FIND',
    title: '검색해서 고르기',
    description: '도시, 지역, 관광지를 직접 검색해요.',
  },
  {
    mode: 'undecided',
    icon: 'AI',
    title: '아직 못 정했어요',
    description: '기간, 동행, 관심사에 맞춰 추천받아요.',
  },
]

const POPULAR_DESTINATIONS: StarterDestination[] = [
  {
    name: '제주도',
    reason: '자연과 드라이브 중심으로 3박 이상 여행에 좋아요.',
    tags: ['자연', '드라이브', '휴식'],
    difficulty: '렌터카 추천',
    titleHint: '제주도 렌트카 여행',
  },
  {
    name: '부산',
    reason: '도시의 활기와 바다, 먹거리를 함께 잡기 좋아요.',
    tags: ['도시', '바다', '맛집'],
    difficulty: '이동 보통',
    titleHint: '부산 바다 먹거리 여행',
  },
  {
    name: '강릉',
    reason: '주말 바다와 카페 여행으로 부담 없이 시작하기 좋아요.',
    tags: ['바다', '카페', '주말'],
    difficulty: '이동 쉬움',
    titleHint: '강릉 주말 바다 여행',
  },
]

const BROAD_REGIONS: BroadRegion[] = [
  {
    id: 'gangwon',
    name: '강원도',
    description: '강릉, 속초, 양양, 춘천 등에서 일정에 맞는 권역을 좁혀요.',
    destinations: [
      {
        name: '강릉',
        reason: '바다, 카페, 시장을 짧은 동선으로 묶기 좋아요.',
        tags: ['바다', '카페', '주말'],
        difficulty: '이동 쉬움',
        titleHint: '강릉 주말 바다 여행',
      },
      {
        name: '속초',
        reason: '바다와 먹거리, 설악산 근교를 함께 잡기 좋아요.',
        tags: ['바다', '맛집', '자연'],
        difficulty: '이동 보통',
        titleHint: '속초 바다 먹거리 여행',
      },
      {
        name: '양양',
        reason: '서핑과 감성 숙소 중심의 가벼운 여행에 어울려요.',
        tags: ['서핑', '바다', '휴식'],
        difficulty: '렌터카 추천',
        titleHint: '양양 서핑 감성 여행',
      },
    ],
  },
  {
    id: 'japan',
    name: '일본',
    description: '도쿄, 오사카, 후쿠오카처럼 도시 단위로 먼저 좁혀요.',
    destinations: [
      {
        name: '도쿄',
        reason: '도시 산책, 쇼핑, 근교 일정을 나누기 좋아요.',
        tags: ['도시', '쇼핑', '근교'],
        difficulty: '대중교통 쉬움',
        titleHint: '도쿄 도시여행',
      },
      {
        name: '오사카',
        reason: '먹거리와 교토 근교 코스를 함께 구성하기 좋아요.',
        tags: ['맛집', '근교', '도시'],
        difficulty: '이동 쉬움',
        titleHint: '오사카 먹거리 2박 3일',
      },
      {
        name: '후쿠오카',
        reason: '짧은 일정과 맛집 중심 여행에 잘 맞아요.',
        tags: ['맛집', '쇼핑', '짧은 일정'],
        difficulty: '이동 쉬움',
        titleHint: '후쿠오카 2박 3일',
      },
    ],
  },
  {
    id: 'jeju',
    name: '제주도',
    description: '섬 전체로 시작하거나 제주시, 서귀포 중심으로 나눌 수 있어요.',
    destinations: [
      {
        name: '제주시',
        reason: '공항 접근성과 시장, 해안 동선을 잡기 좋아요.',
        tags: ['공항', '시장', '해안'],
        difficulty: '이동 쉬움',
        titleHint: '제주시 중심 여행',
      },
      {
        name: '서귀포',
        reason: '자연, 폭포, 해안 드라이브 중심 일정에 어울려요.',
        tags: ['자연', '드라이브', '휴식'],
        difficulty: '렌터카 추천',
        titleHint: '서귀포 자연 여행',
      },
      {
        name: '제주도',
        reason: '아직 권역을 못 정했다면 섬 전체로 시작해도 괜찮아요.',
        tags: ['전체', '렌터카', '자연'],
        difficulty: '렌터카 추천',
        titleHint: '제주도 렌트카 여행',
      },
    ],
  },
]

const RECOMMENDATION_STEPS: RecommendationStep[] = [
  {
    id: 'area',
    question: '어디로 떠나고 싶나요?',
    options: ['국내', '해외', '상관없음', '고민 중'],
  },
  {
    id: 'duration',
    question: '여행 기간은 어떻게 되나요?',
    options: ['당일치기', '1박 2일', '2박 3일', '3박 이상'],
  },
  {
    id: 'companion',
    question: '누구와 함께 하나요?',
    options: ['혼자', '커플', '친구', '가족'],
  },
  {
    id: 'style',
    question: '가장 관심 있는 것은?',
    options: ['자연', '맛집', '문화', '쇼핑'],
  },
]

const RECOMMENDED_DESTINATIONS: StarterDestination[] = [
  {
    name: '강릉',
    reason: '바다와 카페 중심으로 부담 없이 시작하기 좋아요.',
    tags: ['바다', '카페', '1박 2일'],
    difficulty: '이동 쉬움',
    titleHint: '강릉 주말 바다 여행',
  },
  {
    name: '경주',
    reason: '문화유산이 밀집되어 있어 이동 부담 없이 둘러보기 좋아요.',
    tags: ['문화', '역사', '산책'],
    difficulty: '이동 쉬움',
    titleHint: '경주 역사 산책 여행',
  },
  {
    name: '부산',
    reason: '도시의 활기와 해변, 맛집이 함께 있어 선택지가 넓어요.',
    tags: ['도시', '바다', '맛집'],
    difficulty: '이동 보통',
    titleHint: '부산 바다 먹거리 여행',
  },
  {
    name: '오사카',
    reason: '먹거리와 쇼핑, 근교 도시를 일정별로 나누기 좋아요.',
    tags: ['맛집', '쇼핑', '2박 3일'],
    difficulty: '대중교통 쉬움',
    titleHint: '오사카 먹거리 2박 3일',
  },
]

const AUTOCOMPLETE_DEBOUNCE_MS = 350
const MIN_DESTINATION_QUERY_LENGTH = 2

export function TripCreatePage({
  accessToken,
  user,
  onBackToMain,
  onCreatedTrip,
  onLogout,
}: TripCreatePageProps) {
  const [title, setTitle] = useState('')
  const [destinationMode, setDestinationMode] = useState<DestinationMode>('search')
  const [destinationQuery, setDestinationQuery] = useState('')
  const [selectedDestination, setSelectedDestination] = useState<PlaceAutocompleteItem | null>(null)
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [sessionToken, setSessionToken] = useState('')
  const [autocompleteItems, setAutocompleteItems] = useState<PlaceAutocompleteItem[]>([])
  const [status, setStatus] = useState<AsyncStatus>('idle')
  const [searchStatus, setSearchStatus] = useState<AsyncStatus>('idle')
  const [searchError, setSearchError] = useState('')
  const [formError, setFormError] = useState('')
  const [submitError, setSubmitError] = useState('')
  const [isComposing, setIsComposing] = useState(false)
  const [activeRegionId, setActiveRegionId] = useState(BROAD_REGIONS[0].id)
  const [recommendationStepIndex, setRecommendationStepIndex] = useState(0)
  const [recommendationAnswers, setRecommendationAnswers] = useState<Record<RecommendationStepId, string | undefined>>({
    area: undefined,
    duration: undefined,
    companion: undefined,
    style: undefined,
  })

  const destinationInputRef = useRef<HTMLInputElement | null>(null)
  const lastRequestedQueryRef = useRef('')
  const searchSequenceRef = useRef(0)
  const trimmedTitle = title.trim()
  const trimmedDestinationQuery = destinationQuery.trim()
  const dateRangeValid = !startDate || !endDate || startDate <= endDate
  const selectedDestinationName = selectedDestination?.mainText || selectedDestination?.displayText || ''
  const tripLength = formatTripLength(startDate, endDate)
  const pageCopy = getPageCopy(selectedDestinationName || trimmedDestinationQuery, tripLength?.days)
  const activeRegion = BROAD_REGIONS.find((region) => region.id === activeRegionId) ?? BROAD_REGIONS[0]
  const currentRecommendationStep = RECOMMENDATION_STEPS[Math.min(recommendationStepIndex, RECOMMENDATION_STEPS.length - 1)]
  const showRecommendationResults = recommendationStepIndex >= RECOMMENDATION_STEPS.length
  const shouldShowAssistPanel = !selectedDestination
    && autocompleteItems.length === 0
    && searchStatus !== 'loading'
  const canSubmit = Boolean(
    trimmedTitle
    && selectedDestination
    && startDate
    && endDate
    && dateRangeValid
    && status !== 'loading'
  )

  const showNoResults = useMemo(() => (
    searchStatus === 'success'
    && trimmedDestinationQuery.length >= MIN_DESTINATION_QUERY_LENGTH
    && autocompleteItems.length === 0
    && !selectedDestination
  ), [autocompleteItems.length, searchStatus, selectedDestination, trimmedDestinationQuery.length])
  const filteredRecommendations = useMemo(() => {
    const selectedTags = Object.values(recommendationAnswers)
      .filter((answer): answer is string => Boolean(answer))
    return RECOMMENDED_DESTINATIONS
      .map((destination) => ({
        destination,
        score: selectedTags.filter((tag) => destination.tags.includes(tag)).length,
      }))
      .sort((left, right) => right.score - left.score)
      .slice(0, 3)
      .map((item) => item.destination)
  }, [recommendationAnswers])

  useEffect(() => {
    if (!accessToken || isComposing) {
      return
    }

    if (selectedDestination && destinationMatchesSelected(trimmedDestinationQuery, selectedDestination)) {
      return
    }

    if (trimmedDestinationQuery.length < MIN_DESTINATION_QUERY_LENGTH) {
      lastRequestedQueryRef.current = ''
      return
    }

    if (!sessionToken) {
      return
    }

    if (lastRequestedQueryRef.current === trimmedDestinationQuery) {
      return
    }

    const sequence = searchSequenceRef.current + 1
    searchSequenceRef.current = sequence
    const timeoutId = window.setTimeout(() => {
      lastRequestedQueryRef.current = trimmedDestinationQuery
      setSearchStatus('loading')
      setSearchError('')

      void autocompletePlaces(accessToken, {
        query: trimmedDestinationQuery,
        sessionToken,
        languageCode: 'ko',
      })
        .then((response) => {
          if (searchSequenceRef.current !== sequence) {
            return
          }
          setAutocompleteItems(response.items)
          setSearchStatus('success')
        })
        .catch((error: unknown) => {
          if (searchSequenceRef.current !== sequence) {
            return
          }
          setAutocompleteItems([])
          setSearchStatus('error')
          setSearchError(toSearchUserMessage(error))
        })
    }, AUTOCOMPLETE_DEBOUNCE_MS)

    return () => window.clearTimeout(timeoutId)
  }, [
    accessToken,
    isComposing,
    selectedDestination,
    sessionToken,
    trimmedDestinationQuery,
  ])

  function ensureSessionToken() {
    if (sessionToken) {
      return sessionToken
    }
    const nextSessionToken = createSessionToken()
    setSessionToken(nextSessionToken)
    return nextSessionToken
  }

  function clearDestinationSelection() {
    setSelectedDestination(null)
    setAutocompleteItems([])
    lastRequestedQueryRef.current = ''
    searchSequenceRef.current += 1
  }

  function handleDestinationChange(event: ChangeEvent<HTMLInputElement>) {
    ensureSessionToken()
    const nextQuery = event.target.value
    setDestinationQuery(nextQuery)
    setFormError('')
    setSubmitError('')
    if (nextQuery.trim().length < MIN_DESTINATION_QUERY_LENGTH) {
      setAutocompleteItems([])
      setSearchStatus('idle')
      setSearchError('')
      lastRequestedQueryRef.current = ''
    }
    if (selectedDestination) {
      setSelectedDestination(null)
      lastRequestedQueryRef.current = ''
    }
  }

  function handleCompositionStart() {
    setIsComposing(true)
  }

  function handleCompositionEnd(event: CompositionEvent<HTMLInputElement>) {
    setIsComposing(false)
    const nextQuery = event.currentTarget.value
    setDestinationQuery(nextQuery)
    if (nextQuery.trim().length < MIN_DESTINATION_QUERY_LENGTH) {
      setAutocompleteItems([])
      setSearchStatus('idle')
      setSearchError('')
      lastRequestedQueryRef.current = ''
    }
  }

  function applyDestinationStarter(destination: StarterDestination) {
    ensureSessionToken()
    setDestinationQuery(destination.name)
    clearDestinationSelection()
    setSearchStatus('idle')
    setSearchError('')
    setFormError('')
    setSubmitError('')
    if (!trimmedTitle) {
      setTitle(destination.titleHint)
    }
    window.setTimeout(() => destinationInputRef.current?.focus(), 0)
  }

  function handleSelectDestination(item: PlaceAutocompleteItem) {
    ensureSessionToken()
    setSelectedDestination(item)
    setDestinationQuery(item.displayText || item.mainText)
    setAutocompleteItems([])
    searchSequenceRef.current += 1
    setSearchStatus('success')
    setSearchError('')
    setFormError('')
    setSubmitError('')
  }

  function handleRecommendationOption(option: string) {
    const step = currentRecommendationStep
    setRecommendationAnswers((current) => ({
      ...current,
      [step.id]: option,
    }))
    if (recommendationStepIndex < RECOMMENDATION_STEPS.length - 1) {
      setRecommendationStepIndex((current) => current + 1)
    } else {
      setRecommendationStepIndex(RECOMMENDATION_STEPS.length)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!trimmedTitle || !startDate || !endDate) {
      setFormError('모든 항목을 입력하세요.')
      return
    }

    if (!selectedDestination) {
      setFormError('검색 결과에서 여행지를 선택해주세요.')
      return
    }

    if (!dateRangeValid) {
      setFormError('종료일은 시작일 이후여야 합니다.')
      return
    }

    const payload: CreateTripRequest = {
      title: trimmedTitle,
      destination: selectedDestination.displayText || selectedDestination.mainText,
      destinationPlaceId: selectedDestination.placeId,
      destinationSessionToken: sessionToken || undefined,
      startDate,
      endDate,
    }

    setStatus('loading')
    setFormError('')
    setSubmitError('')

    try {
      const created = await createTrip(accessToken, payload)
      setStatus('success')
      onCreatedTrip(created.id)
    } catch (error: unknown) {
      setStatus('error')
      setSubmitError(toUserMessage(error))
    }
  }

  return (
    <main className="trip-create-page">
      <nav className="trip-create-nav" aria-label="여행 생성 내비게이션">
        <div className="trip-create-nav-inner">
          <button className="trip-create-icon-button" type="button" onClick={onBackToMain} aria-label="메인으로 돌아가기">
            ←
          </button>
          <strong>PlanMate</strong>
          <div className="trip-create-user-menu">
            <span>{user?.nickname ?? '여행자'}</span>
            <button type="button" onClick={onLogout}>로그아웃</button>
          </div>
        </div>
      </nav>

      <form className="trip-create-content" onSubmit={handleSubmit}>
        <header className="trip-create-title-section">
          <p>{pageCopy.kicker}</p>
          <h1>{pageCopy.title}</h1>
          <span>{pageCopy.description}</span>
        </header>

        <section className="trip-card trip-basics-card" aria-label="여행 기본 정보">
          <div className="trip-section-heading">
            <span>STEP 1</span>
            <h2>여행 조건 입력하기</h2>
            <p>여행방 이름과 기간을 먼저 정해요.</p>
          </div>
          <label>
            <span>여행방 이름</span>
            <input
              name="title"
              type="text"
              placeholder="예: 강릉 2박 3일"
              maxLength={60}
              value={title}
              onChange={(event) => {
                setTitle(event.target.value)
                setFormError('')
                setSubmitError('')
              }}
              required
            />
          </label>
          <div className="trip-create-date-grid">
            <label>
              <span>시작일</span>
              <input
                name="startDate"
                type="date"
                value={startDate}
                onChange={(event) => {
                  setStartDate(event.target.value)
                  setFormError('')
                  setSubmitError('')
                }}
                required
              />
            </label>
            <label>
              <span>종료일</span>
              <input
                name="endDate"
                type="date"
                value={endDate}
                onChange={(event) => {
                  setEndDate(event.target.value)
                  setFormError('')
                  setSubmitError('')
                }}
                required
              />
            </label>
          </div>
        </section>

        <section className="trip-card destination-card" aria-label="목적지 선택">
          <div className="trip-section-heading">
            <span>STEP 2</span>
            <h2>어디로 여행을 떠나볼까요?</h2>
            <p>정확한 도시를 몰라도 괜찮아요. 넓은 지역부터 선택할 수 있어요.</p>
          </div>

          <div className="destination-search-box">
            <label>
              <span className="sr-only">목적지 검색</span>
              <input
                ref={destinationInputRef}
                name="destination"
                type="text"
                placeholder="도시, 지역, 관광지를 검색하세요"
                maxLength={120}
                value={destinationQuery}
                onFocus={ensureSessionToken}
                onChange={handleDestinationChange}
                onCompositionStart={handleCompositionStart}
                onCompositionEnd={handleCompositionEnd}
                autoComplete="off"
                aria-describedby="destination-search-status"
                aria-invalid={Boolean(formError && !selectedDestination)}
                required
              />
            </label>
          </div>

          <div className="destination-entry-list" role="tablist" aria-label="목적지 선택 방식">
            {DESTINATION_MODES.map((mode) => (
              <button
                className={`destination-entry-card ${destinationMode === mode.mode ? 'active' : ''}`}
                key={mode.mode}
                type="button"
                role="tab"
                aria-selected={destinationMode === mode.mode}
                onClick={() => {
                  setDestinationMode(mode.mode)
                  setFormError('')
                  setSubmitError('')
                }}
              >
                <span>{mode.icon}</span>
                <strong>{mode.title}</strong>
                <small>{mode.description}</small>
              </button>
            ))}
          </div>

          <div className="destination-search-results" id="destination-search-status" aria-live="polite">
            {searchStatus === 'loading' && <p className="destination-search-state">여행지를 검색하고 있습니다.</p>}
            {searchStatus === 'error' && <p className="destination-search-state error">{searchError}</p>}
            {showNoResults && (
              <div className="destination-search-empty">
                <strong>검색 결과가 없습니다.</strong>
                <span>입력어를 조금 바꾸거나 아래 추천에서 다시 시작해보세요.</span>
              </div>
            )}
            {autocompleteItems.length > 0 && !selectedDestination && (
              <DestinationResultPanel items={autocompleteItems} onSelectDestination={handleSelectDestination} />
            )}
          </div>

          {selectedDestination && (
            <SelectedDestinationCard destination={selectedDestination} />
          )}

          {shouldShowAssistPanel && (
            <DestinationAssistPanel
              mode={destinationMode}
              activeRegion={activeRegion}
              activeRegionId={activeRegionId}
              currentRecommendationStep={currentRecommendationStep}
              recommendationAnswers={recommendationAnswers}
              recommendationStepIndex={recommendationStepIndex}
              recommendations={filteredRecommendations}
              showRecommendationResults={showRecommendationResults}
              onDestinationStart={applyDestinationStarter}
              onRecommendationOption={handleRecommendationOption}
              onRecommendationStepChange={setRecommendationStepIndex}
              onSelectRegion={setActiveRegionId}
            />
          )}
        </section>

        <section className="trip-card next-step-card" aria-label="다음 단계 안내">
          <div className="next-step-icon" aria-hidden="true">PM</div>
          <div>
            <h2>다음 단계에서는</h2>
            <ul>
              <li>숙소와 여행 취향을 이어서 입력해요.</li>
              <li>선택한 목적지를 기준으로 장소 후보를 준비해요.</li>
              <li>AI 일정 생성에서 하루 단위 동선을 조합해요.</li>
            </ul>
          </div>
        </section>

        {formError && <p className="field-error">{formError}</p>}
        {submitError && <p className="trip-create-submit-error" role="alert">{submitError}</p>}

        <div className="trip-create-sticky-actions">
          <button className="primary-action" type="submit" disabled={!accessToken || !canSubmit}>
            {status === 'loading' ? '생성 중' : '여행방 만들고 일정 준비하기'}
          </button>
          <button className="secondary-action" type="button" onClick={onBackToMain}>
            다시 선택하기
          </button>
        </div>
      </form>
    </main>
  )
}

function DestinationResultPanel({
  items,
  onSelectDestination,
}: {
  items: PlaceAutocompleteItem[]
  onSelectDestination: (item: PlaceAutocompleteItem) => void
}) {
  return (
    <section className="destination-result-panel" aria-label="검색 결과">
      <div className="panel-heading">
        <h3>검색 결과</h3>
        <p>Google 후보 중 하나를 선택해야 여행방을 만들 수 있어요.</p>
      </div>
      <ul className="destination-option-list">
        {items.slice(0, 5).map((item) => (
          <li key={item.placeId}>
            <button type="button" onClick={() => onSelectDestination(item)}>
              <span className="destination-marker" aria-hidden="true" />
              <span className="destination-option-copy">
                <span className="destination-option-topline">
                  <strong>{item.mainText}</strong>
                  <em>{destinationTypeLabel(item.types)}</em>
                </span>
                {item.secondaryText && <span>{item.secondaryText}</span>}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}

function DestinationAssistPanel({
  mode,
  activeRegion,
  activeRegionId,
  currentRecommendationStep,
  recommendationAnswers,
  recommendationStepIndex,
  recommendations,
  showRecommendationResults,
  onDestinationStart,
  onRecommendationOption,
  onRecommendationStepChange,
  onSelectRegion,
}: {
  mode: DestinationMode
  activeRegion: BroadRegion
  activeRegionId: string
  currentRecommendationStep: RecommendationStep
  recommendationAnswers: Record<RecommendationStepId, string | undefined>
  recommendationStepIndex: number
  recommendations: StarterDestination[]
  showRecommendationResults: boolean
  onDestinationStart: (destination: StarterDestination) => void
  onRecommendationOption: (option: string) => void
  onRecommendationStepChange: (stepIndex: number) => void
  onSelectRegion: (regionId: string) => void
}) {
  if (mode === 'region') {
    return (
      <section className="mode-panel" aria-label="지역으로 고르기">
        <div className="panel-heading">
          <h3>넓게 선택하기</h3>
          <p>넓은 지역을 골라도 PlanMate가 일정에 맞는 세부 권역을 좁혀줘요.</p>
        </div>
        <div className="region-chip-row" role="tablist" aria-label="넓은 지역">
          {BROAD_REGIONS.map((region) => (
            <button
              className={activeRegionId === region.id ? 'active' : ''}
              key={region.id}
              type="button"
              role="tab"
              aria-selected={activeRegionId === region.id}
              onClick={() => onSelectRegion(region.id)}
            >
              {region.name}
            </button>
          ))}
        </div>
        <article className="broad-region-card">
          <span>넓은 지역</span>
          <strong>{activeRegion.name}</strong>
          <p>{activeRegion.description}</p>
        </article>
        <div className="suggestion-grid">
          {activeRegion.destinations.map((destination) => (
            <RecommendedDestinationCard
              actionLabel="이 지역으로 검색하기"
              destination={destination}
              key={destination.name}
              onDestinationStart={onDestinationStart}
            />
          ))}
        </div>
      </section>
    )
  }

  if (mode === 'undecided') {
    return (
      <section className="mode-panel" aria-label="질문형 추천">
        <div className="panel-heading">
          <h3>간단한 질문에 답해주세요</h3>
          <p>조건에 맞는 여행지를 추천해드릴게요.</p>
        </div>
        <div className="recommendation-progress" aria-hidden="true">
          <span style={{ width: `${(Math.min(recommendationStepIndex + 1, RECOMMENDATION_STEPS.length) / RECOMMENDATION_STEPS.length) * 100}%` }} />
        </div>
        <div className="answer-chip-row" aria-label="선택한 추천 조건">
          {RECOMMENDATION_STEPS.map((step, index) => (
            <button
              className={recommendationStepIndex === index ? 'active' : ''}
              key={step.id}
              type="button"
              onClick={() => onRecommendationStepChange(index)}
            >
              {recommendationAnswers[step.id] ?? `${index + 1}. ${step.question.replace('?', '')}`}
            </button>
          ))}
        </div>
        {!showRecommendationResults ? (
          <div className="undecided-step-card">
            <span>{recommendationStepIndex + 1} / {RECOMMENDATION_STEPS.length}</span>
            <h3>{currentRecommendationStep.question}</h3>
            <div className="undecided-option-grid">
              {currentRecommendationStep.options.map((option) => (
                <button
                  className={recommendationAnswers[currentRecommendationStep.id] === option ? 'active' : ''}
                  key={option}
                  type="button"
                  onClick={() => onRecommendationOption(option)}
                >
                  {option}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div className="recommendation-list">
            {recommendations.map((destination) => (
              <RecommendedDestinationCard
                actionLabel="이 목적지로 검색하기"
                destination={destination}
                key={destination.name}
                onDestinationStart={onDestinationStart}
              />
            ))}
          </div>
        )}
      </section>
    )
  }

  return (
    <section className="mode-panel" aria-label="빠른 검색 추천">
      <div className="panel-heading">
        <h3>인기 여행지</h3>
        <p>카드를 누르면 검색어가 입력됩니다. 검색 결과에서 실제 목적지를 선택해주세요.</p>
      </div>
      <div className="popular-card-list">
        {POPULAR_DESTINATIONS.map((destination) => (
          <RecommendedDestinationCard
            actionLabel="이 목적지로 검색하기"
            destination={destination}
            key={destination.name}
            onDestinationStart={onDestinationStart}
          />
        ))}
      </div>
    </section>
  )
}

function RecommendedDestinationCard({
  actionLabel,
  destination,
  onDestinationStart,
}: {
  actionLabel: string
  destination: StarterDestination
  onDestinationStart: (destination: StarterDestination) => void
}) {
  return (
    <article className="recommended-card">
      <div className="recommended-card-image" aria-hidden="true">
        <span>{destination.difficulty}</span>
      </div>
      <div className="recommended-card-body">
        <h3>{destination.name}</h3>
        <p>{destination.reason}</p>
        <div className="tag-row">
          {destination.tags.map((tag) => (
            <span key={tag}>{tag}</span>
          ))}
        </div>
        <button type="button" onClick={() => onDestinationStart(destination)}>
          {actionLabel}
        </button>
      </div>
    </article>
  )
}

function SelectedDestinationCard({ destination }: { destination: PlaceAutocompleteItem }) {
  return (
    <section className="selected-destination-card" aria-label="선택한 목적지">
      <div className="selected-destination-icon" aria-hidden="true" />
      <div>
        <span>{destinationTypeLabel(destination.types)}</span>
        <h3>{destination.mainText || destination.displayText}</h3>
        {destination.secondaryText && <p>{destination.secondaryText}</p>}
        <small>{destinationScopeHint(destination.types)}</small>
      </div>
    </section>
  )
}

function getPageCopy(destinationName: string, days?: number) {
  if (destinationName && days) {
    return {
      kicker: 'AI TRIP PLANNER',
      title: `${destinationName} ${days}일 여행, 이제 일정으로 바꿔볼까요?`,
      description: '여행방을 만든 뒤 상세 화면에서 숙소, 취향, AI 일정 생성을 이어갑니다.',
    }
  }

  if (destinationName) {
    return {
      kicker: 'AI TRIP PLANNER',
      title: `${destinationName} 여행의 시작점을 잡고 있어요`,
      description: '선택한 목적지를 기준으로 장소 후보와 이동 동선을 준비합니다.',
    }
  }

  return {
    kicker: 'AI TRIP PLANNER',
    title: '어디로 여행을 떠나볼까요?',
    description: '정확한 도시를 몰라도 괜찮아요. 목적지와 기간을 정하면 PlanMate가 AI 일정 생성을 준비합니다.',
  }
}

function formatTripLength(startDate: string, endDate: string) {
  if (!startDate || !endDate || startDate > endDate) {
    return null
  }

  const start = new Date(`${startDate}T00:00:00`)
  const end = new Date(`${endDate}T00:00:00`)
  const days = Math.floor((end.getTime() - start.getTime()) / 86_400_000) + 1

  if (Number.isNaN(days) || days <= 0) {
    return null
  }

  return { days }
}

function destinationMatchesSelected(query: string, selectedDestination: PlaceAutocompleteItem) {
  return query === selectedDestination.displayText.trim()
    || query === selectedDestination.mainText.trim()
}

function destinationTypeLabel(types: string[]) {
  const typeKey = destinationTypeKey(types)
  switch (typeKey) {
    case 'country':
      return '나라'
    case 'region':
      return '지역'
    case 'city':
      return '도시'
    case 'district':
      return '동네'
    case 'airport':
      return '공항'
    case 'station':
      return '역'
    case 'food':
      return '음식점'
    case 'lodging':
      return '숙소'
    case 'place':
      return '장소'
    default:
      return '장소'
  }
}

function destinationScopeHint(types: string[]) {
  const typeKey = destinationTypeKey(types)
  switch (typeKey) {
    case 'country':
      return '나라 단위는 넓어요. 다음 단계에서 여행할 도시를 좁히면 일정이 더 정확해집니다.'
    case 'region':
      return '넓은 지역은 다음 단계에서 대표 도시나 권역을 좁혀드릴게요.'
    case 'city':
      return '이 목적지를 기준으로 일정 후보를 준비합니다.'
    case 'district':
      return '좁은 지역을 선택하면 주변 장소 중심으로 일정이 만들어집니다.'
    case 'airport':
    case 'station':
      return '교통 거점을 기준으로 근처 동선을 만들 수 있어요.'
    case 'food':
    case 'lodging':
    case 'place':
      return '방문 장소에 가까워요. 상위 지역을 기준으로 만들면 일정이 더 자연스러워요.'
    default:
      return '이 목적지를 기준으로 일정 후보를 준비합니다.'
  }
}

function destinationTypeKey(types: string[]): DestinationTypeKey {
  if (types.includes('country')) {
    return 'country'
  }
  if (types.some((type) => type.startsWith('administrative_area'))) {
    return 'region'
  }
  if (types.includes('locality')) {
    return 'city'
  }
  if (types.some((type) => type.startsWith('sublocality')) || types.includes('neighborhood')) {
    return 'district'
  }
  if (types.includes('airport')) {
    return 'airport'
  }
  if (types.includes('train_station') || types.includes('transit_station') || types.includes('subway_station')) {
    return 'station'
  }
  if (types.includes('restaurant') || types.includes('cafe')) {
    return 'food'
  }
  if (types.includes('lodging')) {
    return 'lodging'
  }
  if (types.includes('tourist_attraction') || types.includes('natural_feature') || types.includes('point_of_interest')) {
    return 'place'
  }
  return 'unknown'
}

function createSessionToken() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `session-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function toSearchUserMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    return '로그인이 만료되었습니다. 다시 로그인하세요.'
  }
  return '여행지 검색을 사용할 수 없습니다. 잠시 후 다시 시도해주세요.'
}

function toUserMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return '로그인이 만료되었습니다. 다시 로그인하세요.'
    }
    return error.message
  }
  return '요청 처리 중 오류가 발생했습니다.'
}
