package com.motorq.meetup.controllers

import arrow.core.Either
import com.motorq.meetup.dto.AddUserRequest
import com.motorq.meetup.CustomError
import com.motorq.meetup.dto.User
import com.motorq.meetup.repositories.UserRepository
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(private val userRepository: UserRepository) {
    fun addUser(userRequest: AddUserRequest): Either<CustomError, User> {
        return userRepository.addUser(userRequest)
    }
}