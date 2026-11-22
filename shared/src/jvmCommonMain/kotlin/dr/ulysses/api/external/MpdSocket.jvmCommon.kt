package dr.ulysses.api.external

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal actual class MpdSocket(
    private val socket: Socket,
) {
    private val input = socket.openReadChannel()
    private val output = socket.openWriteChannel(autoFlush = true)

    suspend fun greet() {
        val greeting = input.readLine()
        if (greeting?.startsWith("OK MPD") != true) {
            throw MpdException("Not an MPD server: $greeting")
        }
    }

    // Off the caller's thread: a library is thousands of lines, each read resuming the reader, which on the UI
    // thread waits for the next frame every time. Not cancellable once sent: an answer left half read would be
    // taken for the next command's, e.g. when scrolling cancels the artwork of songs leaving the screen
    actual suspend fun command(line: String): List<Pair<String, String>> =
        withContext(NonCancellable + Dispatchers.IO) {
            output.writeStringUtf8("$line\n")
            readAnswer()
        }

    actual suspend fun idle(line: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        output.writeStringUtf8("$line\n")
        readAnswer()
    }

    actual suspend fun binary(line: String): Pair<List<Pair<String, String>>, ByteArray> = withContext(
        NonCancellable + Dispatchers.IO
    ) {
        output.writeStringUtf8("$line\n")
        readBinaryAnswer()
    }

    private suspend fun readBinaryAnswer(): Pair<List<Pair<String, String>>, ByteArray> {
        val pairs = mutableListOf<Pair<String, String>>()
        var bytes = ByteArray(0)
        while (true) {
            val answer = nextLine()
            when {
                answer == "OK" -> return pairs to bytes
                answer.startsWith("ACK") -> throw MpdException(answer)
                answer.startsWith("binary: ") -> {
                    bytes = input.readByteArray(answer.removePrefix("binary: ").toInt())
                    // The data ends with a newline of its own
                    input.readLine()
                }

                else -> pairs += pair(answer)
            }
        }
    }

    actual fun close() = socket.close()

    private suspend fun readAnswer(): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        while (true) {
            val answer = nextLine()
            when {
                answer == "OK" -> return pairs
                answer.startsWith("ACK") -> throw MpdException(answer)
                else -> pairs += pair(answer)
            }
        }
    }

    private suspend fun nextLine() = input.readLine() ?: throw MpdException("The server closed the connection")

    private fun pair(line: String) = line.substringBefore(": ") to line.substringAfter(
        delimiter = ": ",
        missingDelimiterValue = ""
    )
}

internal actual suspend fun openMpdSocket(
    address: String,
    port: Int,
): MpdSocket {
    val socket = aSocket(SelectorManager(Dispatchers.IO)).tcp().connect(
        hostname = address,
        port = port
    )
    return MpdSocket(socket).also { it.greet() }
}
