package dr.ulysses.ui.views

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
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

/**
 * The player's queue, shown like a playlist while the app runs.
 */
@Serializable
data object CurrentQueue : NavKey

@Serializable
data object Search : NavKey

@Serializable
data object ManagePlaylist : NavKey

@Serializable
data object ManageUnsortedList : NavKey

@Serializable
data object ManageSong : NavKey

@Serializable
data object ManageDuplicates : NavKey

@Serializable
data object AutoSortSongs : NavKey

@Serializable
data object MergeNames : NavKey

@Serializable
data object Connections : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data object SyncLibrary : NavKey

val config = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(
                subclass = Artists::class,
                serializer = Artists.serializer()
            )
            subclass(
                subclass = Songs::class,
                serializer = Songs.serializer()
            )
            subclass(
                subclass = Albums::class,
                serializer = Albums.serializer()
            )
            subclass(
                subclass = Playlists::class,
                serializer = Playlists.serializer()
            )
            subclass(
                subclass = ArtistSongs::class,
                serializer = ArtistSongs.serializer()
            )
            subclass(
                subclass = AlbumSongs::class,
                serializer = AlbumSongs.serializer()
            )
            subclass(
                subclass = PlaylistSongs::class,
                serializer = PlaylistSongs.serializer()
            )
            subclass(
                subclass = CurrentQueue::class,
                serializer = CurrentQueue.serializer()
            )
            subclass(
                subclass = Search::class,
                serializer = Search.serializer()
            )
            subclass(
                subclass = ManagePlaylist::class,
                serializer = ManagePlaylist.serializer()
            )
            subclass(
                subclass = ManageUnsortedList::class,
                serializer = ManageUnsortedList.serializer()
            )
            subclass(
                subclass = ManageSong::class,
                serializer = ManageSong.serializer()
            )
            subclass(
                subclass = ManageDuplicates::class,
                serializer = ManageDuplicates.serializer()
            )
            subclass(
                subclass = AutoSortSongs::class,
                serializer = AutoSortSongs.serializer()
            )
            subclass(
                subclass = MergeNames::class,
                serializer = MergeNames.serializer()
            )
            subclass(
                subclass = Connections::class,
                serializer = Connections.serializer()
            )
            subclass(
                subclass = Settings::class,
                serializer = Settings.serializer()
            )
            subclass(
                subclass = SyncLibrary::class,
                serializer = SyncLibrary.serializer()
            )
        }
    }
}
