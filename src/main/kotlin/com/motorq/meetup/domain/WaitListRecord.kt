package com.motorq.meetup.domain

import java.time.Instant
import java.util.UUID

data class WaitListRecord(
    var bookingId: UUID,
    val userId: String,
    val conferenceName: String,
    val timestamp: Instant,
    val isRequestSent: Boolean,
    val slotAvailabilityEndTime: Instant?
)
