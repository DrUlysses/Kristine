package dr.ulysses

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment {
            developmentMode = true
            connector {
                host = "0.0.0.0"
                port = 8080
            }
            watchPaths = listOf("classes", "resources")
            module(Application::module)
        },
    ).start(wait = true)
}

fun Application.module() {
    routing {
        get("/") {
            call.respondText("Ktor:")
        }
    }
}
