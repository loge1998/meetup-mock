package com.motorq.meetup.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.toOption
import com.motorq.meetup.dto.AddUserRequest
import com.motorq.meetup.CustomError
import com.motorq.meetup.dto.User
import com.motorq.meetup.UserAlreadyExistError
import com.motorq.meetup.UserNotFoundError
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

    fun getUserByUserId(userId: String): Either<CustomError, User> {
        return userStore[userId].toOption().toEither { UserNotFoundError }
    }

    fun clearAll() {
        userStore.clear()
    }
}
