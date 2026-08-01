package team.inreok.getiserver.domain.auth.dto

data class AuthorizeResponse(
    val authorizationUrl: String,
    val state: String,
)
