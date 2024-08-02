package com.motorq.meetup

import com.motorq.meetup.controllers.UserController
import com.motorq.meetup.dto.AddUserRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UserControllerTest(@Autowired val userController: UserController) {

    @Test
    fun shouldBeAbleToAddAUserWithUniqueUserId() {
        val userRequest = AddUserRequest(
            "random uuid",
            "test location",
        )
        val response = userController.addUser(userRequest)
        assertTrue(response.isRight())
        response.onRight {
            kotlin.test.assertEquals(userRequest.toUser(), it)
        }
    }

    @Test
    fun shouldThrowConferenceNameAlreadyExistErrorWhenTheSameNameIsBeingUsed() {
        val userRequest = AddUserRequest(
            "random uuid",
            "test location",
        )
        userController.addUser(userRequest)
        val response = userController.addUser(userRequest)

        assertTrue(response.isLeft())
        response.onLeft {
            kotlin.test.assertEquals(UserAlreadyExistError, it)
        }
    }
}