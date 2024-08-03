package com.motorq.meetup.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.motorq.meetup.dto.BookingRequest
import com.motorq.meetup.ConferenceStartedError
import com.motorq.meetup.CustomError
import com.motorq.meetup.ExistingBookingFoundError
import com.motorq.meetup.NoSlotsAvailableError
import com.motorq.meetup.OverlappingConferenceError
import com.motorq.meetup.domain.Booking
import com.motorq.meetup.domain.BookingStatus
import com.motorq.meetup.domain.Conference
import com.motorq.meetup.repositories.BookingRepository
import com.motorq.meetup.repositories.ConferenceRepository
import com.motorq.meetup.repositories.UserRepository
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class BookingService(
    private val conferenceRepository: ConferenceRepository,
    private val userRepository: UserRepository,
    private val bookingRepository: BookingRepository
) {
    fun bookSlot(bookingRequest: BookingRequest): Either<CustomError, Booking> = either {
        val conference = conferenceRepository.getConferenceByName(bookingRequest.conferenceName).bind()
        val user = userRepository.getUserByUserId(bookingRequest.userId).bind()
        validateBookingRequest(conference).bind()
        val userBookings = bookingRepository.getBookingsForUserId(user.userId)
        checkIfUserHasPreviousBooking(userBookings, conference).bind()
        checkIfUserHasAnyOverlappingConference(userBookings, conference).bind()
        val bookings = bookingRepository.addBooking(user, conference, BookingStatus.CONFIRMED).bind()
        decrementAvailableSlot(conference)
        bookings
    }

    private fun decrementAvailableSlot(conference: Conference) {
        val newConference = conference.copy(availableSlots = conference.availableSlots - 1)
        conferenceRepository.putConference(newConference)
    }

    private fun validateBookingRequest(conference: Conference): Either<CustomError, Unit> = either {
        ensure(conference.startDatetime.isAfter(Instant.now())) {
            raise(ConferenceStartedError)
        }
        ensure(conference.availableSlots > 0) {
            raise(NoSlotsAvailableError)
        }
    }

    private fun checkIfUserHasPreviousBooking(
        userBookings: Set<Booking>,
        conference: Conference
    ): Either<CustomError, Unit> = either {
        val isAlreadyBooked = userBookings.any { it.conference == conference }
        ensure(!isAlreadyBooked)
        {
            raise(ExistingBookingFoundError)
        }
    }

    private fun checkIfUserHasAnyOverlappingConference(userBookings: Set<Booking>, conference: Conference): Either<CustomError, Unit> = either {
        val isOverlappingConference = userBookings.any { it.conference.isOverlappingConference(conference) }
        ensure(!isOverlappingConference)
        {
            raise(OverlappingConferenceError)
        }
    }
}