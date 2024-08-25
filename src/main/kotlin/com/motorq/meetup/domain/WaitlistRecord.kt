package com.motorq.meetup.domain

import java.time.Instant

data class WaitlistRecord(
    var bookingId: String,
    val userId: String,
    val conferenceName: String,
    val timestamp: Instant,
    val isRequestSent: Boolean,
    val slotAvailabilityEndTime: Instant?
)
