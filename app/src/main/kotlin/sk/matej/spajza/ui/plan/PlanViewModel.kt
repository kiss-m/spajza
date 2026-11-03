package sk.matej.spajza.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sk.matej.spajza.domain.catalog.Catalog
import sk.matej.spajza.domain.pantry.PantryItem
import sk.matej.spajza.domain.pantry.ShoppingItem
import sk.matej.spajza.domain.plan.PlanEntry
import sk.matej.spajza.domain.plan.PlanMeal
import sk.matej.spajza.domain.plan.WeekId
import sk.matej.spajza.domain.plan.WeekPlan
import sk.matej.spajza.domain.repository.MealPlanRepository
import sk.matej.spajza.domain.repository.ShoppingRepository
import sk.matej.spajza.domain.session.HomeData
import sk.matej.spajza.domain.session.Session
import sk.matej.spajza.domain.session.kitchen
import sk.matej.spajza.domain.usecase.MovePlanEntryUseCase
import sk.matej.spajza.domain.usecase.PlanToShoppingUseCase
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface PlanEvent {
    data class AddedToShopping(val count: Int) : PlanEvent

    /** Everything planned is already in the pantry or on the list. */
    data object NothingToBuy : PlanEvent

    data class Removed(val entry: PlanEntry) : PlanEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val home: HomeData,
    private val plans: MealPlanRepository,
    private val shopping: ShoppingRepository,
    private val clock: Clock,
) : ViewModel() {
    private val week = MutableStateFlow(WeekId.of(today()))
    private var builder: Pair<Catalog, PlanWeekBuilder>? = null

    private val events = Channel<PlanEvent>(Channel.BUFFERED)
    val screenEvents: Flow<PlanEvent> = events.receiveAsFlow()

    private val householdId = home.session.map { it?.householdId }.distinctUntilChanged()

    /** The shown week as stored; null while signed out. */
    private val plan: Flow<WeekPlan?> = combine(householdId, week) { id, shown -> id to shown }
        .flatMapLatest { (id, shown) -> if (id == null) flowOf(null) else plans.observe(id, shown) }

    private class Household(val catalog: Catalog?, val session: Session?, val pantry: List<PantryItem>?, val shopping: List<ShoppingItem>?)

    // The shopping list is collected here so "Do nákupu" reads its current state.
    private val household = combine(home.catalog, home.session, home.pantry, home.shopping) { catalog, session, pantry, list ->
        Household(catalog, session, pantry, list)
    }

    val state: StateFlow<PlanUiState> = combine(household, plan) { data, shown -> data to shown }
        .mapLatest { (data, shown) ->
            val catalog = data.catalog
            val session = data.session
            val pantry = data.pantry
            if (catalog == null || session == null || pantry == null || shown == null) {
                PlanUiState()
            } else {
                builderFor(catalog).build(shown, session.kitchen(pantry), today())
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    private fun builderFor(catalog: Catalog): PlanWeekBuilder {
        val cached = builder
        if (cached != null && cached.first === catalog) return cached.second
        return PlanWeekBuilder(catalog).also { builder = catalog to it }
    }

    fun today(): LocalDate = LocalDate.now(clock)

    fun onPreviousWeek() {
        week.value = week.value.previous()
    }

    fun onNextWeek() {
        week.value = week.value.next()
    }

    fun onThisWeek() {
        week.value = WeekId.of(today())
    }

    fun onServingsChange(entry: PlanEntry, servings: Int) {
        val session = home.session.value ?: return
        val value = servings.coerceIn(PlanEntry.MIN_SERVINGS, PlanEntry.MAX_SERVINGS)
        if (value == entry.servings) return
        viewModelScope.launch { plans.setServings(session.householdId, entry, value) }
    }

    fun onMove(entry: PlanEntry, date: LocalDate, meal: PlanMeal) {
        val session = home.session.value ?: return
        if (date == entry.date && meal == entry.meal) return
        viewModelScope.launch { MovePlanEntryUseCase(plans)(session.householdId, entry, date, meal, movedBy = session.uid) }
    }

    fun onRemove(entry: PlanEntry) {
        val session = home.session.value ?: return
        viewModelScope.launch {
            plans.remove(session.householdId, entry)
            events.send(PlanEvent.Removed(entry))
        }
    }

    /** Puts a removed entry back under its old id. */
    fun onUndoRemove(entry: PlanEntry) {
        val session = home.session.value ?: return
        viewModelScope.launch { plans.add(session.householdId, entry, entry.addedBy ?: session.uid) }
    }

    fun onShop() {
        val catalog = home.catalog.value ?: return
        val session = home.session.value ?: return
        val pantry = home.pantry.value ?: return
        val list = home.shopping.value ?: return
        val shown = state.value.days.flatMap { day -> day.entries.map { it.entry } }
        viewModelScope.launch {
            val count = PlanToShoppingUseCase(shopping)(
                householdId = session.householdId,
                catalog = catalog,
                entries = shown,
                kitchen = session.kitchen(pantry),
                list = list,
                today = today(),
                addedBy = session.uid,
            )
            events.send(if (count == 0) PlanEvent.NothingToBuy else PlanEvent.AddedToShopping(count))
        }
    }
}
