import { bearerHeaders, request } from './client'

export type TripStatus = 'PLANNING' | 'UPCOMING' | 'COMPLETED'

export type TripSummary = {
  id: string
  title: string
  destination: string
  startDate: string
  endDate: string
  status: TripStatus
  memberCount: number
  createdAt: string
}

export type TripMember = {
  userId: number
  nickname: string
  profileImageUrl: string | null
  role: 'OWNER' | 'MEMBER'
}

export type TripDetail = TripSummary & {
  members: TripMember[]
  itinerary: TripItinerary | null
}

export type CreateTripRequest = {
  title: string
  destination: string
  startDate: string
  endDate: string
  mockSampleId?: string
}

export type TripItinerary = {
  sourceType: string
  sourceSampleId: string | null
  summary: string
  budgetSummary: BudgetSummary
  days: ItineraryDay[]
  alternativeSuggestions: AlternativeSuggestion[]
  verificationWarnings: string[]
}

export type BudgetSummary = {
  currency: string
  totalMin: number
  totalMax: number
  perPersonMin: number
  perPersonMax: number
  notes: string[]
}

export type ItineraryDay = {
  day: number
  dateLabel: string
  theme: string
  items: ItineraryItem[]
}

export type ItineraryItem = {
  id: string
  order: number
  startTime: string
  endTime: string
  placeName: string
  category: string
  areaHint: string
  description: string
  estimatedCost: EstimatedCost
  parking: ParkingInfo
  transport: TransportInfo
  whyRecommended: string
  needsVerification: boolean
  lat: number | null
  lng: number | null
  mapVisible: boolean
}

export type EstimatedCost = {
  min: number
  max: number
  included: string[]
}

export type ParkingInfo = {
  required: boolean
  estimatedCostMin: number
  estimatedCostMax: number
  notes: string
}

export type TransportInfo = {
  mode: string
  fromPreviousMinutes: number
  estimatedCostMin: number
  estimatedCostMax: number
  notes: string
}

export type AlternativeSuggestion = {
  target: string
  reason: string
  candidates: string[]
}

export function listMyTrips(accessToken: string) {
  return request<TripSummary[]>('/api/trips', {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function createTrip(accessToken: string, payload: CreateTripRequest) {
  return request<TripSummary>('/api/trips', {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(payload),
  })
}

export function getTripDetail(accessToken: string, tripId: string) {
  return request<TripDetail>(`/api/trips/${tripId}`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}
