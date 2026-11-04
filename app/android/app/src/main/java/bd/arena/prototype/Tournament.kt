package bd.arena.prototype

data class Tournament(val id: String, val title: String, val mode: String, val time: String, val joined: Int, val capacity: Int, val closed: Boolean = false, val fee: Long = 0, val rules: String = "", val prize: Long = 0, val description: String = "", val image: String = mode, val startsAtMillis: Long = 0, val map: String = "", val perKill: Long = 0, val matchStatus: String = if (closed) "closed" else "open", val cancellationReason:String="", val categoryId:String="", val artworkId:String="")
val sampleMatches = listOf(
    Tournament("ff-solo", "Evening showdown", "Solo", "Today · 8:00 PM", 32, 48),
    Tournament("ff-duo", "Duo rush", "Duo", "Today · 9:30 PM", 20, 24),
    Tournament("cs-squad", "Clash Squad tour", "Squad", "Tomorrow · 7:00 PM", 8, 8),
    Tournament("ff-closed", "Morning scrim", "Solo", "Today · 10:00 AM", 12, 48, closed = true)
)
enum class JoinResult { REGISTERED, DUPLICATE, FULL, CLOSED }
fun registrationResult(match: Tournament, registered: Set<String>): JoinResult = when {
    match.id in registered -> JoinResult.DUPLICATE
    match.closed -> JoinResult.CLOSED
    match.joined >= match.capacity -> JoinResult.FULL
    else -> JoinResult.REGISTERED
}

internal fun browseMatches(matches: List<Tournament>, search: String, mode: String, fee: String, availability: String, time: String, now: Long): List<Tournament> {
    val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.of("Asia/Dhaka")).toLocalDate()
    return matches.filter { match ->
        val date = match.startsAtMillis.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.of("Asia/Dhaka")).toLocalDate() }
        (search.isBlank() || match.id.contains(search.trim(), true) || match.title.contains(search.trim(), true)) &&
            (mode == "All" || match.mode.equals(mode, true)) &&
            (fee == "All" || if (fee == "Free") match.fee == 0L else match.fee > 0) &&
            (availability == "All" || if (availability == "Available") !match.closed && match.joined < match.capacity else !match.closed && match.joined >= match.capacity) &&
            (time == "Any time" || if (time == "Today") date == today else date == today.plusDays(1))
    }.sortedBy { if (it.startsAtMillis > 0) it.startsAtMillis else Long.MAX_VALUE }
}

internal fun countdownText(startsAt: Long, now: Long): String {
    if (startsAt <= 0) return ""
    val seconds = ((startsAt - now).coerceAtLeast(0) + 999) / 1000
    if (seconds == 0L) return LocaleStrings.get("Scheduled start reached")
    val days = seconds / 86400
    val hours = seconds % 86400 / 3600
    val minutes = seconds % 3600 / 60
    return LocaleStrings.get("Starts in") + " " + (if (days > 0) "${days}d " else "") + "${hours}h ${minutes}m ${seconds % 60}s"
}

internal fun rosterSize(mode:String):Int = when(mode.lowercase(java.util.Locale.ROOT)) { "solo"->1; "duo"->2; "squad"->4; else->0 }
internal fun validRoster(mode:String,names:List<String>,captain:String):Boolean =
    rosterSize(mode)>0 && names.size==rosterSize(mode) && names.firstOrNull()==captain &&
        names.all { it.isNotBlank() && it==it.trim() && it.length<=80 } &&
        names.map { it.lowercase(java.util.Locale.ROOT) }.distinct().size==names.size

internal fun matchStatusChoices(current:String?,fee:Long):List<String> {
    if(current==null)return listOf("draft")
    val next=when(current){"draft"->"open";"open"->"closed";"closed"->"live";"live"->"review";else->null}
    return listOfNotNull(current,next)+if(current in listOf("draft","open","closed","live"))listOf("cancelled")else emptyList()
}
