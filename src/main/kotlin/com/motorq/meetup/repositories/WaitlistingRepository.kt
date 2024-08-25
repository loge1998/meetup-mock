package com.motorq.meetup.repositories

import arrow.core.Option
import arrow.core.toOption
import com.motorq.meetup.domain.Booking
import com.motorq.meetup.domain.WaitlistRecord
import com.motorq.meetup.entity.WaitlistingTable
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class WaitlistingRepository {

    fun getTheOldestWaitlistingRecordForConference(conferenceName: String): Option<WaitlistRecord> {
        return WaitlistingTable.selectAll()
            .where { WaitlistingTable.conferenceName eq conferenceName }
            .andWhere { WaitlistingTable.isRequestSent neq true}
            .orderBy(WaitlistingTable.timestamp, SortOrder.ASC)
            .firstOrNull()
            .toOption()
            .map{it.toWaitlistRecord()}
    }

    fun addWaitlistEntry(booking: Booking) {
        WaitlistingTable.insert {
            it[bookingId] = UUID.fromString(booking.id)
            it[userId] = booking.user.userId
            it[conferenceName] = booking.conference.name
            it[timestamp] = Instant.now()
            it[isRequestSent] = false
            it[slotAvailabilityEndTime] = null
        }
    }
}

private fun ResultRow.toWaitlistRecord(): WaitlistRecord = WaitlistRecord(
    this[WaitlistingTable.bookingId].toString(),
    this[WaitlistingTable.userId],
    this[WaitlistingTable.conferenceName],
    this[WaitlistingTable.timestamp],
    this[WaitlistingTable.isRequestSent],
    this[WaitlistingTable.slotAvailabilityEndTime]
)
