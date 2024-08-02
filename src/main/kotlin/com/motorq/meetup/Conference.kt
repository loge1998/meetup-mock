package com.motorq.meetup

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
        return (this.endDateTime >= conference.startDatetime)
    }
}
