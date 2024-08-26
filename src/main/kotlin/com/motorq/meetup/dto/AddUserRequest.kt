package com.motorq.meetup.dto

import com.motorq.meetup.domain.User

data class AddUserRequest(val userId: String, val interestedTopics: String) {
    fun toUser() = User(userId, interestedTopics)
}
