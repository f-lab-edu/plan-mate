export type MockTripSampleOption = {
  id: string
  label: string
  title: string
  destination: string
  startDate: string
  endDate: string
  summary: string
}

export const MOCK_TRIP_SAMPLES: MockTripSampleOption[] = [
  {
    id: 'gangneung-couple-car-3d',
    label: '1. 강릉 커플 자가용 2박 3일',
    title: '강릉 바다 보러 가는 2박 3일',
    destination: '강릉',
    startDate: '2026-07-10',
    endDate: '2026-07-12',
    summary: '세인트존스호텔을 중심으로 바다, 카페, 맛집, 사진 명소를 여유롭게 둘러보는 일정',
  },
  {
    id: 'jeju-family-rentcar-4d',
    label: '2. 제주 가족 렌트카 3박 4일',
    title: '부모님 모시고 제주 3박 4일',
    destination: '제주',
    startDate: '2026-08-03',
    endDate: '2026-08-06',
    summary: '부모님과 함께 동부, 서부, 시내 권역을 무리 없이 나누어 도는 가족 일정',
  },
  {
    id: 'busan-friends-food-public-3d',
    label: '3. 부산 친구 먹방 2박 3일',
    title: '부산 친구들이랑 먹방 여행',
    destination: '부산',
    startDate: '2026-09-18',
    endDate: '2026-09-20',
    summary: '해운대, 광안리, 서면을 중심으로 맛집, 카페, 야경, 술집을 즐기는 일정',
  },
  {
    id: 'tokyo-friends-public-4d',
    label: '4. 도쿄 첫 해외여행 3박 4일',
    title: '도쿄 첫 해외여행 3박 4일',
    destination: '도쿄',
    startDate: '2026-12-12',
    endDate: '2026-12-15',
    summary: '신주쿠역 근처 숙소를 거점으로 시부야, 아사쿠사, 긴자를 이동하는 초보 해외여행 일정',
  },
  {
    id: 'osaka-kyoto-family-public-5d',
    label: '5. 부모님과 오사카 교토 4박 5일',
    title: '부모님과 오사카 교토 4박 5일',
    destination: '오사카/교토',
    startDate: '2026-11-02',
    endDate: '2026-11-06',
    summary: '난바역 근처 숙소를 거점으로 오사카와 교토를 여유롭게 둘러보는 가족 일정',
  },
  {
    id: 'fukuoka-couple-public-3d',
    label: '6. 후쿠오카 커플 먹고 쉬는 2박 3일',
    title: '후쿠오카 커플 먹고 쉬는 2박 3일',
    destination: '후쿠오카',
    startDate: '2026-10-09',
    endDate: '2026-10-11',
    summary: '하카타역 근처 숙소를 중심으로 맛집, 카페, 야경, 쇼핑을 여유롭게 즐기는 일정',
  },
]
