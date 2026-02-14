import request from './request'
import type { ChatSendRequest, ChatResponse, ChatHistoryResponse, ApiResponse } from './types'

/** Send text message */
export function sendMessage(data: ChatSendRequest) {
    return request.post<ChatResponse>('/chat/send', data)
}

/** Send voice message */
export function sendVoice(audio: Blob, conversationId?: string) {
    const formData = new FormData()
    formData.append('audio', audio, 'recording.webm')
    if (conversationId) {
        formData.append('conversationId', conversationId)
    }
    return request.post<ChatResponse>('/chat/voice', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}

/** Get chat history for a specific agent */
export function getChatHistory(agentId?: string) {
    return request.get<ChatHistoryResponse>('/chat/history', {
        params: { agentId: agentId || 'default' }
    })
}

/** Stream chat — SSE for streaming responses */
export function streamChat(message: string, conversationId?: string): EventSource {
    const params = new URLSearchParams({ message })
    if (conversationId) params.append('conversationId', conversationId)
    const token = localStorage.getItem('token') || ''
    return new EventSource(`/api/chat/stream?${params.toString()}&token=${token}`)
}
