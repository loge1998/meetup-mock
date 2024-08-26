package com.motorq.meetup.repositories

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import arrow.core.toOption
import com.motorq.meetup.dto.AddUserRequest
import com.motorq.meetup.CustomError
import com.motorq.meetup.domain.User
import com.motorq.meetup.UserAlreadyExistError
import com.motorq.meetup.UserNotFoundError
import com.motorq.meetup.entity.UserTable
import com.motorq.meetup.wrapWithTryCatch
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class UserRepository {

    fun addUser(userRequest: AddUserRequest): Either<CustomError, User> = either {
        val existingUser = getUserByUserId(userRequest.userId)
        if(existingUser.isRight())
            raise(UserAlreadyExistError)
        insertNewUser(userRequest).bind()
    }

    fun getUserByUserId(userId: String): Either<CustomError, User> = wrapWithTryCatch(
        {
            UserTable
                .selectAll()
                .where { UserTable.userId eq userId }
                .firstOrNull()
                .toOption()
                .map { it.toUser() }
        }, logger
    ).flatMap { it.toEither { UserNotFoundError } }

    private fun insertNewUser(userRequest: AddUserRequest): Either<CustomError, User> = wrapWithTryCatch({
        UserTable.insert {
            it[userId] = userRequest.userId
            it[interestedTopics] = userRequest.interestedTopics
        }
        User(userRequest.userId, userRequest.interestedTopics)
    }, logger)

    companion object {
        private val logger = LoggerFactory.getLogger(UserRepository::class.java)
    }
}

private fun ResultRow.toUser() = User(this[UserTable.userId], this[UserTable.interestedTopics])

