package team.inreok.getiserver.web

data class ApiResponse<T>(
    val data: T,
) {
    companion object {
        fun <T> of(data: T): ApiResponse<T> = ApiResponse(data)
    }
}
