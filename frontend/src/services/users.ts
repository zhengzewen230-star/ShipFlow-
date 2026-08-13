import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { ApiPage, Id, PageQuery } from '@/types/api'
import type { ActiveStatus } from './tenants'
export interface User { id: Id; tenantId: Id; username: string; displayName: string; status: ActiveStatus; roleIds?: Id[]; version: number; createdAt: string; updatedAt: string }
export interface UserQuery extends PageQuery { status?: ActiveStatus; username?: string }
export interface CreateUserRequest { username: string; displayName: string; temporaryPassword: string; roleIds: Id[] }
export const listUsers = async (params: UserQuery = {}) => unwrap<ApiPage<User>>(await http.get('/users', { params }))
export const getUser = async (id: Id) => unwrap<User>(await http.get(`/users/${id}`))
export const createUser = async (body: CreateUserRequest) => unwrap<User>(await http.post('/users', body, writeConfig('user')))
export const updateUser = async (id: Id, body: { displayName: string; version: number }) => unwrap<User>(await http.put(`/users/${id}`, body, writeConfig('user-update')))
export const changeUserStatus = async (id: Id, body: { status: ActiveStatus; version: number }) => unwrap<User>(await http.post(`/users/${id}/status`, body, writeConfig('user-status', false)))
export const replaceUserRoles = async (id: Id, body: { roleIds: Id[]; version: number }) => unwrap<User>(await http.put(`/users/${id}/roles`, body, writeConfig('user-roles', false)))
