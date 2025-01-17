package com.flxrs.dankchat.service.twitch.message

import com.flxrs.dankchat.service.irc.IrcMessage

data class RoomState(
    val channel: String,
    val channelId: String = "",
    val tags: Map<RoomStateTag, Int> = mapOf(
        RoomStateTag.EMOTE to 0,
        RoomStateTag.SUBS to 0,
        RoomStateTag.SLOW to 0,
        RoomStateTag.R9K to 0,
        RoomStateTag.FOLLOW to -1,
    )
) {
    val activeStates: BooleanArray
        get() = tags.entries.map { (tag, value) ->
            if (tag == RoomStateTag.FOLLOW) value >= 0 else value > 0
        }.toBooleanArray()

    fun toDisplayText(): String = tags
        .filter { (it.key == RoomStateTag.FOLLOW && it.value >= 0) || it.value > 0 }
        .map {
            when (it.key) {
                RoomStateTag.FOLLOW -> if (it.value == 0) "follow" else "follow(${it.value})"
                RoomStateTag.SLOW   -> "slow(${it.value})"
                else                -> it.key.name.lowercase()
            }
        }.joinToString()

    fun copyFromIrcMessage(msg: IrcMessage): RoomState = copy(
        tags = tags.mapValues { (key, value) -> msg.getRoomStateTag(key, value) },
        channelId = msg.tags["room-id"] ?: channelId
    )

    private fun IrcMessage.getRoomStateTag(tag: RoomStateTag, default: Int): Int = tags[tag.ircTag]?.toIntOrNull() ?: default
}