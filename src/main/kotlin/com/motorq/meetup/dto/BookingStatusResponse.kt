package com.motorq.meetup.dto

import com.motorq.meetup.domain.BookingStatus
import java.time.Instant
import java.util.*

data class BookingStatusResponse(
    val bookingId: UUID,
    val bookingStatus: BookingStatus,
    val isSlotAvailable: Boolean?,
    val confirmationEndTime: Instant?,
)
