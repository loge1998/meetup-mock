package com.motorq.meetup.entity

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ConferenceTable : Table("conferences") {
    val name = text("name").uniqueIndex()
    val location = text("location")
    val topics = text("topics")
    val startDateTime = timestamp("start_datetime")
    val endDateTime = timestamp("end_datetime")
    val availableSlots = integer("available_slots")
}