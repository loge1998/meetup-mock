package com.motorq.meetup

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MeetupApplication

fun main(args: Array<String>) {
    runApplication<MeetupApplication>(*args)
}
