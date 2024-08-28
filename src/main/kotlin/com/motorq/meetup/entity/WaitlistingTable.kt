package com.motorq.meetup.entity

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object WaitlistingTable : Table("waitlisting_records") {
    val bookingId = uuid("booking_id").uniqueIndex()
    val conferenceName = text("conference_name").index()
    val userId = text("user_id").index()
    val timestamp = timestamp("created_timestamp")
    val isRequestSent = bool("request_sent")
    val slotAvailabilityEndTime = timestamp("slot_availability_end_time").nullable()
}