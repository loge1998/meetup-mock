package com.motorq.meetup.repositories

import com.motorq.meetup.domain.Booking
import com.motorq.meetup.domain.BookingStatus
import com.motorq.meetup.domain.Conference
import com.motorq.meetup.dto.User
import com.motorq.meetup.entity.WaitlistingTable
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import common.TestContainerRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@SpringBootTest
class WaitlistingRepositoryTest(@Autowired val waitlistingRepository: WaitlistingRepository){

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
            WaitlistingTable.deleteAll()
        }
    }

    @Test
    fun shouldAddWaitlistRecordForAConference() {
        val userId = "random id"
        val user = User(userId, "topics")
        val bookingId = UUID.randomUUID()
        val conference = Conference(
            "test conference name",
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-08-02T06:10:34Z"),
            Instant.parse("2024-08-02T07:10:34Z"),
            30
        )
        val booking = Booking(bookingId, conference.name, user.userId, BookingStatus.WAITLISTED, Instant.now())

        waitlistingRepository.addWaitlistEntry(booking)
        val waitlistRecordOption =
            waitlistingRepository.getTheOldestWaitlistingRecordForConference(conferenceName = conference.name)
        assertTrue { waitlistRecordOption.isSome() }
        waitlistRecordOption.onSome {
            assertEquals(bookingId, it.bookingId)
            assertFalse { it.isRequestSent }
            assertEquals(conference.name, it.conferenceName)
            assertEquals(userId, it.userId)
        }
    }

    @Test
    fun shouldReturnTheOldestWaitlistRecordFromTheQueue() {
        val userId = "random id"
        val user = User(userId, "topics")
        val secondUser = User("random test", "")
        val bookingId = UUID.randomUUID()
        val secondBookingId = UUID.randomUUID()
        val conference = Conference(
            "test conference name",
            "test location",
            "test topic, test topics 2",
            Instant.parse("2024-08-02T06:10:34Z"),
            Instant.parse("2024-08-02T07:10:34Z"),
            30
        )
        val booking = Booking(bookingId, conference.name, user.userId, BookingStatus.WAITLISTED, Instant.now())
        val secondBooking = Booking(secondBookingId, conference.name, secondUser.userId, BookingStatus.WAITLISTED, Instant.now())

        waitlistingRepository.addWaitlistEntry(booking)
        waitlistingRepository.addWaitlistEntry(secondBooking)

        val waitlistRecordOption =
            waitlistingRepository.getTheOldestWaitlistingRecordForConference(conferenceName = conference.name)
        assertTrue { waitlistRecordOption.isSome() }
        waitlistRecordOption.onSome {
            assertEquals(bookingId, it.bookingId)
            assertFalse { it.isRequestSent }
            assertEquals(conference.name, it.conferenceName)
            assertEquals(userId, it.userId)
        }
    }
}