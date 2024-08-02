package com.motorq.meetup

sealed class CustomError(val message: String)

data object ConferenceAlreadyExistError : CustomError("Conference already exist!")
data object ConferenceNotFoundError: CustomError("Conference not found!.")
data object UserAlreadyExistError: CustomError("User already exist with the same id!")
data object UserNotFoundError: CustomError("User not found!")
data object BookingNotFoundError: CustomError("Bookings not found!")
data object ConferenceStartedError: CustomError("Conference has already started!")
data object NoSlotsAvailableError: CustomError("No slots available error!")
data object ExistingBookingFoundError: CustomError("User has an existing booking for the conference!")
data object OverlappingConferenceError: CustomError("User has an existing overlapping bookings!")