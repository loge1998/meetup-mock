package com.motorq.meetup.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import arrow.core.traverse
import com.motorq.meetup.dto.BookingRequest
import com.motorq.meetup.ConferenceStartedError
import com.motorq.meetup.CustomError
import com.motorq.meetup.ExistingBookingFoundError
import com.motorq.meetup.OverlappingConferenceError
import com.motorq.meetup.WrongRequestError
import com.motorq.meetup.domain.Booking
import com.motorq.meetup.domain.BookingStatus
import com.motorq.meetup.domain.Conference
import com.motorq.meetup.domain.User
import com.motorq.meetup.domain.WaitListRecord
import com.motorq.meetup.dto.BookingStatusResponse
import com.motorq.meetup.ensureNot
import com.motorq.meetup.repositories.BookingRepository
import com.motorq.meetup.repositories.ConferenceRepository
import com.motorq.meetup.repositories.UserRepository
import com.motorq.meetup.repositories.WaitListingRepository
import com.motorq.meetup.wrapWithTryCatch
import java.lang.RuntimeException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class BookingService(
    private val conferenceRepository: ConferenceRepository,
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository,
    private val waitlistingRepository: WaitListingRepository,
    @Value("\${job-scheduler.time-to-wait}") private val timeToWait: String
) {
    fun bookSlot(bookingRequest: BookingRequest): Either<CustomError, Booking> = either {
        val conference = conferenceRepository.getConferenceByName(bookingRequest.conferenceName).bind()
        val user = userRepository.getUserByUserId(bookingRequest.userId).bind()
        val userBookings = bookingRepository.getBookingsForUserId(user.userId).bind()
        checkIfValidRequest(conference, userBookings).bind()
        val bookings = bookSlotBasedOnAvailability(user, conference).bind()
        bookings
    }

    fun getBookingStatus(bookingId: UUID): Either<CustomError, BookingStatusResponse> {
        return bookingRepository.getBookingsById(bookingId)
            .flatMap {enrichWithWaitListingDetails(it)}
    }

    fun cancelBooking(bookingId: UUID): Either<CustomError, BookingStatusResponse> = either {
        val booking = bookingRepository.getBookingsById(bookingId).bind()
        validCancelBookingRequest(booking).bind()
        bookingRepository.updateStatus(bookingId, BookingStatus.CANCELLED).bind()
        waitlistingRepository.deleteByBookingId(bookingId).bind()
        if(booking.status == BookingStatus.CONFIRMED) {
            notifyNextWaitListedUser(booking.conferenceName).bind()
        }
        getBookingStatus(bookingId).bind()
    }

    fun confirmBooking(bookingId: UUID) = either {
        val booking = bookingRepository.getBookingsById(bookingId).bind()
        val waitlistRecord = waitlistingRepository.getWaitListingRecordByBookingId(bookingId).bind()
        validConfirmBookingRequest(booking, waitlistRecord).bind()
        bookingRepository.updateStatus(bookingId, BookingStatus.CONFIRMED).bind()
        removeUserFromOverlappingConferenceWaitListQueue(booking.userId, booking.conferenceName).bind()
        getBookingStatus(bookingId).bind()
    }

    private fun validConfirmBookingRequest(booking: Booking, waitListRecord: WaitListRecord) = either {
        ensure(booking.status == BookingStatus.WAITLISTED) {
            raise(WrongRequestError("Provided booking is not in waitlisting"))
        }

        ensure(
            waitListRecord.isRequestSent &&
            waitListRecord.slotAvailabilityEndTime != null &&
            waitListRecord.slotAvailabilityEndTime.isAfter(Instant.now())
        ) {
            raise(WrongRequestError("Provided booking is not eligible for confirmation."))
        }
    }

    private fun validCancelBookingRequest(booking: Booking): Either<CustomError, Unit> = either {
        ensure(booking.status != BookingStatus.CANCELLED) {
            raise(WrongRequestError("Provided booking is already cancelled"))
        }
    }

    private fun checkIfValidRequest(
        conference: Conference,
        userBookings: Set<Booking>
    ) = either {
        checkIfConferenceIsStillOpen(conference).bind()
        checkIfUserHasPreviousBooking(userBookings, conference).bind()
        checkIfUserHasAnyOverlappingConference(userBookings, conference).bind()
    }

    private fun checkIfConferenceIsStillOpen(conference: Conference): Either<CustomError, Unit> = either {
        ensure(conference.isStillOpen()) {
            raise(ConferenceStartedError)
        }
    }

    private fun checkIfUserHasPreviousBooking(
        userBookings: Set<Booking>,
        conference: Conference
    ): Either<CustomError, Unit> = either {
        val isAlreadyBooked = userBookings.any { it.conferenceName == conference.name }
        ensure(!isAlreadyBooked)
        {
            raise(ExistingBookingFoundError)
        }
    }

    private fun checkIfUserHasAnyOverlappingConference(
        userBookings: Set<Booking>,
        conference: Conference
    ): Either<CustomError, Unit> {
        return userBookings.traverse { conferenceRepository.getConferenceByName(it.conferenceName) }
            .map { it.any { bookedConference -> conference.isOverlappingConference(bookedConference) } }
            .flatMap { either { ensureNot(it) { raise(OverlappingConferenceError) } } }
    }

    private fun bookSlotBasedOnAvailability(
        user: User,
        conference: Conference
    ): Either<CustomError, Booking> {
        return when (conference.isSlotAvailable()) {
            true -> handleSuccessfulBooking(user, conference)
            false -> addUserToWaitlist(user, conference)
        }
    }

    private fun handleSuccessfulBooking(user: User, conference: Conference) = either {
        transaction {
            decrementAvailableSlot(conference)
            bookingRepository.addBooking(user, conference, BookingStatus.CONFIRMED).onLeft { rollback() }.bind()
        }
    }

    private fun addUserToWaitlist(user: User, conference: Conference) = either {
        transaction {
            val booking = bookingRepository.addBooking(user, conference, BookingStatus.WAITLISTED).bind()
            waitlistingRepository.addWaitListEntry(booking).onLeft { rollback() }.bind()
            booking
        }
    }

    private fun decrementAvailableSlot(conference: Conference) =
        conferenceRepository.decrementConferenceAvailableSlot(conference.name)

    private fun enrichWithWaitListingDetails(booking: Booking): Either<CustomError, BookingStatusResponse> {
        if (booking.isInWaitList())
        {
            return waitlistingRepository.getWaitListingRecordByBookingId(booking.id)
                .map { BookingStatusResponse(booking.id, booking.status, it.isRequestSent, it.slotAvailabilityEndTime)}
        }
        return BookingStatusResponse(booking.id, booking.status, null, null).right()
    }

    private fun notifyNextWaitListedUser(conferenceName: String): Either<CustomError, Unit> = either {
        val waitListRecord = waitlistingRepository.getTheOldestWaitListingRecordForConference(conferenceName).bind()
        waitListRecord.map {
            scheduleBackgroundJobToCheckAcceptance(it).bind()
            waitlistingRepository.setWaitListEndTime(it.bookingId, Instant.now().plusSeconds(timeToWait.toLong())).bind()
        }.getOrElse { conferenceRepository.incrementConferenceAvailableSlot(conferenceName).bind() }
    }

    private fun scheduleBackgroundJobToCheckAcceptance(it: WaitListRecord) =
        wrapWithTryCatch({
            BackgroundJob.schedule(
                Instant.now().plusSeconds(timeToWait.toLong())
            ) { checkForAcceptanceOfWaitList(it.bookingId, it.conferenceName) }
        }, logger)

    fun checkForAcceptanceOfWaitList(bookingId: UUID, conferenceName: String) = either {
        logger.info("Running the scheduled Job with $bookingId and $conferenceName")
        val booking = bookingRepository.getBookingsById(bookingId).bind()
        if(booking.status != BookingStatus.CONFIRMED) {
            waitlistingRepository.resetWaitListRecord(bookingId).bind()
            notifyNextWaitListedUser(conferenceName).bind()
        }
        else {
            logger.info("User has accepted the request. Closing the scheduled job successfully.")
        }
    }.onLeft { throw RuntimeException("Operation failed") }

    private fun removeUserFromOverlappingConferenceWaitListQueue(userId: String, conferenceName: String) = either {
        val overlappingConferences = conferenceRepository.getAllOverlappingConference(conferenceName).bind()
        overlappingConferences.traverse { waitlistingRepository.deleteByUserIdAndConferenceName(userId, it) }.bind()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BookingService::class.java)
    }
}