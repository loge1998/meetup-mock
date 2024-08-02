package com.motorq.meetup

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.toOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import org.springframework.stereotype.Repository

@Repository
class UserRepository {
    private val userStore: ConcurrentMap<String, User> = ConcurrentHashMap()

    fun addUser(userRequest: AddUserRequest): Either<CustomError, User> =  either {
        val existingUser = userStore[userRequest.userId].toOption()
        if(existingUser.isSome())
        {
            return UserAlreadyExistError.left()
        }
        val user = userRequest.toUser()
        userStore[userRequest.userId] = user
        user
    }
}
