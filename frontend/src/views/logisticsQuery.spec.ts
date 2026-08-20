import { describe, expect, it } from 'vitest'
import { parseLogisticsQuery, toLogisticsQuery } from './logisticsQuery'

describe('logistics query contract', () => {
  it('restores only supported public catalogue filters and sorting', () => {
    expect(parseLogisticsQuery({ channelCode: ' SF ', channelName: 'Express', serviceCountry: 'us', status: 'ACTIVE', page: '2', pageSize: '50', sortField: 'channelName', sortDirection: 'ASC' }))
      .toEqual({ channelCode: 'SF', channelName: 'Express', serviceCountry: 'US', status: 'ACTIVE', page: 2, pageSize: 50, sortField: 'channelName', sortDirection: 'ASC' })
  })

  it('rejects unsupported query values instead of forwarding them to the API', () => {
    expect(parseLogisticsQuery({ serviceCountry: 'USA', status: 'PUBLISHED', page: '0', pageSize: '101', sortField: 'internalCost', sortDirection: 'DROP' }))
      .toEqual({ channelCode: '', channelName: '', serviceCountry: '', status: '', page: 1, pageSize: 20, sortField: 'updatedAt', sortDirection: 'DESC' })
  })

  it('does not keep default values in the browser URL', () => {
    expect(toLogisticsQuery({ channelCode: 'SF', channelName: '', serviceCountry: 'US', status: '', page: 1, pageSize: 20, sortField: 'updatedAt', sortDirection: 'DESC' })).toEqual({ channelCode: 'SF', serviceCountry: 'US' })
  })
})
