package com.motorq.meetup

data class AddUserRequest(val userId: String, val interestedTopics: String) {
    fun toUser() = User(userId, interestedTopics)
}
