import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { ChatMessage } from '@/api/types'
import { getChatHistory } from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
    const messages = ref<ChatMessage[]>([])
    const conversationId = ref<string>('')
    const isLoading = ref(false)
    const currentAgentId = ref<string>('default')

    function addMessage(msg: ChatMessage) {
        messages.value.push(msg)
    }

    function setLoading(loading: boolean) {
        isLoading.value = loading
    }

    function setConversationId(id: string) {
        conversationId.value = id
    }

    function clearMessages() {
        messages.value = []
        conversationId.value = ''
    }

    /**
     * 切换智能体 — 清空当前消息，加载该智能体的历史
     */
    async function switchAgent(agentId: string) {
        if (agentId === currentAgentId.value) return
        currentAgentId.value = agentId
        messages.value = []
        conversationId.value = ''

        try {
            const res = await getChatHistory(agentId)
            const data = res.data?.data
            if (data) {
                conversationId.value = data.conversationId || ''
                if (data.messages && data.messages.length > 0) {
                    // 将后端历史消息转为前端格式
                    messages.value = data.messages.map((m: any, i: number) => ({
                        id: `history-${i}`,
                        role: m.role as 'user' | 'assistant',
                        content: m.content,
                        timestamp: Date.now(),
                        type: 'text' as const
                    }))
                }
            }
        } catch (e) {
            console.warn('加载聊天历史失败:', e)
        }
    }

    return {
        messages, conversationId, isLoading, currentAgentId,
        addMessage, setLoading, setConversationId, clearMessages, switchAgent
    }
})
