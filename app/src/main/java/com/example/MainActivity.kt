package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.model.ChecklistItem
import com.example.data.model.HandoverAct
import com.example.data.repository.HandoverRepository
import com.example.ui.screens.ActDetailScreen
import com.example.ui.screens.ActsDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.LiveCameraScreen
import com.example.ui.screens.TariffsAndServicesScreen
import com.example.ui.theme.AktZhilyaTheme
import com.example.ui.viewmodel.HandoverViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_ACTS = "acts"
const val ROUTE_TARIFFS = "tariffs"
const val ROUTE_ACT_DETAIL = "act_detail"
const val ROUTE_CAMERA = "live_camera"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AktZhilyaTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope,
            )
        }
    }

    val googleConfigured = remember(context) {
        runCatching { context.getString(R.string.default_web_client_id) }
            .getOrDefault("")
            .startsWith("000000000000")
            .not()
    }

    val user = currentUser
    if (user == null && !googleConfigured) {
        key("local-user") {
            AuthenticatedMainApp(
                currentUserId = "local-user",
                onSignOut = {},
            )
        }
    } else if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedMainApp(
                currentUserId = user.uid,
                onSignOut = {
                    signOut(
                        credentialManager = credentialManager,
                        scope = scope,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedMainApp(
    currentUserId: String,
    onSignOut: () -> Unit,
) {
    val viewModel: HandoverViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                HandoverViewModel(HandoverRepository(db, Firebase.auth), currentUserId)
            }
        },
    )

    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    val actsState by viewModel.actsState.collectAsStateWithLifecycle()
    val planSettings by viewModel.planSettingsState.collectAsStateWithLifecycle()
    val selectedActState by viewModel.selectedActState.collectAsStateWithLifecycle()
    val checklistItemsState by viewModel.checklistItemsState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var pendingCameraItem by remember { mutableStateOf<ChecklistItem?>(null) }
    var pendingCameraAct by remember { mutableStateOf<HandoverAct?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val showTopLevelChrome = currentRoute == ROUTE_ACTS || currentRoute == ROUTE_TARIFFS

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (showTopLevelChrome) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("АктЖилья")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = onSignOut,
                                modifier = Modifier.testTag("sign_out_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Выйти из аккаунта",
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            },
            bottomBar = {
                if (showTopLevelChrome && !useNavigationRail) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_ACTS,
                            onClick = {
                                navController.navigate(ROUTE_ACTS) {
                                    popUpTo(ROUTE_ACTS) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(Icons.Filled.Description, contentDescription = null) },
                            label = { Text("Мои акты") },
                            modifier = Modifier.testTag("nav_acts_tab"),
                        )
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_TARIFFS,
                            onClick = {
                                navController.navigate(ROUTE_TARIFFS) {
                                    popUpTo(ROUTE_ACTS)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null) },
                            label = { Text("Тарифы и сервисы") },
                            modifier = Modifier.testTag("nav_tariffs_tab"),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (showTopLevelChrome && useNavigationRail) {
                    NavigationRail {
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_ACTS,
                            onClick = {
                                navController.navigate(ROUTE_ACTS) {
                                    popUpTo(ROUTE_ACTS) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(Icons.Filled.Description, contentDescription = null) },
                            label = { Text("Акты") },
                        )
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_TARIFFS,
                            onClick = {
                                navController.navigate(ROUTE_TARIFFS) {
                                    popUpTo(ROUTE_ACTS)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null) },
                            label = { Text("Тарифы") },
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_ACTS,
                    modifier = Modifier.weight(1f),
                ) {
                    composable(ROUTE_ACTS) {
                        ActsDashboardScreen(
                            actsState = actsState,
                            planSettings = planSettings,
                            isBusy = isBusy,
                            onOpenAct = { actId ->
                                viewModel.selectAct(actId)
                                navController.navigate(ROUTE_ACT_DETAIL)
                            },
                            onCreateAct = { title, address, apt, deposit, rent, role, lName, lPhone, tName, tPhone, lat, lng, locLabel ->
                                viewModel.createAct(
                                    title = title,
                                    address = address,
                                    apartmentNumber = apt,
                                    depositAmountRub = deposit,
                                    monthlyRentRub = rent,
                                    creatorRole = role,
                                    landlordName = lName,
                                    landlordPhone = lPhone,
                                    tenantName = tName,
                                    tenantPhone = tPhone,
                                    latitude = lat,
                                    longitude = lng,
                                    locationLabel = locLabel,
                                ) {
                                    navController.navigate(ROUTE_ACT_DETAIL)
                                }
                            },
                            onDeleteDraftAct = { actId ->
                                viewModel.deleteDraftAct(actId)
                            },
                        )
                    }

                    composable(ROUTE_TARIFFS) {
                        TariffsAndServicesScreen(
                            planSettings = planSettings,
                            isBusy = isBusy,
                            onSelectPlan = { planType, companyName, b2bActive ->
                                viewModel.updatePlanSettings(planType, companyName, b2bActive)
                            },
                        )
                    }

                    composable(ROUTE_ACT_DETAIL) {
                        ActDetailScreen(
                            actState = selectedActState,
                            itemsState = checklistItemsState,
                            planSettings = planSettings,
                            isBusy = isBusy,
                            onBack = {
                                navController.popBackStack()
                            },
                            onLaunchCamera = { item, act ->
                                pendingCameraItem = item
                                pendingCameraAct = act
                                navController.navigate(ROUTE_CAMERA)
                            },
                            onUpdateChecklistItem = { updated, currentList ->
                                val actId = (selectedActState as? UiState.Success)?.data?.id ?: return@ActDetailScreen
                                viewModel.updateChecklistItem(actId, updated, currentList)
                            },
                            onAddCustomItem = { zone, title, nextOrder ->
                                val actId = (selectedActState as? UiState.Success)?.data?.id ?: return@ActDetailScreen
                                viewModel.addCustomItem(actId, zone, title, nextOrder)
                            },
                            onSaveActDetails = { updatedAct ->
                                viewModel.updateAct(updatedAct, "Показания и стороны сохранены")
                            },
                            onMarkPdfGenerated = { actId ->
                                viewModel.markPdfGenerated(actId)
                            },
                            onCreatorSign = { actId, phone, smsCode, shareToken ->
                                viewModel.signAsCreator(actId, phone, smsCode, shareToken)
                            },
                            onCounterpartyConfirm = { actId, name, phone, smsCode ->
                                viewModel.confirmAsCounterparty(actId, name, phone, smsCode)
                            },
                        )
                    }

                    composable(ROUTE_CAMERA) {
                        val camItem = pendingCameraItem
                        val camAct = pendingCameraAct
                        if (camItem == null || camAct == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            val currentItems = (checklistItemsState as? UiState.Success)?.data ?: emptyList()
                            LiveCameraScreen(
                                actId = camAct.id,
                                zoneTitle = "${camItem.zone}: ${camItem.title}",
                                fallbackLat = camAct.latitude,
                                fallbackLng = camAct.longitude,
                                onPhotoCaptured = { proof ->
                                    val updated = camItem.copy(
                                        photoLocalPath = proof.localPath,
                                        photoCapturedAtIso = proof.capturedAtIso,
                                        photoLat = proof.latitude,
                                        photoLng = proof.longitude,
                                        photoSha256 = proof.sha256,
                                        condition = if (camItem.condition == ChecklistItem.CONDITION_PENDING) {
                                            ChecklistItem.CONDITION_OK
                                        } else {
                                            camItem.condition
                                        },
                                    )
                                    viewModel.updateChecklistItem(camAct.id, updated, currentItems)
                                    pendingCameraItem = null
                                    pendingCameraAct = null
                                    navController.popBackStack()
                                },
                                onClose = {
                                    pendingCameraItem = null
                                    pendingCameraAct = null
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
