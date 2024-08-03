package com.motorq.meetup

import com.motorq.meetup.controllers.ConferenceController
import com.motorq.meetup.domain.BookingStatus
import com.motorq.meetup.dto.AddConferenceRequest
import com.motorq.meetup.dto.AddUserRequest
import com.motorq.meetup.dto.BookingRequest
import com.motorq.meetup.repositories.BookingRepository
import com.motorq.meetup.repositories.ConferenceRepository
import com.motorq.meetup.repositories.UserRepository
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class ConferenceControllerTest(
    @Autowired val conferenceController: ConferenceController,
    @Autowired val conferenceRepository: ConferenceRepository,
    @Autowired val userRepository: UserRepository,
    @Autowired val bookingRepository: BookingRepository
) {

    @BeforeEach
    fun setup() {
        conferenceRepository.clearAll()
        userRepository.clearAll()
        bookingRepository.clearAll()
    }

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

    @Test
    fun shouldThrowConferenceNotFoundErrorWhenBookingConferenceDoesNotExist() {
        val conferenceName = "test conference name"
        val response = conferenceController.bookConferenceTicket(BookingRequest("randomId", conferenceName))
        assertTrue { response.isLeft() }
        response.onLeft {
            assertEquals(ConferenceNotFoundError, it)
        }
    }

    @Test
    fun shouldThrowUserNotFoundErrorWhenBookingUserDoesNotExist() {
        val conferenceName = "test conference name"
        val conferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-08-02T06:10:34Z"),
            Instant.parse("2024-08-02T07:10:34Z"),
            30
        )
        conferenceController.addConference(conferenceRequest)
        val response = conferenceController.bookConferenceTicket(BookingRequest("randomId", conferenceName))
        assertTrue { response.isLeft() }
        response.onLeft {
            assertEquals(UserNotFoundError, it)
        }
    }

    @Test
    fun shouldThrowConferenceStartedErrorWhenTheBookingTimeHasExceededTheStartTime() {
        val conferenceName = "test conference name"
        val userId = "random uuid"
        val conferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-08-02T06:10:34Z"),
            Instant.parse("2024-08-02T07:10:34Z"),
            30
        )
        val userRequest = AddUserRequest(
            userId,
            "test location",
        )
        conferenceController.addConference(conferenceRequest)
        userRepository.addUser(userRequest)

        val response = conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName))

        assertTrue { response.isLeft() }
        response.onLeft {
            assertEquals(ConferenceStartedError, it)
        }
    }

    @Test
    fun shouldThrowNoSlotsAvailableErrorWhenTheConferenceHasNoExtraSlotsLeft() {
        val conferenceName = "test conference name"
        val userId = "random uuid"
        val conferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-09-02T06:10:34Z"),
            Instant.parse("2024-09-02T07:10:34Z"),
            0
        )
        val userRequest = AddUserRequest(
            userId,
            "test location",
        )
        conferenceController.addConference(conferenceRequest)
        userRepository.addUser(userRequest)

        val response = conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName))

        assertTrue { response.isLeft() }
        response.onLeft {
            assertEquals(NoSlotsAvailableError, it)
        }
    }

    @Test
    fun shouldThrowExistingBookingFoundErrorWhenUserHasExistingBookingForSameConference() {
        val conferenceName = "test conference name"
        val userId = "random uuid"
        val conferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-09-02T06:10:34Z"),
            Instant.parse("2024-09-02T07:10:34Z"),
            30
        )
        val userRequest = AddUserRequest(
            userId,
            "test location",
        )
        conferenceController.addConference(conferenceRequest)
        userRepository.addUser(userRequest)

        bookingRepository.addBooking(userRequest.toUser(), conferenceRequest.toConference(), BookingStatus.CONFIRMED)

        val response = conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName))

        assertTrue { response.isLeft() }
        response.onLeft {
            assertEquals(ExistingBookingFoundError, it)
        }
    }

    @Test
    fun shouldThrowOverlappingConferenceErrorWhenUserHasOverlappingConferenceBooking() {
        val conferenceName = "test conference name"
        val userId = "random uuid"
        val conferenceRequest = AddConferenceRequest(
            "conference 1",
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-09-02T06:10:34Z"),
            Instant.parse("2024-09-02T07:10:34Z"),
            30
        )

        val secondConferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-09-02T06:10:34Z"),
            Instant.parse("2024-09-02T07:10:34Z"),
            30
        )

        val userRequest = AddUserRequest(
            userId,
            "test location",
        )
        conferenceController.addConference(conferenceRequest)
        conferenceController.addConference(secondConferenceRequest)
        userRepository.addUser(userRequest)

        bookingRepository.addBooking(userRequest.toUser(), conferenceRequest.toConference(), BookingStatus.CONFIRMED)

        val response = conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName))

        assertTrue { response.isLeft() }
        response.onLeft {
            assertEquals(OverlappingConferenceError, it)
        }
    }

    @Test
    fun shouldBookTheConferenceForUserWhenAllConditionsAreMet() {
        val conferenceName = "test conference name"
        val userId = "random uuid"
        val conferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-09-02T06:10:34Z"),
            Instant.parse("2024-09-02T07:10:34Z"),
            30
        )
        val userRequest = AddUserRequest(
            userId,
            "test location",
        )
        conferenceController.addConference(conferenceRequest)
        userRepository.addUser(userRequest)

        val response = conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName))
        assertTrue { response.isRight() }
        response.onRight {
            assertEquals(conferenceRequest.toConference(), it.conference)
            assertEquals(userRequest.toUser(), it.user)
            assertEquals(BookingStatus.CONFIRMED,it.status)
        }
        conferenceRepository.getConferenceByName(conferenceName).onRight {
            assertEquals(29, it.availableSlots)
        }
    }
}