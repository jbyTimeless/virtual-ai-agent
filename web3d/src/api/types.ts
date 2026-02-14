/** Auth API types */
export interface LoginRequest {
    username: string
    password: string
}

export interface RegisterRequest {
    username: string
    password: string
    nickname?: string
}

export interface AuthResponse {
    code: number
    message: string
    data: {
        token: string
        user: UserInfo
    }
}

export interface UserInfo {
    id: number
    username: string
    nickname: string
    avatar?: string
}

/** Chat API types */
export interface ChatMessage {
    id: string
    role: 'user' | 'assistant'
    content: string
    timestamp: number
    type: 'text' | 'voice'
}

export interface ChatSendRequest {
    message: string
    agentId?: string
}

export interface ChatVoiceRequest {
    audio: Blob
    conversationId?: string
}

/** 对应后端 ChatSendResponse */
export interface ChatResponse {
    code: number
    message: string
    data: {
        reply: string
        conversationId: string
        agentId: string
    }
}

/** 单条历史消息 */
export interface ChatMessageItem {
    role: string
    content: string
}

/** 对应后端 ChatHistoryResponse */
export interface ChatHistoryResponse {
    code: number
    message: string
    data: {
        messages: ChatMessageItem[]
        conversationId: string
        agentId: string
    }
}

/** Common API Response */
export interface ApiResponse<T = any> {
    code: number
    message: string
    data: T
}

/** Model info for the selector */
export interface ModelInfo {
    id: string
    name: string
    path: string
    thumbnail?: string
    description?: string
}
