package com.motorq.meetup

import com.motorq.meetup.controllers.ConferenceController
import com.motorq.meetup.domain.BookingStatus
import com.motorq.meetup.dto.AddConferenceRequest
import com.motorq.meetup.dto.AddUserRequest
import com.motorq.meetup.dto.BookingRequest
import com.motorq.meetup.entity.BookingsTable
import com.motorq.meetup.entity.ConferenceTable
import com.motorq.meetup.entity.UserTable
import com.motorq.meetup.entity.WaitlistingTable
import com.motorq.meetup.repositories.BookingRepository
import com.motorq.meetup.repositories.ConferenceRepository
import com.motorq.meetup.repositories.UserRepository
import com.motorq.meetup.repositories.WaitlistingRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import java.time.Instant
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class ConferenceControllerTest(
    @Autowired val conferenceController: ConferenceController,
    @Autowired val conferenceRepository: ConferenceRepository,
    @Autowired val userRepository: UserRepository,
    @Autowired val bookingRepository: BookingRepository,
    @Autowired val waitlistingRepository: WaitlistingRepository
) {

    @BeforeEach
    fun setup() {
        transaction {
            ConferenceTable.deleteAll()
            UserTable.deleteAll()
            BookingsTable.deleteAll()
            WaitlistingTable.deleteAll()
        }
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
    fun shouldReturnBookingWithWaitlistingStatusWhenTheConferenceHasNoExtraSlotsLeft() {
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

        val booking = conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName)).shouldBeRight()
        val conference = conferenceRepository.getConferenceByName(conferenceName).shouldBeRight()
        val waitlistRecord =
            waitlistingRepository.getTheOldestWaitlistingRecordForConference(conferenceName).shouldBeSome()

        assertEquals(0, conference.availableSlots)
        assertEquals(conferenceRequest.name, booking.conferenceName)
        assertEquals(userRequest.userId, booking.userId)
        assertEquals(BookingStatus.WAITLISTED, booking.status)

        assertEquals(booking.id, waitlistRecord.bookingId)
        assertFalse { waitlistRecord.isRequestSent }
        assertEquals(conferenceName, waitlistRecord.conferenceName)
        assertEquals(userId, waitlistRecord.userId)
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

        conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName))
            .shouldBeLeft(OverlappingConferenceError)
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
            assertEquals(conferenceRequest.name, it.conferenceName)
            assertEquals(userRequest.userId, it.userId)
            assertEquals(BookingStatus.CONFIRMED, it.status)
        }
        conferenceRepository.getConferenceByName(conferenceName).onRight {
            assertEquals(29, it.availableSlots)
        }
    }

    @Test
    fun shouldReturnBookingStatusForAConfirmedBooking() {
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

        val booking = conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName)).shouldBeRight()
        val bookingStatusResponse = conferenceController.getBookingStatus(booking.id).shouldBeRight()

        assertEquals(booking.id, bookingStatusResponse.bookingId)
        assertEquals(BookingStatus.CONFIRMED, bookingStatusResponse.bookingStatus)
        assertEquals(null, bookingStatusResponse.isSlotAvailable)
        assertEquals(null, bookingStatusResponse.confirmationEndTime)
    }

    @Test
    fun shouldReturnBookingStatusForAWaitListedBooking() {
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

        val booking = conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName)).shouldBeRight()
        val bookingStatusResponse = conferenceController.getBookingStatus(booking.id).shouldBeRight()

        assertEquals(booking.id, bookingStatusResponse.bookingId)
        assertEquals(BookingStatus.WAITLISTED, bookingStatusResponse.bookingStatus)
        assertEquals(false, bookingStatusResponse.isSlotAvailable)
        assertEquals(null, bookingStatusResponse.confirmationEndTime)
    }
}