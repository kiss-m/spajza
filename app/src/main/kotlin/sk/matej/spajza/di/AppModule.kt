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
import okhttp3.OkHttpClient
import sk.matej.spajza.R
import sk.matej.spajza.data.account.FirebaseAccountRepository
import sk.matej.spajza.data.auth.FirebaseAuthRepository
import sk.matej.spajza.data.catalog.CatalogDatabase
import sk.matej.spajza.data.catalog.CatalogImporter
import sk.matej.spajza.data.catalog.RoomCatalogRepository
import sk.matej.spajza.data.firestore.FirestoreCookHistoryRepository
import sk.matej.spajza.data.firestore.FirestoreCustomIngredientRepository
import sk.matej.spajza.data.firestore.FirestoreHouseholdRepository
import sk.matej.spajza.data.firestore.FirestoreOwnRecipeRepository
import sk.matej.spajza.data.firestore.FirestorePantryRepository
import sk.matej.spajza.data.firestore.FirestoreRatingRepository
import sk.matej.spajza.data.firestore.FirestoreShoppingRepository
import sk.matej.spajza.data.firestore.FirestoreUserRepository
import sk.matej.spajza.data.ocr.MlKitTextReader
import sk.matej.spajza.data.off.OpenFoodFactsClient
import sk.matej.spajza.data.settings.DataStoreDeviceSettingsRepository
import sk.matej.spajza.data.web.WebRecipeImporter
import sk.matej.spajza.notifications.WorkManagerExpiryScheduler
import sk.matej.spajza.domain.receipt.TextReader
import sk.matej.spajza.domain.repository.AccountRepository
import sk.matej.spajza.domain.repository.AuthRepository
import sk.matej.spajza.domain.repository.CatalogRepository
import sk.matej.spajza.domain.repository.CookHistoryRepository
import sk.matej.spajza.domain.repository.CustomIngredientRepository
import sk.matej.spajza.domain.repository.DeviceSettingsRepository
import sk.matej.spajza.domain.repository.ExpiryReminderScheduler
import sk.matej.spajza.domain.repository.HouseholdRepository
import sk.matej.spajza.domain.repository.OwnRecipeRepository
import sk.matej.spajza.domain.repository.PantryRepository
import sk.matej.spajza.domain.repository.ProductLookup
import sk.matej.spajza.domain.repository.RatingRepository
import sk.matej.spajza.domain.repository.RecipeImporter
import sk.matej.spajza.domain.repository.ShoppingRepository
import sk.matej.spajza.domain.repository.UserRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.session.SessionManager
import sk.matej.spajza.domain.usecase.AddCustomToPantryUseCase
import sk.matej.spajza.domain.usecase.CreateHouseholdUseCase
import sk.matej.spajza.domain.usecase.CreateInviteUseCase
import sk.matej.spajza.domain.usecase.JoinHouseholdUseCase
import sk.matej.spajza.domain.usecase.LeaveHouseholdUseCase
import sk.matej.spajza.domain.usecase.ScanProductUseCase
import sk.matej.spajza.BuildConfig
import java.time.Clock
import java.util.concurrent.TimeUnit
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
    fun customIngredientRepository(db: FirebaseFirestore): CustomIngredientRepository = FirestoreCustomIngredientRepository(db)

    @Provides
    @Singleton
    fun ownRecipeRepository(db: FirebaseFirestore): OwnRecipeRepository = FirestoreOwnRecipeRepository(db)

    @Provides
    @Singleton
    fun recipeImporter(http: OkHttpClient): RecipeImporter =
        // Recipe sites expect a browser; the app still says who it is.
        WebRecipeImporter(http, userAgent = "Mozilla/5.0 (Linux; Android) Spajza/${BuildConfig.VERSION_NAME} (+https://github.com/kiss-m/spajza)")

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun productLookup(http: OkHttpClient): ProductLookup =
        // Open Food Facts asks every app to name itself and a way to reach its author.
        OpenFoodFactsClient(http, userAgent = "Spajza/${BuildConfig.VERSION_NAME} (Android; https://github.com/kiss-m/spajza)")

    @Provides
    fun scanProductUseCase(lookup: ProductLookup): ScanProductUseCase = ScanProductUseCase(lookup)

    @Provides
    @Singleton
    fun textReader(@ApplicationContext context: Context): TextReader = MlKitTextReader(context)

    @Provides
    fun addCustomToPantryUseCase(custom: CustomIngredientRepository, pantry: PantryRepository): AddCustomToPantryUseCase =
        AddCustomToPantryUseCase(custom, pantry)

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
    fun deviceSettingsRepository(@ApplicationContext context: Context): DeviceSettingsRepository =
        DataStoreDeviceSettingsRepository(context)

    @Provides
    @Singleton
    fun expiryReminderScheduler(@ApplicationContext context: Context, clock: Clock): ExpiryReminderScheduler =
        WorkManagerExpiryScheduler(context, clock)

    @Provides
    fun createInviteUseCase(households: HouseholdRepository, clock: Clock): CreateInviteUseCase =
        CreateInviteUseCase(households, clock)

    @Provides
    fun joinHouseholdUseCase(
        users: UserRepository,
        households: HouseholdRepository,
        pantry: PantryRepository,
        shopping: ShoppingRepository,
        custom: CustomIngredientRepository,
        recipes: OwnRecipeRepository,
        clock: Clock,
    ): JoinHouseholdUseCase = JoinHouseholdUseCase(users, households, pantry, shopping, custom, recipes, clock)

    @Provides
    fun leaveHouseholdUseCase(users: UserRepository, households: HouseholdRepository): LeaveHouseholdUseCase =
        LeaveHouseholdUseCase(users, households)

    @Provides
    @Singleton
    fun homeData(
        sessions: SessionManager,
        catalog: CatalogRepository,
        pantry: PantryRepository,
        shopping: ShoppingRepository,
        ratings: RatingRepository,
        history: CookHistoryRepository,
        custom: CustomIngredientRepository,
        recipes: OwnRecipeRepository,
        @ApplicationScope scope: CoroutineScope,
    ): HomeData = HomeData(sessions, catalog, pantry, shopping, ratings, history, custom, recipes, scope)
}
