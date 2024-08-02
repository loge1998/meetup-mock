package com.motorq.meetup

import arrow.core.Either
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(private val userRepository: UserRepository) {
    fun addUser(userRequest: AddUserRequest): Either<CustomError, User> {
        return userRepository.addUser(userRequest)
    }
}