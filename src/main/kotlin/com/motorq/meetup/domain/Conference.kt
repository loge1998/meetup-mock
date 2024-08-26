package com.motorq.meetup.domain

import java.time.Instant

data class Conference(
    val name: String,
    val location: String,
    val topics: String,
    val startDatetime: Instant,
    val endDateTime: Instant,
    val availableSlots: Int
) {
    fun isOverlappingConference(conference: Conference): Boolean {
        return this.startDatetime.isBefore(conference.endDateTime) && conference.startDatetime.isBefore(this.endDateTime)
    }

    fun isSlotAvailable(): Boolean {
        return availableSlots > 0
    }

    fun isStillOpen(): Boolean {
        return (startDatetime.isAfter(Instant.now()))
    }
}
