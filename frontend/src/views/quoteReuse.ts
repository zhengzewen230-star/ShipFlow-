import type { Quote } from '@/services/quotes'

export interface QuoteReuseInput {
  storeId: string
  channelId: string
  destinationCountry: string
  weight: string
  length: string
  width: string
  height: string
}

export function quoteReuseInput(quote: Quote): QuoteReuseInput {
  return {
    storeId: String(quote.storeId),
    channelId: String(quote.channelId),
    destinationCountry: quote.destinationCountry,
    weight: String(quote.declaredWeight),
    length: String(quote.declaredLength),
    width: String(quote.declaredWidth),
    height: String(quote.declaredHeight),
  }
}
