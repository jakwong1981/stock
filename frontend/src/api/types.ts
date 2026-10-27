/** API DTOs. All monetary / quantity fields are decimal strings. */

export interface Instrument {
  symbol: string
  name: string
  shortName: string
  exchange: string
  currency: string
  timezone: string
  logoUrl: string | null
  marquee: boolean
}

export interface Quote {
  symbol: string
  price: string
  prevClose: string
  open: string
  high: string
  low: string
  change: string
  changePercent: string
  volume: string
  lastSize: string
  ts: number
}

export interface Candle {
  time: number // epoch seconds
  open: string
  high: string
  low: string
  close: string
  volume: string
}

export type Session = 'PRE_MARKET' | 'REGULAR' | 'LUNCH_BREAK' | 'POST_MARKET' | 'CLOSED'
export interface MarketStatus {
  exchange: string
  timezone: string
  session: Session
  localTime: string
}

export interface Me {
  id: string
  email: string
  displayName: string | null
  preferredLocale: string
  colorScheme: 'GREEN_UP' | 'RED_UP'
}

export interface TokenResponse {
  accessToken: string
  expiresAt: string
  user: Me
}

export type Side = 'BUY' | 'SELL' | 'DIVIDEND'
export interface Transaction {
  id: string
  symbol: string
  side: Side
  quantity: string
  price: string
  fee: string
  currency: string
  executedAt: string
}

export interface Position {
  symbol: string
  currency: string
  quantity: string
  averageCost: string
  costBasis: string
  marketPrice: string | null
  marketValue: string | null
  unrealizedPnl: string | null
  unrealizedPnlPercent: string | null
  realizedPnl: string
  dividends: string
}

export interface CurrencySummary {
  currency: string
  costBasis: string
  marketValue: string
  unrealizedPnl: string
  realizedPnl: string
  dividends: string
}

export interface PortfolioResult {
  positions: Position[]
  summaries: CurrencySummary[]
}

export type SentimentLabel = 'BULLISH' | 'BEARISH' | 'NEUTRAL' | 'UNANALYZED'
export interface NewsItem {
  id: string
  title: string
  summary: string
  source: string
  url: string
  publishedAt: string
  symbols: string[]
  language: string
  sentimentScore: string | null
  sentimentLabel: SentimentLabel
  isBreaking: boolean
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}
