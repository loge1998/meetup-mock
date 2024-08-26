package com.motorq.meetup.domain

import com.motorq.meetup.dto.User
import java.time.Instant
import java.util.UUID

data class Booking(
    val id: UUID,
    val conferenceName: String,
    val userId: String,
    val status: BookingStatus,
    val timestamp: Instant
)
