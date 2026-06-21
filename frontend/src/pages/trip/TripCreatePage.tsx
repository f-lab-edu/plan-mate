import { useRef, useState } from 'react'
import type { ChangeEvent, CompositionEvent, FormEvent, KeyboardEvent } from 'react'
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

const MIN_DESTINATION_QUERY_LENGTH = 2
const POPULAR_SEARCH_KEYWORDS = ['오사카', '후쿠오카', '강릉', '제주도', '도쿄']

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
  const pageCopy = getPageCopy(selectedDestinationName, tripLength?.days)
  const canSearchDestination = Boolean(
    accessToken
    && trimmedDestinationQuery.length >= MIN_DESTINATION_QUERY_LENGTH
    && searchStatus !== 'loading'
    && !isComposing
  )
  const canSubmit = Boolean(
    trimmedTitle
    && selectedDestination
    && startDate
    && endDate
    && dateRangeValid
    && status !== 'loading'
  )
  const showNoResults = (
    searchStatus === 'success'
    && trimmedDestinationQuery.length >= MIN_DESTINATION_QUERY_LENGTH
    && autocompleteItems.length === 0
    && !selectedDestination
  )
  const searchGuide = getSearchGuide({
    isComposing,
    queryLength: trimmedDestinationQuery.length,
    selectedDestination,
  })

  function handleDestinationChange(event: ChangeEvent<HTMLInputElement>) {
    const nextQuery = event.target.value
    setDestinationQuery(nextQuery)
    setFormError('')
    setSubmitError('')
    setSearchError('')
    setAutocompleteItems([])
    setSearchStatus('idle')
    lastRequestedQueryRef.current = ''

    if (selectedDestination) {
      setSelectedDestination(null)
      searchSequenceRef.current += 1
    }

    if (nextQuery.trim().length < MIN_DESTINATION_QUERY_LENGTH) {
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
  }

  function handleDestinationKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter') {
      return
    }

    event.preventDefault()
    if (event.nativeEvent.isComposing || isComposing) {
      return
    }

    void requestDestinationSearch()
  }

  async function requestDestinationSearch() {
    if (isComposing) {
      return
    }

    if (trimmedDestinationQuery.length < MIN_DESTINATION_QUERY_LENGTH) {
      setAutocompleteItems([])
      setSearchStatus('idle')
      setSearchError('2글자 이상 입력한 뒤 검색해 주세요.')
      return
    }

    if (selectedDestination && destinationMatchesSelected(trimmedDestinationQuery, selectedDestination)) {
      return
    }

    if (lastRequestedQueryRef.current === trimmedDestinationQuery && searchStatus !== 'error') {
      return
    }

    const sequence = searchSequenceRef.current + 1
    searchSequenceRef.current = sequence
    lastRequestedQueryRef.current = trimmedDestinationQuery
    setAutocompleteItems([])
    setSearchStatus('loading')
    setSearchError('')
    setFormError('')
    setSubmitError('')

    try {
      const response = await autocompletePlaces(accessToken, {
        query: trimmedDestinationQuery,
        languageCode: 'ko',
      })
      if (searchSequenceRef.current !== sequence) {
        return
      }
      setAutocompleteItems(response.items)
      setSearchStatus('success')
    } catch (error: unknown) {
      if (searchSequenceRef.current !== sequence) {
        return
      }
      setAutocompleteItems([])
      setSearchStatus('error')
      setSearchError(toSearchUserMessage(error))
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

  function handlePopularKeyword(keyword: string) {
    setDestinationQuery(keyword)
    setSelectedDestination(null)
    setAutocompleteItems([])
    setSearchStatus('idle')
    setSearchError('')
    setFormError('')
    setSubmitError('')
    lastRequestedQueryRef.current = ''
    searchSequenceRef.current += 1
  }

  function handleResetDestination() {
    setSelectedDestination(null)
    setAutocompleteItems([])
    setSearchStatus('idle')
    setSearchError('')
    setFormError('')
    setSubmitError('')
    lastRequestedQueryRef.current = ''
    searchSequenceRef.current += 1
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
      <div className="trip-create-map-grid" aria-hidden="true" />
      <nav className="trip-create-nav" aria-label="여행 생성 내비게이션">
        <div className="trip-create-nav-inner">
          <div className="trip-create-brand">
            <button className="trip-create-icon-button" type="button" onClick={onBackToMain} aria-label="메인으로 돌아가기">
              ←
            </button>
            <span className="trip-create-brand-mark" aria-hidden="true">PM</span>
            <strong>PlanMate</strong>
          </div>
          <div className="trip-create-user-menu">
            <span>{user?.nickname ?? '여행자'}</span>
            <button type="button" onClick={onLogout}>로그아웃</button>
          </div>
        </div>
      </nav>

      <form className={`trip-create-shell ${selectedDestination ? 'destination-selected' : ''}`} onSubmit={handleSubmit}>
        <section className="trip-create-search-panel" aria-label="목적지 검색">
          <div className="trip-create-title-section">
            <p>{pageCopy.kicker}</p>
            <h1>{pageCopy.title}</h1>
            <span>{pageCopy.description}</span>
          </div>

          <div className="trip-create-card trip-create-destination-card">
            <div className="trip-section-heading">
              <span>STEP 1</span>
              <h2>어디로 여행을 떠나볼까요?</h2>
              <p>도시나 지역을 입력한 뒤 Enter 또는 돋보기 아이콘으로 검색해 주세요.</p>
            </div>

            <div className="destination-search-box">
              <label>
                <span className="trip-create-sr-only">도시나 지역 검색</span>
                <span className="destination-search-control">
                  <input
                    name="destination"
                    type="text"
                    placeholder="예: 오사카, 후쿠오카, 시부야, 풍무동"
                    maxLength={120}
                    value={destinationQuery}
                    onChange={handleDestinationChange}
                    onCompositionStart={handleCompositionStart}
                    onCompositionEnd={handleCompositionEnd}
                    onKeyDown={handleDestinationKeyDown}
                    autoComplete="off"
                    aria-describedby="destination-search-guide destination-search-status"
                    aria-invalid={Boolean(formError && !selectedDestination)}
                    required
                  />
                  <button
                    className="destination-search-icon-button"
                    type="button"
                    onClick={() => void requestDestinationSearch()}
                    disabled={!canSearchDestination}
                    aria-label="도시나 지역 검색"
                  >
                    <span className="destination-search-icon" aria-hidden="true" />
                  </button>
                </span>
              </label>
            </div>

            <p
              className={`destination-search-guide ${searchError && searchStatus === 'idle' ? 'error' : ''}`}
              id="destination-search-guide"
            >
              {searchError && searchStatus === 'idle' ? searchError : searchGuide}
            </p>

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
          </div>

          <PopularSearchPanel onSelectKeyword={handlePopularKeyword} />
        </section>

        <aside className="trip-create-details-panel" aria-label="여행 조건 입력">
          {!selectedDestination ? (
            <section className="trip-create-card trip-create-empty-details">
              <span>STEP 2</span>
              <h2>검색 결과에서 지역을 선택하면 다음 단계가 열립니다.</h2>
              <p>후보를 선택하면 위치를 확인하고 여행방 이름과 기간을 입력할 수 있어요.</p>
            </section>
          ) : (
            <>
              <SelectedDestinationCard destination={selectedDestination} onReset={handleResetDestination} />
              <GoogleMapPreview destination={selectedDestination} />

              <section className="trip-create-card trip-create-basics-card" aria-label="여행 기본 정보">
                <div className="trip-section-heading">
                  <span>STEP 2</span>
                  <h2>이제 여행 조건을 입력해 주세요</h2>
                  <p>선택한 지역을 기준으로 여행방 이름과 기간을 정해요.</p>
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

              <section className="trip-create-card trip-create-next-card" aria-label="다음 단계 안내">
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

              {formError && <p className="trip-create-field-error">{formError}</p>}
              {submitError && <p className="trip-create-submit-error" role="alert">{submitError}</p>}

              <div className="trip-create-sticky-actions">
                <button className="trip-create-secondary-action" type="button" onClick={handleResetDestination}>
                  이전 단계
                </button>
                <button className="trip-create-primary-action" type="submit" disabled={!accessToken || !canSubmit}>
                  {status === 'loading' ? '생성 중' : '여행방 만들고 일정 준비하기'}
                </button>
              </div>
            </>
          )}
        </aside>
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

function PopularSearchPanel({ onSelectKeyword }: { onSelectKeyword: (keyword: string) => void }) {
  return (
    <section className="trip-create-card popular-search-panel" aria-label="인기 검색어">
      <div>
        <span>인기 검색어</span>
        <h2>많이 찾는 여행지를 먼저 잡아둘게요.</h2>
        <p>아직 실제 인기 집계는 아니며, 검색어를 빠르게 넣기 위한 임시 위치입니다.</p>
      </div>
      <div className="popular-search-list">
        {POPULAR_SEARCH_KEYWORDS.map((keyword) => (
          <button type="button" key={keyword} onClick={() => onSelectKeyword(keyword)}>
            {keyword}
          </button>
        ))}
      </div>
    </section>
  )
}

function SelectedDestinationCard({
  destination,
  onReset,
}: {
  destination: PlaceAutocompleteItem
  onReset: () => void
}) {
  return (
    <section className="selected-destination-card" aria-label="선택한 여행 기준">
      <div className="selected-destination-icon" aria-hidden="true" />
      <div>
        <span>선택한 여행 기준</span>
        <h3>{destination.mainText || destination.displayText}</h3>
        {destination.secondaryText && <p>{destination.secondaryText}</p>}
        <em>{searchScopeLabel(destination.searchScope)}</em>
      </div>
      <button type="button" onClick={onReset}>다시 검색</button>
    </section>
  )
}

function GoogleMapPreview({ destination }: { destination: PlaceAutocompleteItem }) {
  return (
    <section className="trip-create-card destination-map-card" aria-label="선택한 위치 지도">
      <div className="trip-section-heading">
        <span>위치 확인</span>
        <h2>이 위치가 맞나요?</h2>
        <p>Google Maps에서 선택한 지역의 위치를 확인한 뒤 여행 조건을 입력해 주세요.</p>
      </div>
      <div className="destination-map-frame">
        <iframe
          title={`${destination.displayText} 지도`}
          src={googleMapEmbedUrl(destination)}
          loading="lazy"
          referrerPolicy="no-referrer-when-downgrade"
        />
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
    title: '어디로 여행을 떠나볼까요?',
    description: '도시나 지역을 검색하고 후보를 선택하면 PlanMate가 일정 생성을 시작할 기준점을 잡습니다.',
  }
}

function getSearchGuide({
  isComposing,
  queryLength,
  selectedDestination,
}: {
  isComposing: boolean
  queryLength: number
  selectedDestination: PlaceAutocompleteItem | null
}) {
  if (selectedDestination) {
    return '다른 지역을 찾으려면 다시 검색을 눌러 이전 단계로 돌아가세요.'
  }
  if (isComposing) {
    return '한글 입력을 확정한 뒤 Enter 또는 돋보기 아이콘으로 검색해 주세요.'
  }
  if (queryLength > 0 && queryLength < MIN_DESTINATION_QUERY_LENGTH) {
    return '2글자 이상 입력하면 검색할 수 있어요.'
  }
  return 'Enter를 누르거나 돋보기 아이콘을 클릭하면 검색합니다.'
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

function googleMapEmbedUrl(destination: PlaceAutocompleteItem) {
  const query = encodeURIComponent(destination.displayText || destination.mainText)
  return `https://www.google.com/maps?q=${query}&output=embed`
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
