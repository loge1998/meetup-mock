package com.motorq.meetup.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import arrow.core.traverse
import com.motorq.meetup.dto.BookingRequest
import com.motorq.meetup.ConferenceStartedError
import com.motorq.meetup.CustomError
import com.motorq.meetup.ExistingBookingFoundError
import com.motorq.meetup.OverlappingConferenceError
import com.motorq.meetup.domain.Booking
import com.motorq.meetup.domain.BookingStatus
import com.motorq.meetup.domain.Conference
import com.motorq.meetup.domain.User
import com.motorq.meetup.dto.BookingStatusResponse
import com.motorq.meetup.ensureNot
import com.motorq.meetup.repositories.BookingRepository
import com.motorq.meetup.repositories.ConferenceRepository
import com.motorq.meetup.repositories.UserRepository
import com.motorq.meetup.repositories.WaitlistingRepository
import java.util.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class BookingService(
    private val conferenceRepository: ConferenceRepository,
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository,
    private val waitlistingRepository: WaitlistingRepository
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
            waitlistingRepository.addWaitlistEntry(booking).onLeft { rollback() }.bind()
            booking
        }
    }

    private fun decrementAvailableSlot(conference: Conference) =
        conferenceRepository.decrementConferenceAvailableSlot(conference.name)

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

    private fun enrichWithWaitListingDetails(booking: Booking): Either<CustomError, BookingStatusResponse> {
        if (booking.isInWaitList())
        {
            return waitlistingRepository.getWaitlistingRecordByBookingId(booking.id)
                .map { BookingStatusResponse(booking.id, booking.status, it.isRequestSent, it.slotAvailabilityEndTime)}
        }
        return BookingStatusResponse(booking.id, booking.status, null, null).right()
    }
}