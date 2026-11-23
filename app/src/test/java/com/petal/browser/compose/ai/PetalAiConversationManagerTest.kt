package com.petal.browser.compose.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PetalAiConversationManagerTest {

    private class MockAiBackend(
        private val chunkFlowProvider: () -> Flow<String> = { flow { emit("Hello world") } }
    ) : AiBackend {
        var lastMessagesSent: List<AiChatMessage> = emptyList()
        var streamCallCount = 0

        override fun stream(messages: List<AiChatMessage>, model: String): Flow<String> {
            streamCallCount++
            lastMessagesSent = messages
            return chunkFlowProvider()
        }
    }

    @Test
    fun testPerTabIsolation() {
        val manager = PetalAiConversationManager(maxTurns = 10)
        manager.setMessagesForTab("tab1", listOf(PetalChatMessage(role = AiRole.USER, text = "Hello from Tab 1")))
        manager.setMessagesForTab("tab2", listOf(PetalChatMessage(role = AiRole.USER, text = "Hello from Tab 2")))

        val tab1Messages = manager.getMessages("tab1")
        val tab2Messages = manager.getMessages("tab2")

        assertEquals(1, tab1Messages.size)
        assertEquals("Hello from Tab 1", tab1Messages[0].text)

        assertEquals(1, tab2Messages.size)
        assertEquals("Hello from Tab 2", tab2Messages[0].text)
    }

    @Test
    fun testHistoryCapping() {
        // maxTurns = 3 -> max 6 messages
        val manager = PetalAiConversationManager(maxTurns = 3)
        val tabId = "tab_cap"

        // Add 8 messages
        for (i in 1..8) {
            val role = if (i % 2 == 1) AiRole.USER else AiRole.ASSISTANT
            manager.addMessage(tabId, PetalChatMessage(role = role, text = "Message $i"))
        }

        val messages = manager.getMessages(tabId)
        assertEquals(6, messages.size)
        // Oldest 2 messages (Message 1 and Message 2) should be dropped
        assertEquals("Message 3", messages.first().text)
        assertEquals("Message 8", messages.last().text)
    }

    @Test
    fun testClearConversation() {
        val manager = PetalAiConversationManager()
        val tabId = "tab_clear"
        manager.addMessage(tabId, PetalChatMessage(role = AiRole.USER, text = "Hi"))
        assertEquals(1, manager.getMessages(tabId).size)

        manager.clearConversation(tabId)
        assertTrue(manager.getMessages(tabId).isEmpty())
        assertNull(manager.tabErrors.value[tabId])
    }

    @Test
    fun testPruneClosedTabs() {
        val manager = PetalAiConversationManager()
        manager.addMessage("tab1", PetalChatMessage(role = AiRole.USER, text = "Keep me"))
        manager.addMessage("tab2", PetalChatMessage(role = AiRole.USER, text = "Close me"))
        manager.addMessage("tab3", PetalChatMessage(role = AiRole.USER, text = "Also close me"))

        manager.pruneClosedTabs(activeTabIds = setOf("tab1"))

        assertEquals(1, manager.conversations.value.size)
        assertTrue(manager.conversations.value.containsKey("tab1"))
        assertFalse(manager.conversations.value.containsKey("tab2"))
        assertFalse(manager.conversations.value.containsKey("tab3"))
    }

    @Test
    fun testDropTab() {
        val manager = PetalAiConversationManager()
        manager.addMessage("tabA", PetalChatMessage(role = AiRole.USER, text = "A"))
        manager.dropTab("tabA")
        assertTrue(manager.getMessages("tabA").isEmpty())
    }

    @Test
    fun testSendMessageStreamingAccumulation() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(testDispatcher)
        val manager = PetalAiConversationManager()

        val backend = MockAiBackend {
            flow {
                emit("Hello")
                emit(" ")
                emit("World")
                emit("!")
            }
        }

        val tabId = "tab_stream"
        manager.sendMessage(tabId, "Tell me a greeting", backend, "test-model", scope)

        testScheduler.advanceUntilIdle()

        val messages = manager.getMessages(tabId)
        assertEquals(2, messages.size)
        assertEquals(AiRole.USER, messages[0].role)
        assertEquals("Tell me a greeting", messages[0].text)
        assertEquals(AiRole.ASSISTANT, messages[1].role)
        assertEquals("Hello World!", messages[1].text)
        assertFalse(manager.isStreaming(tabId))
    }

    @Test
    fun testStopStreaming() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(testDispatcher)
        val manager = PetalAiConversationManager()

        val backend = MockAiBackend {
            flow {
                emit("Part 1")
                kotlinx.coroutines.delay(1000)
                emit("Part 2")
            }
        }

        val tabId = "tab_cancel"
        manager.sendMessage(tabId, "Hello", backend, "test-model", scope)

        // Advance a little to emit "Part 1"
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()

        assertTrue(manager.isStreaming(tabId))
        assertEquals("Part 1", manager.getMessages(tabId).last().text)

        manager.stopStreaming(tabId)
        assertFalse(manager.isStreaming(tabId))

        // Advance past delay
        testScheduler.advanceTimeBy(2000)
        testScheduler.advanceUntilIdle()

        // "Part 2" was never appended because job was cancelled
        assertEquals("Part 1", manager.getMessages(tabId).last().text)
    }

    @Test
    fun testErrorStateHandling() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(testDispatcher)
        val manager = PetalAiConversationManager()

        val backend = MockAiBackend {
            flow {
                throw AiBackendException.MissingApiKey("API key is not configured")
            }
        }

        val tabId = "tab_error"
        manager.sendMessage(tabId, "Hello", backend, "test-model", scope)
        testScheduler.advanceUntilIdle()

        assertFalse(manager.isStreaming(tabId))
        val error = manager.tabErrors.value[tabId]
        assertNotNull(error)
        assertTrue(error is AiBackendException.MissingApiKey)
    }

    @Test
    fun testRegenerateLastMessage() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(testDispatcher)
        val manager = PetalAiConversationManager()

        var callCount = 0
        val backend = MockAiBackend {
            flow {
                callCount++
                if (callCount == 1) {
                    emit("First answer")
                } else {
                    emit("Second answer")
                }
            }
        }

        val tabId = "tab_regen"
        manager.sendMessage(tabId, "Original query", backend, "test-model", scope)
        testScheduler.advanceUntilIdle()

        assertEquals(2, manager.getMessages(tabId).size)
        assertEquals("First answer", manager.getMessages(tabId)[1].text)

        // Regenerate
        manager.regenerateLastMessage(tabId, backend, "test-model", scope)
        testScheduler.advanceUntilIdle()

        assertEquals(2, manager.getMessages(tabId).size)
        assertEquals("Original query", manager.getMessages(tabId)[0].text)
        assertEquals("Second answer", manager.getMessages(tabId)[1].text)
    }
}
