package com.motorq.meetup

import arrow.core.Either
import org.springframework.web.bind.annotation.RestController

@RestController
class ConferenceController(
    private val conferenceRepository: ConferenceRepository,
    private val bookingService: BookingService
) {

    fun addConference(addConferenceRequest: AddConferenceRequest): Either<CustomError, Conference> {
        return conferenceRepository.addConference(addConferenceRequest)
    }

    fun bookConferenceTicket(bookingRequest: BookingRequest): Either<CustomError, Booking> {
        return bookingService.bookSlot(bookingRequest)
    }
}