package com.motorq.meetup.controllers

import arrow.core.Either
import com.motorq.meetup.dto.AddConferenceRequest
import com.motorq.meetup.domain.Booking
import com.motorq.meetup.dto.BookingRequest
import com.motorq.meetup.service.BookingService
import com.motorq.meetup.domain.Conference
import com.motorq.meetup.repositories.ConferenceRepository
import com.motorq.meetup.CustomError
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