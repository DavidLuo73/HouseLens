export const SOURCE_LABELS: Record<string, string> = {
  F591: '591',
  Sinyi: '信義',
  Yungching: '永慶',
  Rakuya: '樂屋',
  TwHouse: '台灣房屋',
  HBHousing: '住商',
  CtHouse: '中信',
}

export const SOURCE_SITES = Object.entries(SOURCE_LABELS).map(([value, label]) => ({ value, label }))
