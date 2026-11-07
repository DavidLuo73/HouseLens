package tw.houselens.viewer.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import tw.houselens.viewer.BuildConfig
import tw.houselens.viewer.R

@Composable
fun AuthScreen(state: AuthState, busy: Boolean, error: String?, onLogin: () -> Unit, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("HouseLens", style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.auth_intro), style = MaterialTheme.typography.titleMedium)
        when (state) {
            AuthState.Checking -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.auth_checking))
            }
            AuthState.SignedOut -> {
                Text(stringResource(R.string.auth_sign_in_hint))
                Button(onClick = onLogin, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.auth_google))
                }
                if (busy) Text(stringResource(R.string.auth_browser_hint))
            }
            is AuthState.Denied -> {
                state.email?.let { Text(it) }
                Text(stringResource(R.string.auth_denied))
                Button(onClick = onLogin, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.auth_switch_account))
                }
            }
            AuthState.ConnectionError -> {
                Text(stringResource(R.string.auth_connection_error))
                Button(onClick = onRetry, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.auth_retry))
                }
                TextButton(onClick = onSignOut, enabled = !busy) { Text(stringResource(R.string.auth_sign_out)) }
            }
            AuthState.StorageError -> {
                Text(stringResource(R.string.auth_cleanup_error))
                Button(onClick = onSignOut, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.auth_retry))
                }
            }
            is AuthState.Authorized -> {
                Text(state.email)
                Text(stringResource(R.string.auth_approved))
                Text(stringResource(R.string.auth_local_data))
                Text(stringResource(R.string.auth_version, BuildConfig.VERSION_NAME))
                Button(onClick = onSignOut, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.auth_sign_out))
                }
            }
        }
        error?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error) }
    }
}
