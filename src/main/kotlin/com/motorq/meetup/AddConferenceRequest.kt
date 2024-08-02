package com.motorq.meetup

import java.time.Instant

data class AddConferenceRequest(
    val name: String,
    val location: String,
    val topics: String,
    val startDatetime: Instant,
    val endDateTime: Instant,
    val availableSlots: Int
) {
    fun toConference(): Conference = Conference(name, location, topics, startDatetime, endDateTime, availableSlots)
}