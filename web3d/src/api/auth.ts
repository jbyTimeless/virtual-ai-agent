import request from './request'
import type { LoginRequest, RegisterRequest, AuthResponse, ApiResponse } from './types'

/** User login */
export function login(data: LoginRequest) {
    return request.post<AuthResponse>('/auth/login', data)
}

/** User register */
export function register(data: RegisterRequest) {
    return request.post<AuthResponse>('/auth/register', data)
}

/** User logout */
export function logout() {
    return request.post<ApiResponse>('/auth/logout')
}
