package com.motorq.meetup.repositories

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.toOption
import com.motorq.meetup.domain.Booking
import com.motorq.meetup.BookingNotFoundError
import com.motorq.meetup.domain.BookingStatus
import com.motorq.meetup.domain.Conference
import com.motorq.meetup.CustomError
import com.motorq.meetup.dto.User
import com.motorq.meetup.UserNotFoundError
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import org.springframework.stereotype.Repository

@Repository
class BookingRepository {
    private val bookingStore: ConcurrentMap<String, Booking> = ConcurrentHashMap()
    private val userBookings: ConcurrentMap<String, MutableSet<Booking>> = ConcurrentHashMap()

    fun addBooking(user: User, conference: Conference, bookingStatus: BookingStatus): Either<CustomError, Booking> = either {
        val bookingId: String = UUID.randomUUID().toString()
        val booking = Booking(
            bookingId,
            conference,
            user,
            bookingStatus,
            Instant.now()
        )
        bookingStore[bookingId] = booking
        val bookings = userBookings[user.userId] ?: mutableSetOf()
        bookings.add(booking)
        userBookings[user.userId] = bookings
        booking
    }

    fun getBookingsForUserId(userId: String): Either<CustomError, Set<Booking>> {
        return userBookings[userId].toOption().toEither { UserNotFoundError }
    }

    fun getBookingsById(bookingId: String): Either<CustomError, Booking> {
        return bookingStore[bookingId].toOption().toEither { BookingNotFoundError }
    }

    fun clearAll() {
        bookingStore.clear()
        userBookings.clear()
    }
}