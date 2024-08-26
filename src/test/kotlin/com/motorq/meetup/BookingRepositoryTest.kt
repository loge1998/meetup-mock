package com.motorq.meetup

import com.motorq.meetup.domain.BookingStatus
import com.motorq.meetup.domain.Conference
import com.motorq.meetup.dto.User
import com.motorq.meetup.entity.BookingsTable
import com.motorq.meetup.repositories.BookingRepository
import common.TestContainerRunner
import io.kotest.assertions.arrow.core.shouldBeRight
import java.time.Instant
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BookingRepositoryTest(@Autowired val bookingRepository: BookingRepository) {

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
            BookingsTable.deleteAll()
        }
    }

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
        val booking = bookingRepository.addBooking(user, conference, BookingStatus.CONFIRMED).shouldBeRight()
        val userBookings = bookingRepository.getBookingsForUserId(userId).shouldBeRight()
        assertEquals(1, userBookings.size)
        assertEquals(userBookings.first(), booking)
    }
}