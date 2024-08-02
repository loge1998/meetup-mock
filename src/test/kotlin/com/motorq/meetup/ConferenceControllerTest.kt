package com.motorq.meetup

import arrow.core.right
import java.time.Instant
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class ConferenceControllerTest(@Autowired val conferenceController: ConferenceController) {

    @Test
    fun shouldBeAbleToAddAConferenceWithUniqueConferenceName() {
        val conferenceRequest = AddConferenceRequest(
            "test conference name",
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-08-02T06:10:34Z"),
            Instant.parse("2024-08-02T07:10:34Z"),
            30
        )
        val response = conferenceController.addConference(conferenceRequest)
        assertTrue(response.isRight())
        response.onRight {
            assertEquals(conferenceRequest.toConference(), it)
        }
    }

    @Test
    fun shouldThrowConferenceNameAlreadyExistErrorWhenTheSameNameIsBeingUsed() {
        val conferenceRequest = AddConferenceRequest(
            "test conference name",
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-08-02T06:10:34Z"),
            Instant.parse("2024-08-02T07:10:34Z"),
            30
        )
        conferenceController.addConference(conferenceRequest)
        val response = conferenceController.addConference(conferenceRequest)

        assertTrue(response.isLeft())
        response.onLeft {
            assertEquals(ConferenceAlreadyExistError, it)
        }
    }
}