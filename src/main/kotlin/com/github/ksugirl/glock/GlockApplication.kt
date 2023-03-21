package com.github.ksugirl.glock

import com.github.kotlintelegrambot.entities.ChatPermissions
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Duration.ofMinutes
import java.util.concurrent.TimeUnit.SECONDS

@EnableScheduling
@SpringBootApplication
class GlockApplication {

  @Value("\${telegram.api.token}")
  private lateinit var apiToken: String

  @Bean
  fun restrictions(): ChatPermissions {
    return ChatPermissions(
      canSendMessages = false,
      canSendMediaMessages = false,
      canSendPolls = false,
      canSendOtherMessages = false,
      canAddWebPagePreviews = false,
      canChangeInfo = false,
      canInviteUsers = false,
      canPinMessages = false
    )
  }

  @Bean
  fun taskScheduler(): TaskScheduler {
    val scheduler = ThreadPoolTaskScheduler()
    scheduler.poolSize = 2
    return scheduler
  }

  @Bean
  fun glockBot(): GlockBot {
    val bot = GlockBot(apiToken, restrictions(), ofMinutes(5))
    bot.startPollingAsync()
    return bot
  }

  @Scheduled(fixedDelay = 3, timeUnit = SECONDS)
  fun cleanTempMessages() {
    glockBot().cleanTempMessages()
  }

  @Scheduled(fixedDelay = 1, timeUnit = SECONDS)
  fun checkRestrictions() {
    glockBot().checkRestrictions()
  }
}

fun main(args: Array<String>) {
  runApplication<GlockApplication>(*args)
}