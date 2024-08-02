package com.motorq.meetup

sealed class CustomError(val message: String)

data object ConferenceAlreadyExistError : CustomError("Conference already exist!")
data object UserAlreadyExistError: CustomError("User already exist with the same id")