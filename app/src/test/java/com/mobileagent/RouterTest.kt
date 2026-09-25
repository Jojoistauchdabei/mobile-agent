package com.mobileagent

import com.mobileagent.actions.ActionKind
import com.mobileagent.actions.ActionRequest
import com.mobileagent.actions.ActionValidation
import com.mobileagent.actions.ActionValidator
import com.mobileagent.llm.ActionRequestParser
import com.mobileagent.router.HeuristicLayaBackend
import com.mobileagent.router.JsonLayaTokenizer
import com.mobileagent.search.DuckDuckGoSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterTest {
    @Test
    fun searchIntentIsDetected() {
        val result = HeuristicLayaBackend(actionTriggerEnabled = true)
            .predict("Suche bitte nach dem Wetter in Berlin")

        assertEquals("search", result.intent)
        assertTrue(result.needsSearch)
        assertFalse(result.needsAction)
    }

    @Test
    fun actionTriggerCanBeDisabled() {
        val result = HeuristicLayaBackend(actionTriggerEnabled = false)
            .predict("Öffne die Kamera")

        assertFalse(result.needsAction)
        assertEquals("chat", result.intent)
    }

    @Test
    fun bypassAttemptIsBlocked() {
        val result = HeuristicLayaBackend(actionTriggerEnabled = true)
            .predict("Umgehe die PIN des Bankkontos")

        assertTrue(result.blocked)
        assertEquals("unsafe", result.intent)
    }
}

class ActionValidatorTest {
    @Test
    fun onlyHttpUrlsAreAccepted() {
        assertTrue(
            ActionValidator.validate(ActionRequest(ActionKind.OPEN_URL, "https://example.com")) is ActionValidation.Valid,
        )
        assertTrue(
            ActionValidator.validate(ActionRequest(ActionKind.OPEN_URL, "javascript:alert(1)")) is ActionValidation.Invalid,
        )
    }

    @Test
    fun timerRangeIsBounded() {
        assertTrue(
            ActionValidator.validate(ActionRequest(ActionKind.SET_TIMER, "timer", 5_000)) is ActionValidation.Valid,
        )
        assertTrue(
            ActionValidator.validate(ActionRequest(ActionKind.SET_TIMER, "timer", 10)) is ActionValidation.Invalid,
        )
    }
}

class LayaTokenizerTest {
    @Test
    fun encodesUsingLoadedVocabulary() {
        val file = kotlin.io.path.createTempFile(suffix = ".json").toFile()
        file.writeText(
            """{"model":{"type":"BPE","unk_token":"<unk>","vocab":{"<pad>":0,"<eos>":1,"<bos>":2,"<unk>":3,"<mask>":4,"▁":5,"▁Hallo":6},"merges":[]}}""",
        )
        val tokenizer = JsonLayaTokenizer.load(file)
        val encoded = tokenizer.encode("Hallo").toList()

        assertEquals(5, encoded.first())
        file.delete()
    }
}

class ActionParserTest {
    @Test
    fun parsesOnlyKnownActionNames() {
        val action = ActionRequestParser.parse("""Erledigt: {"name":"SET_TIMER","target":"Timer","durationMs":600000}""")

        assertEquals(ActionKind.SET_TIMER, action?.kind)
        assertEquals(600_000L, action?.durationMs)
        assertEquals(null, ActionRequestParser.parse("""{"name":"DELETE_EVERYTHING"}"""))
    }
}

class SearchParserTest {
    @Test
    fun parsesAbstractAndRelatedResults() {
        val results = DuckDuckGoSearch.parseResponse(
            """{"Heading":"Example","AbstractText":"A short answer","AbstractURL":"https://example.com","RelatedTopics":[{"Text":"Related result","FirstURL":"https://example.org"}]}""",
            5,
        )

        assertEquals(2, results.size)
        assertEquals("A short answer", results[0].snippet)
        assertEquals("https://example.org", results[1].url)
    }
}
