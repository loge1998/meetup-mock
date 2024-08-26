package com.motorq.meetup.repositories

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import arrow.core.toOption
import com.motorq.meetup.ConferenceAlreadyExistError
import com.motorq.meetup.ConferenceNotFoundError
import com.motorq.meetup.CustomError
import com.motorq.meetup.DatabaseOperationFailedError
import com.motorq.meetup.catchUniqueConstraintViolation
import com.motorq.meetup.domain.Conference
import com.motorq.meetup.dto.AddConferenceRequest
import com.motorq.meetup.entity.ConferenceTable
import com.motorq.meetup.filterOrError
import com.motorq.meetup.wrapWithTryCatch
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class ConferenceRepository {

    fun addConference(addConferenceRequest: AddConferenceRequest): Either<CustomError, Conference> =
        catchUniqueConstraintViolation({
            ConferenceTable.insert {
                it[name] = addConferenceRequest.name
                it[location] = addConferenceRequest.location
                it[topics] = addConferenceRequest.topics
                it[startDateTime] = addConferenceRequest.startDatetime
                it[endDateTime] = addConferenceRequest.endDateTime
                it[availableSlots] = addConferenceRequest.availableSlots
            }
            addConferenceRequest.toConference()
        }, ConferenceAlreadyExistError, logger)

    fun getConferenceByName(conferenceName: String): Either<CustomError, Conference> = wrapWithTryCatch({
        ConferenceTable.selectAll()
            .where { ConferenceTable.name eq conferenceName }
            .firstOrNull()
            .toOption()
            .map { it.toConference() }
    }, logger).flatMap { it.toEither { ConferenceNotFoundError } }

    fun decrementConferenceAvailableSlot(conferenceName: String): Either<CustomError, Int> =
        wrapWithTryCatch({
            transaction {
                val currentValue = ConferenceTable.select(ConferenceTable.availableSlots)
                    .where { ConferenceTable.name eq conferenceName }
                    .forUpdate()
                    .map { it[ConferenceTable.availableSlots] }
                    .firstOrNull()

                var updatedRows = -1
                if (currentValue != null && currentValue > 1) {
                    updatedRows = ConferenceTable.update({ ConferenceTable.name eq conferenceName }) {
                        with(SqlExpressionBuilder) {
                            it.update(availableSlots, availableSlots - 1)
                        }
                    }
                }
                updatedRows
            }
        }, logger).flatMap {
            either {
                when (it) {
                    -1 -> raise(DatabaseOperationFailedError)
                    0 -> raise(ConferenceNotFoundError)
                    else -> it
                }
            }
        }

    fun incrementConferenceAvailableSlot(conferenceName: String): Either<CustomError, Int> =
        wrapWithTryCatch({
            ConferenceTable.update({ ConferenceTable.name eq conferenceName }) {
                with(SqlExpressionBuilder) {
                    it.update(availableSlots, availableSlots + 1)
                }
            }
        }, logger).flatMap {
            it.filterOrError({ it > 0 }, ConferenceNotFoundError)
        }

    companion object {
        private val logger = LoggerFactory.getLogger(ConferenceRepository::class.java)
    }
}

private fun ResultRow.toConference() =
    Conference(
        this[ConferenceTable.name],
        this[ConferenceTable.location],
        this[ConferenceTable.topics],
        this[ConferenceTable.startDateTime],
        this[ConferenceTable.endDateTime],
        this[ConferenceTable.availableSlots]
    )
