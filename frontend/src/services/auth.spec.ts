import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { refreshToken } from './auth'

describe('authentication service method contracts', () => {
  afterEach(() => vi.restoreAllMocks())

  it('refreshes the session with POST and no request body', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({
      data: { data: { accessToken: 'access-token', expiresIn: 900 } },
    } as never)

    await expect(refreshToken()).resolves.toEqual({ accessToken: 'access-token', expiresIn: 900 })
    expect(post).toHaveBeenCalledWith('/auth/refresh')
  })
})
