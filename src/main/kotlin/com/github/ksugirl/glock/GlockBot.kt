package com.github.ksugirl.glock

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.ChatPermissions
import java.lang.Thread.startVirtualThread
import java.time.Duration
import java.time.Duration.ofSeconds
import java.time.Instant.now
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor

class GlockBot(apiKey: String, private val restrictions: ChatPermissions, restrictionsDuration: Duration) {

  private val restrictionMessage =
    "your post has been deleted because you were shot. Deleted post can be viewed in the admin panel"

  private val bot =
    bot {
      token = apiKey
      dispatch {
        command("shoot", ::shoot)
        message(::checkRestrictions)
      }
    }

  private val repliesExecutor = newSingleThreadExecutor()

  private val restrictionsExecutor = newSingleThreadExecutor()

  private val tempReplySec = ofSeconds(3).toSeconds()

  private val restrictionsDurationSec = restrictionsDuration.toSeconds()

  private val tempReplies = ConcurrentHashMap<Long, ConcurrentHashMap<Long, Long>>()

  private val restrictedUsers = ConcurrentHashMap<Long, ConcurrentHashMap<Long, Long>>()

  private fun shoot(env: CommandHandlerEnvironment) {
    val gunfighter = env.message.from?.id ?: return
    val message = env.message.replyToMessage ?: return
    val chatId = message.chat.id
    if (isRestricted(chatId, gunfighter)) {
      return
    }
    val userId = message.from?.id ?: return
    val messageId = message.messageId
    restrictUser(chatId, userId)
    sendTempReply(chatId, "💥", messageId)
  }

  private fun restrictUser(chatId: Long, userId: Long) {
    val untilDate = now().epochSecond + restrictionsDurationSec
    bot.restrictChatMember(fromId(chatId), userId, restrictions, untilDate)
    restrictionsExecutor.execute {
      restrictedUsers.compute(chatId) { _, users ->
        val restrictedUsers = users ?: ConcurrentHashMap()
        restrictedUsers[userId] = untilDate
        return@compute restrictedUsers
      }
    }
  }

  private fun sendTempReply(chatId: Long, text: String, originalMessageId: Long) {
    val tempMessage = bot.sendMessage(fromId(chatId), text, replyToMessageId = originalMessageId)
    val tempMessageId = tempMessage.get().messageId
    repliesExecutor.execute {
      tempReplies.compute(chatId) { _, replies ->
        val tempReplies = replies ?: ConcurrentHashMap()
        val untilDate = now().epochSecond + tempReplySec
        tempReplies[tempMessageId] = untilDate
        return@compute tempReplies
      }
    }
  }

  fun checkRestrictions() {
    restrictionsExecutor.submit {
      for ((_, users) in restrictedUsers) {
        val it = users.iterator()
        while (it.hasNext()) {
          val (_, expirationMillis) = it.next()
          if (now().epochSecond >= expirationMillis) {
            it.remove()
          }
        }
      }
    }.get()
  }

  private fun checkRestrictions(env: MessageHandlerEnvironment) {
    val user = env.message.from ?: return
    val messageId = env.message.messageId
    val userId = user.id
    val chatId = env.message.chat.id
    val untilDate = getRestrictionDateUntil(chatId, userId) ?: return
    val username = user.username
    val appeal = if (username == null) user.firstName else "@$username"
    if (now().epochSecond < untilDate) {
      sendTempReply(chatId, "$appeal, $restrictionMessage", messageId)
      bot.deleteMessage(fromId(chatId), messageId)
    }
  }

  private fun getRestrictionDateUntil(chatId: Long, userId: Long): Long? {
    val chatRestrictions = restrictedUsers[chatId] ?: return null
    return chatRestrictions[userId]
  }

  private fun isRestricted(chatId: Long, userId: Long): Boolean {
    return getRestrictionDateUntil(chatId, userId) != null
  }

  fun cleanTempReplies() {
    repliesExecutor.submit {
      for ((chatId, repliesIds) in tempReplies) {
        val it = repliesIds.iterator()
        while (it.hasNext()) {
          val (replyId, expirationSec) = it.next()
          if (now().epochSecond >= expirationSec) {
            bot.deleteMessage(fromId(chatId), replyId)
            it.remove()
          }
        }
      }
    }.get()
  }

  fun startPollingAsync() {
    startVirtualThread(bot::startPolling)
  }
}