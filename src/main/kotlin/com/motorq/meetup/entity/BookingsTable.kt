package com.motorq.meetup.entity

import com.motorq.meetup.domain.BookingStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object BookingsTable : Table("bookings") {
    val id = uuid("id").uniqueIndex()
    val conferenceName = text("conference_name").index()
    val userId = text("user_id").index()
    val timestamp = timestamp("created_timestamp")
    val status = enumerationByName("status", length = 50, BookingStatus::class)
}