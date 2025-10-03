package dr.ulysses.ui.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Serializable
data object Artists : NavKey

@Serializable
data object Songs : NavKey

@Serializable
data object Albums : NavKey

@Serializable
data object Playlists : NavKey

@Serializable
data object ArtistSongs : NavKey

@Serializable
data object AlbumSongs : NavKey

@Serializable
data object PlaylistSongs : NavKey

@Serializable
data object Search : NavKey

@Serializable
data object ManagePlaylist : NavKey

@Serializable
data object ManageUnsortedList : NavKey

@Serializable
data object ManageSong : NavKey

@Serializable
data object Connections : NavKey

@Serializable
data object Settings : NavKey

val config = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Artists::class, Artists.serializer())
            subclass(Songs::class, Songs.serializer())
            subclass(Albums::class, Albums.serializer())
            subclass(Playlists::class, Playlists.serializer())
            subclass(ArtistSongs::class, ArtistSongs.serializer())
            subclass(AlbumSongs::class, AlbumSongs.serializer())
            subclass(PlaylistSongs::class, PlaylistSongs.serializer())
            subclass(Search::class, Search.serializer())
            subclass(ManagePlaylist::class, ManagePlaylist.serializer())
            subclass(ManageUnsortedList::class, ManageUnsortedList.serializer())
            subclass(ManageSong::class, ManageSong.serializer())
            subclass(Connections::class, Connections.serializer())
            subclass(Settings::class, Settings.serializer())
        }
    }
}

@Composable
inline fun <reified T : NavKey> rememberNavBackStackFix(
    configuration: SavedStateConfiguration,
    vararg elements: T,
): NavBackStack<NavKey> = rememberSerializable(
    configuration = configuration,
    serializer = NavBackStackSerializer(PolymorphicSerializer(NavKey::class)),
) {
    NavBackStack(*elements)
}
