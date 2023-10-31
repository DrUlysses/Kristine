package dr.ulysses.plugins

import dr.ulysses.controllers.Song
import dr.ulysses.controllers.Tag
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.webjars.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.thymeleaf.ThymeleafContent
import java.io.File

fun Application.configureRouting() {

    install(StatusPages) {
        exception<AuthenticationException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized)
        }
        exception<AuthorizationException> { call, _ ->
            call.respond(HttpStatusCode.Forbidden)
        }

    }
    install(Webjars) {
        path = "/webjars" //defaults to /webjars
    }

    routing {
        get("/") {
            // TODO: Add simple player and navigation buttons to other pages
            call.respond(ThymeleafContent("index", mapOf(
                "name" to "world",
                "songList" to Song.refreshSongs()
            )))
        }
        post("/add_song") {
            kotlin.runCatching { call.receiveNullable<File>() }.getOrNull()?.let {
                call.respondText(Song.add(it))
            } ?: run {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Got add song form failed: No file"
                )
            }
        }
        get("/manage_tags") {
            try {
                val songName = call.receive<String>()
                call.respond(Tag.getTags(songName))
            } catch (e: Exception) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Asked for add song form failed: No song name"
                )
            }
        }
        // Static plugin. Try to access `/static/index.html`
        staticResources("/", "static") {
            enableAutoHeadResponse()
        }
        get("/webjars") {
            call.respondText("<script src='/webjars/jquery/jquery.js'></script>", ContentType.Text.Html)
        }
    }
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()
