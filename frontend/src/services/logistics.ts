import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { ApiPage, Id } from '@/types/api'
import type { ActiveStatus } from './tenants'
export type TransportMode = 'OCEAN' | 'AIR' | 'ROAD' | 'RAIL' | 'COURIER'
export interface LogisticsProvider { id: Id; providerCode: string; providerName: string; status: ActiveStatus; version: number; createdAt: string; updatedAt: string }
export interface LogisticsChannel { id: Id; providerId: Id; channelCode: string; channelName: string; transportMode: TransportMode; serviceArea: string; status: ActiveStatus; serviceCountries: string[]; version: number; createdAt: string; updatedAt: string }
export interface PriceRuleTier { tierNo: number; minWeight: number; maxWeight?: number | null; billingMode: 'FIXED' | 'FIRST_CONTINUE'; firstWeight?: number | null; firstFee?: number | null; additionalWeight?: number | null; additionalFee?: number | null; tierFee?: number | null }
export interface PriceRule { id: Id; channelId: Id; versionNo: number; ruleName: string; currency: string; volumeDivisor: number; roundingMode: 'CEILING' | 'ROUND' | 'NONE'; roundingIncrement: number; status: 'PUBLISHED'; effectiveFrom?: string; tiers: PriceRuleTier[] }
export const listLogisticsProviders = async () => unwrap<ApiPage<LogisticsProvider>>(await http.get('/platform/logistics-providers'))
export const getLogisticsProvider = async (id: Id) => unwrap<LogisticsProvider>(await http.get(`/platform/logistics-providers/${id}`))
export const createLogisticsProvider = async (body: { providerCode: string; providerName: string }) => unwrap<LogisticsProvider>(await http.post('/platform/logistics-providers', body, writeConfig('provider')))
export const updateLogisticsProvider = async (id: Id, body: { providerName: string; status: ActiveStatus; version: number }) => unwrap<LogisticsProvider>(await http.put(`/platform/logistics-providers/${id}`, body, writeConfig('provider-update', false)))
export const listLogisticsChannels = async () => unwrap<ApiPage<LogisticsChannel>>(await http.get('/platform/logistics-channels'))
export const getLogisticsChannel = async (id: Id) => unwrap<LogisticsChannel>(await http.get(`/platform/logistics-channels/${id}`))
export const createLogisticsChannel = async (body: { providerId: Id; channelCode: string; channelName: string; transportMode: TransportMode; serviceArea: string }) => unwrap<LogisticsChannel>(await http.post('/platform/logistics-channels', body, writeConfig('channel')))
export const updateLogisticsChannel = async (id: Id, body: { channelName: string; transportMode: TransportMode; serviceArea: string; status: ActiveStatus; version: number }) => unwrap<LogisticsChannel>(await http.put(`/platform/logistics-channels/${id}`, body, writeConfig('channel-update', false)))
export const replaceLogisticsChannelServiceCountries = async (id: Id, body: { countryCodes: string[]; version: number }) => unwrap<LogisticsChannel>(await http.put(`/platform/logistics-channels/${id}/service-countries`, body, writeConfig('channel-countries', false)))
export const listPublishedPriceRules = async (channelId: Id) => unwrap<PriceRule[]>(await http.get(`/platform/logistics-channels/${channelId}/price-rules`))
export const getPublishedPriceRule = async (channelId: Id, ruleId: Id) => unwrap<PriceRule>(await http.get(`/platform/logistics-channels/${channelId}/price-rules/${ruleId}`))
export const publishPriceRuleVersion = async (channelId: Id, body: Omit<PriceRule, 'id' | 'channelId' | 'status'>) => unwrap<PriceRule>(await http.post(`/platform/logistics-channels/${channelId}/price-rules`, body, writeConfig('price-rule')))
export const listAvailableLogisticsChannels = async (countryCode?: string) => unwrap<ApiPage<LogisticsChannel>>(await http.get('/logistics/channels', { params: { countryCode } }))
export const getAvailableLogisticsChannel = async (id: Id) => unwrap<LogisticsChannel>(await http.get(`/logistics/channels/${id}`))
export const getAvailableChannelServiceCountries = async (id: Id) => unwrap<string[]>(await http.get(`/logistics/channels/${id}/service-countries`))
export const getEffectivePublishedPriceRule = async (id: Id) => unwrap<PriceRule>(await http.get(`/logistics/channels/${id}/price-rule`))
