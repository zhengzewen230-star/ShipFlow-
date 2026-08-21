import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import {
  confirmReconciliationDifference,
  importBillingCsv,
  getBillImportBatch,
  getReconciliation,
  listBillDetails,
  listBillImportErrors,
  listBillImportBatches,
  listReconciliations,
} from './billing'

describe('finance billing service contracts', () => {
  afterEach(() => vi.restoreAllMocks())

  it('uses tenant-scoped batch, error, and detail query paths', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { data: { items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 } } } as never)
    await listBillImportBatches({ page: 1, pageSize: 20, status: 'FAILED' })
    await getBillImportBatch('41')
    await listBillImportErrors('41', { page: 1, pageSize: 20 })
    await listBillDetails({ batchId: '41', status: 'ERROR' })
    expect(get).toHaveBeenNthCalledWith(1, '/billing/import-batches', { params: { page: 1, pageSize: 20, status: 'FAILED' } })
    expect(get).toHaveBeenNthCalledWith(2, '/billing/import-batches/41')
    expect(get).toHaveBeenNthCalledWith(3, '/billing/import-batches/41/errors', { params: { page: 1, pageSize: 20 } })
    expect(get).toHaveBeenNthCalledWith(4, '/billing/details', { params: { batchId: '41', status: 'ERROR' } })
  })

  it('binds billing import requests to an idempotency key', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: { id: '41', status: 'SUCCESS' } } } as never)
    await importBillingCsv(new Blob(['csv'], { type: 'text/csv' }) as unknown as File, '3')
    expect(post).toHaveBeenCalledWith('/billing/import-batches', expect.any(FormData), expect.objectContaining({
      headers: expect.objectContaining({ 'X-Request-ID': expect.any(String), 'Idempotency-Key': expect.stringMatching(/^bill-import-/) }),
    }))
  })

  it('uses reconciliation list, detail, and CSRF/idempotent confirmation paths', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { data: { items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 } } } as never)
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: { id: '81', reconciliationStatus: 'CONFIRMED', version: 1 } } } as never)
    await listReconciliations({ orderId: '9', status: 'PENDING_CONFIRMATION' })
    await getReconciliation('81')
    await confirmReconciliationDifference('81', { resolutionType: 'ACCEPT', remark: 'reviewed', version: 0 })
    expect(get).toHaveBeenNthCalledWith(1, '/reconciliations', { params: { orderId: '9', status: 'PENDING_CONFIRMATION' } })
    expect(get).toHaveBeenNthCalledWith(2, '/reconciliations/81')
    expect(post).toHaveBeenCalledWith('/reconciliations/81/confirm', { resolutionType: 'ACCEPT', remark: 'reviewed', version: 0 }, expect.objectContaining({ headers: expect.objectContaining({ 'X-Request-ID': expect.any(String), 'Idempotency-Key': expect.any(String) }) }))
  })
})
