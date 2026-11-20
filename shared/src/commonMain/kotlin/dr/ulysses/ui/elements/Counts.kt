package dr.ulysses.ui.elements

/**
 * "1 song", "3 songs".
 */
fun count(
    number: Int,
    one: String,
    many: String,
) = if (number == 1) {
    "1 $one"
} else {
    "$number $many"
}

fun songs(number: Int) = count(
    number = number,
    one = "song",
    many = "songs"
)
