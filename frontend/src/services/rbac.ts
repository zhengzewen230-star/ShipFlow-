import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { Id } from '@/types/api'
import type { ActiveStatus } from './tenants'
export interface Permission { id: Id; permissionCode: string; permissionName: string; description?: string }
export interface Role { id: Id; tenantId?: Id | null; roleCode: string; roleName: string; roleScope: 'PLATFORM' | 'TENANT'; status: ActiveStatus; permissionIds: Id[]; version: number; createdAt: string; updatedAt: string }
export const listRoles = async (status?: ActiveStatus) => unwrap<Role[]>(await http.get('/roles', { params: { status } }))
export const getRole = async (id: Id) => unwrap<Role>(await http.get(`/roles/${id}`))
export const listPermissions = async () => unwrap<Permission[]>(await http.get('/permissions'))
export const replaceRolePermissions = async (id: Id, body: { permissionIds: Id[]; version: number }) => unwrap<Role>(await http.put(`/roles/${id}/permissions`, body, writeConfig('role-permissions', false)))
