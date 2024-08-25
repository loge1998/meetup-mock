package com.motorq.meetup.entity

import org.jetbrains.exposed.sql.Table

object UserTable: Table("users") {
    val userId = text("user_id").uniqueIndex()
    val interestedTopics = text("interested_topics")
}