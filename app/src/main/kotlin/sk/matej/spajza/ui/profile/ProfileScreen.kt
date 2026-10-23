package sk.matej.spajza.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import sk.matej.spajza.R
import sk.matej.spajza.domain.repository.AuthUser

@Composable
fun ProfileRoute(user: AuthUser, viewModel: ProfileViewModel = hiltViewModel()) {
    var language by remember { mutableStateOf(AppLanguage.current()) }
    ProfileScreen(
        accountLabel = user.email ?: user.displayName ?: user.uid,
        language = language,
        onLanguageSelected = {
            language = it
            it.apply()
        },
        onSignOut = viewModel::signOut,
    )
}

@Composable
fun ProfileScreen(
    accountLabel: String,
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.profile_signed_in_as, accountLabel), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.profile_language), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            AppLanguage.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = option == language, onClick = { onLanguageSelected(option) }, role = Role.RadioButton)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == language, onClick = null)
                    Text(
                        stringResource(
                            when (option) {
                                AppLanguage.SYSTEM -> R.string.language_system
                                AppLanguage.SK -> R.string.language_sk
                                AppLanguage.EN -> R.string.language_en
                            },
                        ),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        OutlinedButton(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
    }
}
