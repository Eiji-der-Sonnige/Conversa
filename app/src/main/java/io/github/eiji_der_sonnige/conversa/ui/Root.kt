package io.github.eiji_der_sonnige.conversa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.eiji_der_sonnige.conversa.MainViewModel
import io.github.eiji_der_sonnige.conversa.Screen
import io.github.eiji_der_sonnige.conversa.call.CallState
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun ConversaRoot(vm: MainViewModel) {
    val screen by vm.screen.collectAsState()
    val messenger by vm.messenger.collectAsState()
    val error by vm.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val callFlow = remember(messenger) { messenger?.calls?.state ?: MutableStateFlow(CallState.Idle) }
    val callState by callFlow.collectAsState()

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            val m = messenger
            when (val s = screen) {
                Screen.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                Screen.Login -> LoginScreen(vm)
                is Screen.TotpVerify -> TotpVerifyScreen(vm, s)
                is Screen.TotpSetup -> if (m != null) TotpSetupScreen(vm, m, s.secret)
                Screen.Chats -> if (m != null) ChatListScreen(vm, m)
                is Screen.Chat -> if (m != null) ChatScreen(vm, m, s.peer)
                Screen.Settings -> if (m != null) SettingsScreen(vm, m)
            }
            val unlocked = screen !is Screen.TotpVerify && screen !is Screen.Login
            if (m != null && unlocked && callState != CallState.Idle) {
                CallScreen(m, callState)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}
