import { bearerHeaders, request } from './client'

export type PlaceAutocompleteRequest = {
  query: string
  sessionToken: string
  languageCode?: string
}

export type PlaceAutocompleteItem = {
  placeId: string
  mainText: string
  secondaryText: string
  displayText: string
  types: string[]
}

export type PlaceAutocompleteResponse = {
  items: PlaceAutocompleteItem[]
}

export function autocompletePlaces(accessToken: string, payload: PlaceAutocompleteRequest) {
  return request<PlaceAutocompleteResponse>('/api/places/autocomplete', {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(payload),
  })
}
