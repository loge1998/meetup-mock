package com.motorq.meetup.repositories

import arrow.core.Option
import arrow.core.flatMap
import arrow.core.toOption
import com.motorq.meetup.BookingNotFoundError
import com.motorq.meetup.domain.Booking
import com.motorq.meetup.domain.WaitlistRecord
import com.motorq.meetup.entity.BookingsTable
import com.motorq.meetup.entity.WaitlistingTable
import com.motorq.meetup.wrapWithTryCatch
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class WaitlistingRepository {

    fun getTheOldestWaitlistingRecordForConference(conferenceName: String): Option<WaitlistRecord> {
        return WaitlistingTable.selectAll()
            .where { WaitlistingTable.conferenceName eq conferenceName }
            .andWhere { WaitlistingTable.isRequestSent neq true }
            .orderBy(WaitlistingTable.timestamp, SortOrder.ASC)
            .firstOrNull()
            .toOption()
            .map { it.toWaitlistRecord() }
    }

    fun addWaitlistEntry(booking: Booking) = wrapWithTryCatch({
        WaitlistingTable.insert {
            it[bookingId] = booking.id
            it[userId] = booking.userId
            it[conferenceName] = booking.conferenceName
            it[timestamp] = Instant.now()
            it[isRequestSent] = false
            it[slotAvailabilityEndTime] = null
        }
    }, logger)

    fun getWaitlistingRecordByBookingId(bookingId: UUID) = wrapWithTryCatch({
        WaitlistingTable.selectAll()
            .where { WaitlistingTable.bookingId eq bookingId }
            .firstOrNull()
            .toOption()
            .map { it.toWaitlistRecord() }
    }, logger).flatMap { it.toEither { BookingNotFoundError } }

    fun deleteByBookingId(id: UUID) = wrapWithTryCatch({
        WaitlistingTable.deleteWhere { bookingId eq id }
    }, logger)

    fun resetWaitListRecord(bookingId: UUID) = wrapWithTryCatch({
        WaitlistingTable.update({ WaitlistingTable.bookingId eq bookingId }) {
            it[timestamp] = Instant.now()
            it[isRequestSent] = false
            it[slotAvailabilityEndTime] = null
        }
    }, logger)

    fun deleteByUserIdAndConferenceName(userId: String, conferenceName: String) = wrapWithTryCatch({
        WaitlistingTable.deleteWhere { (this.userId eq userId) and (this.conferenceName eq conferenceName)}
    }, logger)

    companion object {
        private val logger = LoggerFactory.getLogger(WaitlistingRepository::class.java)
    }
}

private fun ResultRow.toWaitlistRecord(): WaitlistRecord = WaitlistRecord(
    this[WaitlistingTable.bookingId],
    this[WaitlistingTable.userId],
    this[WaitlistingTable.conferenceName],
    this[WaitlistingTable.timestamp],
    this[WaitlistingTable.isRequestSent],
    this[WaitlistingTable.slotAvailabilityEndTime]
)
