package dr.ulysses

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import dr.ulysses.plugins.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureRouting()
        configureHTTP()
        configureSerialization()
        configureSockets()
    }.start(wait = true)
}
