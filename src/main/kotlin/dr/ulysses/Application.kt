package dr.ulysses

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import dr.ulysses.plugins.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.thymeleaf.*
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

fun main() {
    embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment {
            developmentMode = true
            connector { port = 8080 }
            watchPaths = listOf("classes", "resources")
            module(Application::module)
        },
    ).start(wait = true)
}

private fun Application.module() {
    configureRouting()
    configureHTTP()
    configureSerialization()
    configureSockets()
    install(Thymeleaf) {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            characterEncoding = "utf-8"
        })
    }
}
