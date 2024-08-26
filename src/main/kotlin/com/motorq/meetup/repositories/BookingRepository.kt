package com.motorq.meetup.repositories

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import arrow.core.toOption
import com.motorq.meetup.domain.Booking
import com.motorq.meetup.BookingNotFoundError
import com.motorq.meetup.domain.BookingStatus
import com.motorq.meetup.domain.Conference
import com.motorq.meetup.CustomError
import com.motorq.meetup.dto.User
import com.motorq.meetup.UserNotFoundError
import com.motorq.meetup.entity.BookingsTable
import com.motorq.meetup.wrapWithTryCatch
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class BookingRepository {

    fun addBooking(user: User, conference: Conference, bookingStatus: BookingStatus): Either<CustomError, Booking> =
        wrapWithTryCatch({
            val bookingId = UUID.randomUUID()
            val createdTimestamp = Instant.now()
            val booking = Booking(
                bookingId,
                conference.name,
                user.userId,
                bookingStatus,
                createdTimestamp
            )
            BookingsTable.insert {
                it[id] = bookingId
                it[conferenceName] = conference.name
                it[userId] = user.userId
                it[status] = bookingStatus
                it[timestamp] = createdTimestamp
            }
            booking
        }, logger)

    fun getBookingsForUserId(userId: String): Either<CustomError, Set<Booking>> = wrapWithTryCatch({
        BookingsTable.selectAll()
            .where { BookingsTable.userId eq userId }
            .map { it.toBooking() }
            .toSet()
    }, logger)

    companion object {
        private val logger = LoggerFactory.getLogger(BookingRepository::class.java)
    }
}

private fun ResultRow.toBooking() = Booking(
    this[BookingsTable.id],
    this[BookingsTable.conferenceName],
    this[BookingsTable.userId],
    this[BookingsTable.status],
    this[BookingsTable.timestamp]
)
