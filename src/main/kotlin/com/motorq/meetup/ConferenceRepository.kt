package com.motorq.meetup

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.toOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import org.springframework.stereotype.Repository

@Repository
class ConferenceRepository {

    private val conferenceStore: ConcurrentMap<String, Conference> = ConcurrentHashMap()

    fun addConference(addConferenceRequest: AddConferenceRequest): Either<CustomError, Conference> = either {
        val existingConference = conferenceStore[addConferenceRequest.name].toOption()
        if(existingConference.isSome())
        {
            return ConferenceAlreadyExistError.left()
        }
        val conference = addConferenceRequest.toConference()
        conferenceStore[addConferenceRequest.name] = conference
        conference
    }

    fun getConferenceByName(conferenceName: String): Either<CustomError, Conference> {
        return conferenceStore[conferenceName].toOption().toEither { ConferenceNotFoundError }
    }

    fun putConference(conference: Conference) {
        conferenceStore[conference.name] = conference
    }

    fun clearAll() {
        conferenceStore.clear()
    }
}
