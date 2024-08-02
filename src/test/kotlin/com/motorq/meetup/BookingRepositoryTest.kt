package com.motorq.meetup

import arrow.core.right
import com.motorq.meetup.domain.BookingStatus
import com.motorq.meetup.domain.Conference
import com.motorq.meetup.dto.User
import com.motorq.meetup.repositories.BookingRepository
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BookingRepositoryTest {

    private val bookingRepository = BookingRepository()

    @Test
    fun shouldBeAbleToAddBookingsForAnUser() {
        val userId = "random id"
        val user = User(userId, "topics")
        val conference = Conference(
            "test conference name",
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-08-02T06:10:34Z"),
            Instant.parse("2024-08-02T07:10:34Z"),
            30
        )
        val bookings = bookingRepository.addBooking(user, conference, BookingStatus.CONFIRMED)
        assertTrue(bookings.isRight())
        bookingRepository.getBookingsForUserId(userId).onRight {
            assertEquals(1, it.size)
            assertEquals(it.first().right(), bookings)
        }
    }
}