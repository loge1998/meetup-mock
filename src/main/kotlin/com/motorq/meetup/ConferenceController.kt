package com.motorq.meetup

import arrow.core.Either
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RestController

@RestController
class ConferenceController(private val conferenceRepository: ConferenceRepository) {

    fun addConference(addConferenceRequest: AddConferenceRequest): Either<CustomError, Conference> {
        return conferenceRepository.addConference(addConferenceRequest)
    }

    fun bookConferenceTicket(bookingRequest: BookingRequest) {


    }
}