package com.motorq.meetup.domain

import java.time.Instant
import java.util.*

data class Booking(
    val id: UUID,
    val conferenceName: String,
    val userId: String,
    val status: BookingStatus,
    val timestamp: Instant,
) {
    fun isInWaitList() = (status == BookingStatus.WAITLISTED)
    fun isConfirmed() = (status == BookingStatus.CONFIRMED)
}
