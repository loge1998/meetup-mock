package com.motorq.meetup.controllers

import com.motorq.meetup.UserAlreadyExistError
import com.motorq.meetup.dto.AddUserRequest
import com.motorq.meetup.entity.UserTable
import common.TestContainerRunner
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UserControllerTest(@Autowired val userController: UserController) {

    companion object {
        @JvmStatic
        @BeforeAll
        fun startContainer() {
            TestContainerRunner.startPostgresIfNotRunning()
        }
    }

    @BeforeEach
    fun setup() {
        transaction {
            UserTable.deleteAll()
        }
    }

    @Test
    fun shouldBeAbleToAddAUserWithUniqueUserId() {
        val userRequest = AddUserRequest(
            "random uuid",
            "test location",
        )
        userController.addUser(userRequest).shouldBeRight(userRequest.toUser())
    }

    @Test
    fun shouldThrowUserNameAlreadyExistErrorWhenTheSameNameIsBeingUsed() {
        val userRequest = AddUserRequest(
            "random uuid",
            "test location",
        )
        userController.addUser(userRequest)
        userController.addUser(userRequest).shouldBeLeft(UserAlreadyExistError)
    }
}