package bd.arena.prototype

internal fun validPageLink(text:String):Boolean = runCatching {
    val uri=java.net.URI(text)
    uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null
}.getOrDefault(false)
