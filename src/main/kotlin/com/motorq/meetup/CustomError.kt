package com.motorq.meetup

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import java.sql.SQLException
import org.slf4j.Logger
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

sealed class CustomError(val message: String)

data object ConferenceAlreadyExistError : CustomError("Conference already exist!")
data object ConferenceNotFoundError: CustomError("Conference not found!.")
data object UserAlreadyExistError: CustomError("User already exist with the same id!")
data object UserNotFoundError: CustomError("User not found!")
data object BookingNotFoundError: CustomError("Bookings not found!")
data object ConferenceStartedError: CustomError("Conference has already started!")
data object ExistingBookingFoundError: CustomError("User has an existing booking for the conference!")
data object OverlappingConferenceError: CustomError("User has an existing overlapping bookings!")
data object DatabaseOperationFailedError : CustomError("DB operation failed!")

fun <T> wrapWithTryCatch(function1: () -> T, log: Logger): Either<CustomError, T> = either {
    try {
        function1()
    } catch (ex: SQLException) {
        log.error(ex.message)
        raise(DatabaseOperationFailedError)
    }
}

fun <Error> Raise<Error>.ensureNot(condition: Boolean, raise: () -> Error) {
    return if (!condition) Unit else raise(raise())
}