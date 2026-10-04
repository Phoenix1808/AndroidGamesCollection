package com.example.uploadingscreen

import com.example.uploadingscreen.game.GameSession
import com.example.uploadingscreen.model.LoginResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameSessionTest {

    @Before
    fun reset() {
        GameSession.myUserId = null
        GameSession.enterRoom("RESET0", null, null)
    }

    @Test
    fun loginResponse_parsesMongoIdAsUserId() {
        // trimmed copy of a real /auth/login response
        val json = """{"user":{"_id":"6ac115e3b76d6f1597836a5c","username":"cocappiboft","avatar":null,
            "email":"cocappiboft@test.com","__v":0},"accessToken":"eyJ.x.y"}"""
        val res = Gson().fromJson(json, LoginResponse::class.java)
        assertEquals("6ac115e3b76d6f1597836a5c", res.user?.id)
        assertEquals("eyJ.x.y", res.accessToken)
    }

    @Test
    fun isHost_followsServerHostId() {
        GameSession.myUserId = "me"
        GameSession.setPlayers(linkedMapOf("host" to "A", "me" to "B"), "host")
        assertFalse(GameSession.isHost)
        GameSession.setPlayers(linkedMapOf("me" to "B"), "me")
        assertTrue(GameSession.isHost)
    }

    @Test
    fun enterRoom_clearsPreviousGame() {
        GameSession.setPlayers(linkedMapOf("a" to "A"), "a")
        GameSession.deadPlayers.add("a")
        GameSession.role = "imposter"
        GameSession.enterRoom("NEWRM1", 5, "b")
        assertTrue(GameSession.players.isEmpty())
        assertTrue(GameSession.deadPlayers.isEmpty())
        assertEquals(null, GameSession.role)
        assertEquals(5, GameSession.maxPlayers)
        assertEquals("b", GameSession.hostId)
    }

    @Test
    fun alivePlayers_excludesDead() {
        GameSession.myUserId = "me"
        GameSession.setPlayers(linkedMapOf("me" to "Me", "x" to "X"), "x")
        GameSession.deadPlayers.add("me")
        assertTrue(GameSession.amIDead)
        assertEquals(setOf("x"), GameSession.alivePlayers().keys)
    }
}
