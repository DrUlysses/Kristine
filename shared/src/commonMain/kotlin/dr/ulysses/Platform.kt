package dr.ulysses

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
