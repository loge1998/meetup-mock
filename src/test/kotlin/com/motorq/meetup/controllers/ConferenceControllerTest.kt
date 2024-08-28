package com.motorq.meetup.controllers

import com.motorq.meetup.BookingNotFoundError
import com.motorq.meetup.ConferenceAlreadyExistError
import com.motorq.meetup.ConferenceNotFoundError
import com.motorq.meetup.ConferenceStartedError
import com.motorq.meetup.ExistingBookingFoundError
import com.motorq.meetup.OverlappingConferenceError
import com.motorq.meetup.UserNotFoundError
import com.motorq.meetup.WrongRequestError
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
import com.motorq.meetup.repositories.WaitListingRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeNone
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit
import org.awaitility.Awaitility.await
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
    @Autowired val waitlistingRepository: WaitListingRepository
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
        conferenceController.addConference(conferenceRequest).shouldBeRight(conferenceRequest.toConference())
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
        conferenceController.addConference(conferenceRequest).shouldBeLeft(ConferenceAlreadyExistError)
    }

    @Test
    fun shouldThrowConferenceNotFoundErrorWhenBookingConferenceDoesNotExist() {
        val conferenceName = "test conference name"
        conferenceController.bookConferenceTicket(BookingRequest("randomId", conferenceName)).shouldBeLeft(ConferenceNotFoundError)
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
        conferenceController.bookConferenceTicket(BookingRequest("randomId", conferenceName)).shouldBeLeft(UserNotFoundError)
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

        conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName)).shouldBeLeft(ConferenceStartedError)
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
            waitlistingRepository.getTheOldestWaitListingRecordForConference(conferenceName).shouldBeRight()
                .shouldBeSome()

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

    @Test
    fun shouldReturnBookingNotFoundWhenBookingIdIsNotPresent() {
         conferenceController.getBookingStatus(UUID.randomUUID()).shouldBeLeft(BookingNotFoundError)
    }

    @Test
    fun shouldReturnBookingNotFoundWhenBookingIdIsNotPresentForCancelBooking() {
        conferenceController.cancelBooking(UUID.randomUUID()).shouldBeLeft(BookingNotFoundError)
    }

    @Test
    fun shouldReturnBookingNotFoundWhenBookingIdIsNotPresentForConfirmBooking() {
        conferenceController.confirmBooking(UUID.randomUUID()).shouldBeLeft(BookingNotFoundError)
    }

    @Test
    fun shouldReturnWrongRequestErrorWhenBookingStatusIsNotWaitlistedForConfirmBooking() {
        val conferenceName = "test conference name"
        val userId = "random uuid"
        val conferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-09-02T06:10:34Z"),
            Instant.parse("2024-09-02T07:10:34Z"),
            10
        )
        val userRequest = AddUserRequest(
            userId,
            "test location",
        )
        conferenceController.addConference(conferenceRequest)
        userRepository.addUser(userRequest)

        val booking = conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName)).shouldBeRight()
        conferenceController.confirmBooking(bookingId = booking.id).shouldBeLeft(WrongRequestError("Provided booking is not in waitlisting"))
    }

    @Test
    fun shouldReturnWrongRequestErrorWhenBookingStatusIsAlreadyCancelledForCancelBooking() {
        val conferenceName = "test conference name"
        val userId = "random uuid"
        val conferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-09-02T06:10:34Z"),
            Instant.parse("2024-09-02T07:10:34Z"),
            10
        )
        val userRequest = AddUserRequest(
            userId,
            "test location",
        )
        conferenceController.addConference(conferenceRequest)
        userRepository.addUser(userRequest)

        val booking = bookingRepository.addBooking(
            userRequest.toUser(),
            conferenceRequest.toConference(),
            BookingStatus.CANCELLED
        ).shouldBeRight()

        conferenceController.cancelBooking(bookingId = booking.id).shouldBeLeft(WrongRequestError("Provided booking is already cancelled"))
    }

    @Test
    fun shouldReturnWrongRequestErrorWhenBookingIsNotEligibleForConfirmation() {
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
        conferenceController.confirmBooking(bookingId = booking.id)
            .shouldBeLeft(WrongRequestError("Provided booking is not eligible for confirmation."))
    }

    @Test
    fun shouldReturnBookingResponseWhenConfirmBookingRequestIsSuccessful() {
        val conferenceName = "test conference name"
        val conferenceName2 = "test2"
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

        val conferenceRequest2 = AddConferenceRequest(
            conferenceName2,
            "test location 2",
            "test topic",
            Instant.parse("2024-09-02T06:50:34Z"),
            Instant.parse("2024-09-02T07:40:34Z"),
            0
        )

        val conference = conferenceController.addConference(conferenceRequest).shouldBeRight()
        val conference2 = conferenceController.addConference(conferenceRequest2).shouldBeRight()
        val user = userRepository.addUser(userRequest).shouldBeRight()
        val booking1 = bookingRepository.addBooking(user, conference, BookingStatus.WAITLISTED).shouldBeRight()
        val booking2 = bookingRepository.addBooking(user, conference2, BookingStatus.WAITLISTED).shouldBeRight()

        waitlistingRepository.addWaitListEntry(booking1)
        waitlistingRepository.addWaitListEntry(booking2)
        waitlistingRepository.setWaitListEndTime(booking1.id, Instant.now().plus(1, ChronoUnit.HOURS))

        val bookingStatusResponse = conferenceController.confirmBooking(bookingId = booking1.id).shouldBeRight()

        assertEquals(booking1.id, bookingStatusResponse.bookingId)
        assertEquals(BookingStatus.CONFIRMED, bookingStatusResponse.bookingStatus)

        waitlistingRepository.getTheOldestWaitListingRecordForConference(conferenceName).shouldBeRight().shouldBeNone()
        waitlistingRepository.getTheOldestWaitListingRecordForConference(conferenceName2).shouldBeRight().shouldBeNone()
    }

    @Test
    fun shouldReturnWrongRequestErrorWhenTheSlotAvailabilityEndTimeIsExceeded() {
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

        val conference = conferenceController.addConference(conferenceRequest).shouldBeRight()
        val user = userRepository.addUser(userRequest).shouldBeRight()
        val booking = bookingRepository.addBooking(user, conference, BookingStatus.WAITLISTED).shouldBeRight()

        waitlistingRepository.addWaitListEntry(booking)
        waitlistingRepository.setWaitListEndTime(booking.id, Instant.now().minusSeconds(5))

        conferenceController.confirmBooking(bookingId = booking.id).shouldBeLeft(WrongRequestError("Provided booking is not eligible for confirmation."))
    }

    @Test
    fun shouldCancelWaitListedBookingWithoutNotifyingOtherUsers() {
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

        conferenceController.addConference(conferenceRequest).shouldBeRight()
        userRepository.addUser(userRequest).shouldBeRight()
        val booking =
            conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName)).shouldBeRight()

        val bookingStatusResponse = conferenceController.cancelBooking(booking.id).shouldBeRight()

        assertEquals(booking.id, bookingStatusResponse.bookingId)
        assertEquals(BookingStatus.CANCELLED, bookingStatusResponse.bookingStatus)

        waitlistingRepository.getTheOldestWaitListingRecordForConference(conferenceName).shouldBeRight().shouldBeNone()
    }

    @Test
    fun shouldCancelConfirmedBookingWithNotifyingTheNextWaitListedUser() {
        val conferenceName = "test conference name"
        val userId = "random uuid"
        val userId2 = "random uuid2"
        val conferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-09-02T06:10:34Z"),
            Instant.parse("2024-09-02T07:10:34Z"),
            1
        )
        val userRequest = AddUserRequest(
            userId,
            "test location",
        )

        val userRequest2 = AddUserRequest(
            userId2,
            "test location 2"
        )

        conferenceController.addConference(conferenceRequest).shouldBeRight()
        userRepository.addUser(userRequest).shouldBeRight()
        userRepository.addUser(userRequest2).shouldBeRight()

        val booking =
            conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName)).shouldBeRight()

        val booking2 = conferenceController.bookConferenceTicket(BookingRequest(userId2, conferenceName)).shouldBeRight()

        val bookingStatusResponse = conferenceController.cancelBooking(booking.id).shouldBeRight()

        assertEquals(booking.id, bookingStatusResponse.bookingId)
        assertEquals(BookingStatus.CANCELLED, bookingStatusResponse.bookingStatus)

        val waitListRecord = waitlistingRepository.getWaitListingRecordByBookingId(booking2.id).shouldBeRight()

        assertTrue(waitListRecord.isRequestSent)
    }

    @Test
    fun shouldCancelConfirmedBookingWithIncrementingConferenceAvailability() {
        val conferenceName = "test conference name"
        val userId = "random uuid"
        val conferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-09-02T06:10:34Z"),
            Instant.parse("2024-09-02T07:10:34Z"),
            1
        )
        val userRequest = AddUserRequest(
            userId,
            "test location",
        )

        conferenceController.addConference(conferenceRequest).shouldBeRight()
        userRepository.addUser(userRequest).shouldBeRight()

        val booking =
            conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName)).shouldBeRight()

        val bookingStatusResponse = conferenceController.cancelBooking(booking.id).shouldBeRight()

        assertEquals(booking.id, bookingStatusResponse.bookingId)
        assertEquals(BookingStatus.CANCELLED, bookingStatusResponse.bookingStatus)

        waitlistingRepository.getTheOldestWaitListingRecordForConference(conferenceName).shouldBeRight().shouldBeNone()
        val conference = conferenceRepository.getConferenceByName(conferenceName).shouldBeRight()
        assertEquals(1, conference.availableSlots)
    }

    @Test
    fun shouldScheduleABackgroundJobWhenNotifyingTheNextWaitListedUser() {
        val conferenceName = "test conference name"
        val userId = "random uuid"
        val userId2 = "random uuid2"
        val userId3 = "random uuid3"
        val conferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-09-02T06:10:34Z"),
            Instant.parse("2024-09-02T07:10:34Z"),
            1
        )
        val userRequest = AddUserRequest(
            userId,
            "test location",
        )

        val userRequest2 = AddUserRequest(
            userId2,
            "test location 2"
        )

        val userRequest3 = AddUserRequest(
            userId3,
            "test location 3"
        )

        conferenceController.addConference(conferenceRequest).shouldBeRight()
        userRepository.addUser(userRequest).shouldBeRight()
        userRepository.addUser(userRequest2).shouldBeRight()
        userRepository.addUser(userRequest3).shouldBeRight()

        val booking =
            conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName)).shouldBeRight()

        val booking2 = conferenceController.bookConferenceTicket(BookingRequest(userId2, conferenceName)).shouldBeRight()

        val booking3 = conferenceController.bookConferenceTicket(BookingRequest(userId3, conferenceName)).shouldBeRight()

        conferenceController.cancelBooking(booking.id).shouldBeRight()

        await().atMost(10, TimeUnit.SECONDS).until { checkForCorrectState(booking2.id, booking3.id) };
    }

    @Test
    fun shouldBeAbleToAcceptTheWaitListRequestPostAnotherUserHasCancelled() {
        val conferenceName = "test conference name"
        val userId = "random uuid"
        val userId2 = "random uuid2"
        val conferenceRequest = AddConferenceRequest(
            conferenceName,
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-09-02T06:10:34Z"),
            Instant.parse("2024-09-02T07:10:34Z"),
            1
        )
        val userRequest = AddUserRequest(
            userId,
            "test location",
        )

        val userRequest2 = AddUserRequest(
            userId2,
            "test location 2"
        )

        conferenceController.addConference(conferenceRequest).shouldBeRight()
        userRepository.addUser(userRequest).shouldBeRight()
        userRepository.addUser(userRequest2).shouldBeRight()

        val booking =
            conferenceController.bookConferenceTicket(BookingRequest(userId, conferenceName)).shouldBeRight()

        val booking2 = conferenceController.bookConferenceTicket(BookingRequest(userId2, conferenceName)).shouldBeRight()

        conferenceController.cancelBooking(booking.id).shouldBeRight()
        val bookingStatusResponse = conferenceController.confirmBooking(booking2.id).shouldBeRight()

        assertEquals(booking2.id, bookingStatusResponse.bookingId)
        assertEquals(BookingStatus.CONFIRMED, bookingStatusResponse.bookingStatus)

        waitlistingRepository.getTheOldestWaitListingRecordForConference(conferenceName).shouldBeRight().shouldBeNone()
    }

    private fun checkForCorrectState(bookingId1: UUID, bookingId2: UUID): Boolean {
        val waitListRecord = waitlistingRepository.getWaitListingRecordByBookingId(bookingId1).shouldBeRight()
        val waitListRecord2 = waitlistingRepository.getWaitListingRecordByBookingId(bookingId2).shouldBeRight()
        return !waitListRecord.isRequestSent && waitListRecord2.isRequestSent
    }
}