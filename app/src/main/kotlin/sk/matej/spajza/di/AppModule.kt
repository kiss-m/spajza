package sk.matej.spajza.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import sk.matej.spajza.data.auth.FirebaseAuthRepository
import sk.matej.spajza.data.catalog.CatalogDatabase
import sk.matej.spajza.data.catalog.CatalogImporter
import sk.matej.spajza.data.catalog.RoomCatalogRepository
import sk.matej.spajza.domain.repository.AuthRepository
import sk.matej.spajza.domain.repository.CatalogRepository
import javax.inject.Qualifier
import javax.inject.Singleton

/** A scope that lives as long as the process, for work that must outlive any screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun catalogDatabase(@ApplicationContext context: Context): CatalogDatabase =
        Room.databaseBuilder<CatalogDatabase>(context, "catalog.db")
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun catalogRepository(
        @ApplicationContext context: Context,
        database: CatalogDatabase,
        @ApplicationScope scope: CoroutineScope,
    ): CatalogRepository = RoomCatalogRepository(CatalogImporter(context.assets, database.catalogDao()), scope)

    @Provides
    @Singleton
    fun firebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun firestore(): FirebaseFirestore = Firebase.firestore.apply {
        // Everything keeps working offline after the first sign-in and syncs later.
        firestoreSettings = firestoreSettings { setLocalCacheSettings(persistentCacheSettings {}) }
    }

    @Provides
    @Singleton
    fun authRepository(@ApplicationContext context: Context, auth: FirebaseAuth): AuthRepository =
        FirebaseAuthRepository(context, auth)
}
