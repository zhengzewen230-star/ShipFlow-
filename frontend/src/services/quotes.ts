import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { ApiPage, Id, PageQuery } from '@/types/api'

export interface CreateQuoteRequest { storeId: number; channelId: number; declaredWeight: number; declaredLength: number; declaredWidth: number; declaredHeight: number; destinationCountry: string }
export interface Quote { id: Id; quoteNo: string; channelId: Id; destinationCountry: string; ruleVersionNo?: number; declaredWeight: number; declaredChargeableWeight: number; amount: number; currency: string; feeDetail: Record<string, unknown>; validFrom?: string; validTo: string; status?: 'VALID' | 'EXPIRED' | 'CANCELLED'; version?: number }
export interface QuoteQuery extends PageQuery { storeId?: Id; channelId?: Id; status?: 'VALID' | 'EXPIRED' | 'CANCELLED' }
export interface QuoteValidation { quoteId: Id; exists: boolean; expired: boolean; canCreateOrder: boolean; reason: 'AVAILABLE' | 'EXPIRED' | 'CANCELLED' | 'ALREADY_USED' }
export const listQuotes = async (params: QuoteQuery = {}) => unwrap<ApiPage<Quote>>(await http.get('/quotes', { params }))
export const createQuote = async (body: CreateQuoteRequest) => unwrap<Quote>(await http.post('/quotes', body, writeConfig('quote')))
export const getQuote = async (id: Id) => unwrap<Quote>(await http.get(`/quotes/${id}`))
export const validateQuote = async (id: Id) => unwrap<QuoteValidation>(await http.post(`/quotes/${id}/validate`, undefined, writeConfig('quote-validation', false)))
