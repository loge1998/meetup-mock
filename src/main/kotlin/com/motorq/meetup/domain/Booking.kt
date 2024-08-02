package com.motorq.meetup.domain

import com.motorq.meetup.dto.User
import java.time.Instant

data class Booking(val id: String, val conference: Conference, val user: User, val status: BookingStatus, val timestamp: Instant)
