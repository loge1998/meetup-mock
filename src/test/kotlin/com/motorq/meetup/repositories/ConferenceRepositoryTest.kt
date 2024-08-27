package com.motorq.meetup.repositories

import arrow.core.Either
import com.motorq.meetup.ConferenceNotFoundError
import com.motorq.meetup.CustomError
import com.motorq.meetup.DatabaseOperationFailedError
import com.motorq.meetup.domain.Conference
import com.motorq.meetup.dto.AddConferenceRequest
import com.motorq.meetup.entity.ConferenceTable
import common.TestContainerRunner
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals

@SpringBootTest
class ConferenceRepositoryTest(@Autowired val conferenceRepository: ConferenceRepository) {
    companion object {
        @JvmStatic
        @BeforeAll
        fun startContainer() {
            TestContainerRunner.startPostgresIfNotRunning()
        }
    }

    @BeforeEach
    fun setup() {
        transaction {
            ConferenceTable.deleteAll()
        }
    }

    @Test
    fun shouldReturnDatabaseOperationFailedErrorWhenConferenceNameNotFound() {
        conferenceRepository.decrementConferenceAvailableSlot("random test").shouldBeLeft(DatabaseOperationFailedError)
    }

    @Test
    fun shouldReturnDatabaseOperationFailedErrorWhenConferenceAvailabilityIsLessThan1() {
        val conferenceName = "conference1"
        insertConference(conferenceName, "2024-09-02T06:10:34Z", "2024-09-02T07:10:34Z", 0)
        conferenceRepository.decrementConferenceAvailableSlot(conferenceName).shouldBeLeft(DatabaseOperationFailedError)
    }

    @Test
    fun shouldReduceTheAvailabilityForASuccessfulRequest() {
        val conferenceName = "conference1"
        val conferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-09-02T06:10:34Z"),
            Instant.parse("2024-09-02T07:10:34Z"),
            1
        )
        conferenceRepository.addConference(conferenceRequest)
        conferenceRepository.decrementConferenceAvailableSlot(conferenceName).shouldBeRight(1)
        val conference = conferenceRepository.getConferenceByName(conferenceName).shouldBeRight()
        assertEquals(0, conference.availableSlots)
    }

    @Test
    fun shouldReduceConferenceNotFoundErrorForIncrementRequest() {
        conferenceRepository.incrementConferenceAvailableSlot("random test").shouldBeLeft(ConferenceNotFoundError)
    }

    @Test
    fun shouldIncrementTheAvailabilityForASuccessfulRequest() {
        val conferenceName = "conference1"
        insertConference(conferenceName, "2024-09-02T06:10:34Z", "2024-09-02T07:10:34Z")
        conferenceRepository.incrementConferenceAvailableSlot(conferenceName).shouldBeRight(1)
        val conference = conferenceRepository.getConferenceByName(conferenceName).shouldBeRight()
        assertEquals(2, conference.availableSlots)
    }

    @Test
    fun shouldReturnAllOverlappingConferencesForGivenConferenceName() {

        val conference = insertConference("conferenceToTestAgainst", "2024-09-10T09:00:00Z", "2024-09-10T17:00:00Z").shouldBeRight()

        val conference1 = insertConference("ExactOverlap", "2024-09-10T09:00:00Z", "2024-09-10T17:00:00Z").shouldBeRight()
        val conference2 = insertConference("leftPartial", "2024-09-10T08:00:00Z", "2024-09-10T12:00:00Z").shouldBeRight()
        val conference3 = insertConference("rightPartial", "2024-09-10T15:00:00Z", "2024-09-10T18:00:00Z").shouldBeRight()
        val conference4 = insertConference("containedInside", "2024-09-10T10:00:00Z", "2024-09-10T12:00:00Z").shouldBeRight()
        val conference5 = insertConference("completelyOverlap", "2024-09-10T07:00:00Z", "2024-09-10T19:00:00Z").shouldBeRight()
        val conference6 = insertConference("noOverlapFromLeft", "2024-09-10T06:00:00Z", "2024-09-10T08:00:00Z").shouldBeRight()
        val conference7 = insertConference("noOverlapFromRight", "2024-09-10T18:00:00Z", "2024-09-10T20:00:00Z").shouldBeRight()

        val overlappingConferences = conferenceRepository.getAllOverlappingConference(conference.name).shouldBeRight()
        val expectedAnswer = listOf(conference.name, conference1.name, conference2.name, conference3.name, conference4.name, conference5.name)

        assertThat(overlappingConferences).hasSameElementsAs(expectedAnswer)
    }


    private fun insertConference(conferenceName: String, startDateTime: String, endDateTime: String, availableSlot: Int = 1): Either<CustomError, Conference> {
      val conferenceRequest = AddConferenceRequest(
          conferenceName,
          "test location",
          "test topic, test topics 2",
          Instant.parse(startDateTime),
          Instant.parse(endDateTime),
          availableSlot
        )
        return conferenceRepository.addConference(conferenceRequest)
    }
}