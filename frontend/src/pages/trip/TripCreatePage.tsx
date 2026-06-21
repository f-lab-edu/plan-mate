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
  const [destinationQuery, setDestinationQuery] = useState('')
  const [selectedDestination, setSelectedDestination] = useState<PlaceAutocompleteItem | null>(null)
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [autocompleteItems, setAutocompleteItems] = useState<PlaceAutocompleteItem[]>([])
  const [status, setStatus] = useState<AsyncStatus>('idle')
  const [searchStatus, setSearchStatus] = useState<AsyncStatus>('idle')
  const [searchError, setSearchError] = useState('')
  const [formError, setFormError] = useState('')
  const [submitError, setSubmitError] = useState('')
  const [isComposing, setIsComposing] = useState(false)

  const lastRequestedQueryRef = useRef('')
  const searchSequenceRef = useRef(0)
  const trimmedTitle = title.trim()
  const trimmedDestinationQuery = destinationQuery.trim()
  const dateRangeValid = !startDate || !endDate || startDate <= endDate
  const selectedDestinationName = selectedDestination?.mainText || selectedDestination?.displayText || ''
  const tripLength = formatTripLength(startDate, endDate)
  const pageCopy = getPageCopy(selectedDestinationName || trimmedDestinationQuery, tripLength?.days)
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
    trimmedDestinationQuery,
  ])

  function handleDestinationChange(event: ChangeEvent<HTMLInputElement>) {
    const nextQuery = event.target.value
    setDestinationQuery(nextQuery)
    setFormError('')
    setSubmitError('')

    if (selectedDestination) {
      setSelectedDestination(null)
      searchSequenceRef.current += 1
      lastRequestedQueryRef.current = ''
    }

    if (nextQuery.trim().length < MIN_DESTINATION_QUERY_LENGTH) {
      setAutocompleteItems([])
      setSearchStatus('idle')
      setSearchError('')
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

  function handleSelectDestination(item: PlaceAutocompleteItem) {
    setSelectedDestination(item)
    setDestinationQuery(item.displayText || item.mainText)
    setAutocompleteItems([])
    searchSequenceRef.current += 1
    setSearchStatus('success')
    setSearchError('')
    setFormError('')
    setSubmitError('')
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!trimmedTitle || !startDate || !endDate) {
      setFormError('모든 항목을 입력하세요.')
      return
    }

    if (!selectedDestination) {
      setFormError('검색 결과에서 여행의 기준이 될 도시나 지역을 선택해 주세요.')
      return
    }

    if (!dateRangeValid) {
      setFormError('종료일은 시작일 이후여야 합니다.')
      return
    }

    const payload: CreateTripRequest = {
      title: trimmedTitle,
      destination: selectedDestination.displayText,
      destinationPlaceId: selectedDestination.placeId,
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
            <h2>어느 도시나 지역을 중심으로 여행할까요?</h2>
            <p>AI가 일정을 만들 수 있도록 기준이 될 도시나 지역을 검색 결과에서 선택해 주세요.</p>
          </div>

          <div className="destination-search-box">
            <label>
              <span className="sr-only">도시나 지역 검색</span>
              <input
                name="destination"
                type="text"
                placeholder="예: 오사카, 후쿠오카, 시부야, 풍무동"
                maxLength={120}
                value={destinationQuery}
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

          <div className="destination-search-results" id="destination-search-status" aria-live="polite">
            {searchStatus === 'loading' && <p className="destination-search-state">도시나 지역을 검색하고 있어요.</p>}
            {searchStatus === 'error' && <p className="destination-search-state error">{searchError}</p>}
            {showNoResults && (
              <div className="destination-search-empty">
                <strong>도시나 지역을 찾을 수 없어요.</strong>
                <span>철자를 확인하거나 상위 지역 또는 국가명을 함께 입력해 주세요.</span>
              </div>
            )}
            {autocompleteItems.length > 0 && !selectedDestination && (
              <DestinationResultPanel items={autocompleteItems} onSelectDestination={handleSelectDestination} />
            )}
          </div>

          {selectedDestination && (
            <SelectedDestinationCard destination={selectedDestination} />
          )}
        </section>

        <section className="trip-card next-step-card" aria-label="다음 단계 안내">
          <div className="next-step-icon" aria-hidden="true">PM</div>
          <div>
            <h2>다음 단계에서는</h2>
            <ul>
              <li>선택한 도시나 지역을 기준으로 장소 후보를 준비해요.</li>
              <li>상세 화면에서 숙소와 여행 취향을 이어서 입력해요.</li>
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
            취소
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
      <ul className="destination-option-list">
        {items.slice(0, 5).map((item) => (
          <li key={item.placeId}>
            <button type="button" onClick={() => onSelectDestination(item)}>
              <span className="destination-marker" aria-hidden="true" />
              <span className="destination-option-copy">
                <span className="destination-option-topline">
                  <strong>{item.mainText}</strong>
                  <em>{searchScopeLabel(item.searchScope)}</em>
                </span>
                {item.secondaryText && <span>{item.secondaryText}</span>}
              </span>
            </button>
          </li>
        ))}
      </ul>
      <p className="destination-search-help">
        원하는 지역이 보이지 않나요?<br />
        주변의 큰 도시나 국가명을 함께 입력해 보세요.<br />
        예: 풍무동 김포, 텐진 후쿠오카, 시부야 도쿄
      </p>
    </section>
  )
}

function SelectedDestinationCard({ destination }: { destination: PlaceAutocompleteItem }) {
  return (
    <section className="selected-destination-card" aria-label="선택한 여행 기준">
      <div className="selected-destination-icon" aria-hidden="true" />
      <div>
        <span>선택한 여행 기준</span>
        <h3>{destination.mainText || destination.displayText}</h3>
        {destination.secondaryText && <p>{destination.secondaryText}</p>}
        <em>{searchScopeLabel(destination.searchScope)}</em>
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
      description: '선택한 도시나 지역을 기준으로 장소 후보와 이동 동선을 준비합니다.',
    }
  }

  return {
    kicker: 'AI TRIP PLANNER',
    title: '목적지만 정하면, 일정의 뼈대가 시작됩니다',
    description: '도시나 지역과 기간을 정하면 PlanMate가 AI 일정 생성을 준비합니다.',
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

function searchScopeLabel(searchScope: PlaceAutocompleteItem['searchScope']) {
  return searchScope === 'CITY' ? '도시' : '지역'
}

function toSearchUserMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    return '로그인이 만료되었습니다. 다시 로그인하세요.'
  }
  return '장소 검색 서비스를 일시적으로 사용할 수 없어요. 잠시 후 다시 시도해 주세요.'
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
