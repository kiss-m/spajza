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
import sk.matej.spajza.R
import sk.matej.spajza.data.account.FirebaseAccountRepository
import sk.matej.spajza.data.auth.FirebaseAuthRepository
import sk.matej.spajza.data.catalog.CatalogDatabase
import sk.matej.spajza.data.catalog.CatalogImporter
import sk.matej.spajza.data.catalog.RoomCatalogRepository
import sk.matej.spajza.data.firestore.FirestoreCookHistoryRepository
import sk.matej.spajza.data.firestore.FirestoreHouseholdRepository
import sk.matej.spajza.data.firestore.FirestorePantryRepository
import sk.matej.spajza.data.firestore.FirestoreRatingRepository
import sk.matej.spajza.data.firestore.FirestoreShoppingRepository
import sk.matej.spajza.data.firestore.FirestoreUserRepository
import sk.matej.spajza.domain.repository.AccountRepository
import sk.matej.spajza.domain.repository.AuthRepository
import sk.matej.spajza.domain.repository.CatalogRepository
import sk.matej.spajza.domain.repository.CookHistoryRepository
import sk.matej.spajza.domain.repository.HouseholdRepository
import sk.matej.spajza.domain.repository.PantryRepository
import sk.matej.spajza.domain.repository.RatingRepository
import sk.matej.spajza.domain.repository.ShoppingRepository
import sk.matej.spajza.domain.repository.UserRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.session.SessionManager
import sk.matej.spajza.domain.usecase.CreateHouseholdUseCase
import java.time.Clock
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
    fun clock(): Clock = Clock.systemDefaultZone()

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

    @Provides
    @Singleton
    fun userRepository(db: FirebaseFirestore): UserRepository = FirestoreUserRepository(db)

    @Provides
    @Singleton
    fun householdRepository(db: FirebaseFirestore): HouseholdRepository = FirestoreHouseholdRepository(db)

    @Provides
    @Singleton
    fun pantryRepository(db: FirebaseFirestore): PantryRepository = FirestorePantryRepository(db)

    @Provides
    @Singleton
    fun shoppingRepository(db: FirebaseFirestore): ShoppingRepository = FirestoreShoppingRepository(db)

    @Provides
    @Singleton
    fun ratingRepository(db: FirebaseFirestore): RatingRepository = FirestoreRatingRepository(db)

    @Provides
    @Singleton
    fun cookHistoryRepository(db: FirebaseFirestore): CookHistoryRepository = FirestoreCookHistoryRepository(db)

    @Provides
    @Singleton
    fun accountRepository(@ApplicationContext context: Context, auth: FirebaseAuth, db: FirebaseFirestore): AccountRepository =
        FirebaseAccountRepository(context, auth, db)

    @Provides
    @Singleton
    fun sessionManager(
        @ApplicationContext context: Context,
        auth: AuthRepository,
        users: UserRepository,
        households: HouseholdRepository,
        catalog: CatalogRepository,
        @ApplicationScope scope: CoroutineScope,
    ): SessionManager = SessionManager(
        auth = auth,
        users = users,
        households = households,
        catalog = catalog,
        createHousehold = CreateHouseholdUseCase(users, households),
        defaultHouseholdName = { context.getString(R.string.default_household_name) },
        scope = scope,
    )

    @Provides
    @Singleton
    fun homeData(
        sessions: SessionManager,
        catalog: CatalogRepository,
        pantry: PantryRepository,
        shopping: ShoppingRepository,
        ratings: RatingRepository,
        history: CookHistoryRepository,
        @ApplicationScope scope: CoroutineScope,
    ): HomeData = HomeData(sessions, catalog, pantry, shopping, ratings, history, scope)
}
