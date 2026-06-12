package com.cheradip.ailanguagetutor.feature.practice



import android.Manifest

import android.content.pm.PackageManager

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp

import androidx.compose.material3.Card

import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.unit.dp

import androidx.core.content.ContextCompat

import androidx.hilt.navigation.compose.hiltViewModel

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.cheradip.ailanguagetutor.ui.components.CheradipDropdown

import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen

import com.cheradip.ailanguagetutor.ui.components.InputChannel

import com.cheradip.ailanguagetutor.ui.components.InputChannelBar

@Composable

fun PracticeHubScreen(

    onOpenModeSelection: () -> Unit = {},

    onScanClick: () -> Unit = {},

    onCameraClick: () -> Unit = {},

    onImportClick: () -> Unit = {},

    startVoiceInput: Boolean = false,

    modifier: Modifier = Modifier,

    viewModel: PracticeHubViewModel = hiltViewModel(),

) {

    val context = LocalContext.current

    var inputChannel by remember { mutableStateOf(InputChannel.TYPE) }

    var syncedLine by remember { mutableStateOf("") }

    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var voiceLaunchHandled by remember { mutableStateOf(false) }



    val hubState by viewModel.uiState.collectAsStateWithLifecycle()

    val practiceLangs by viewModel.practiceLanguages.collectAsStateWithLifecycle()

    val languageOptions by viewModel.languageOptions.collectAsStateWithLifecycle()



    var hasMicPermission by remember {

        mutableStateOf(

            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==

                PackageManager.PERMISSION_GRANTED,

        )

    }

    val micPermissionLauncher = rememberLauncherForActivityResult(

        ActivityResultContracts.RequestPermission(),

    ) { granted ->

        hasMicPermission = granted

        if (granted) {

            pendingMicAction?.invoke()

        } else {

            viewModel.setSpeechError("Microphone permission is required for voice input.")

        }

        pendingMicAction = null

    }



    fun withMicPermission(action: () -> Unit) {

        if (hasMicPermission) {

            action()

        } else {

            pendingMicAction = action

            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        }

    }



    LaunchedEffect(startVoiceInput, hasMicPermission) {
        if (startVoiceInput && !voiceLaunchHandled) {
            voiceLaunchHandled = true
            inputChannel = InputChannel.VOICE
            if (hasMicPermission) {
                viewModel.startVoiceInput()
            } else {
                pendingMicAction = { viewModel.startVoiceInput() }
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    CheradipScrollScreen(

        modifier = modifier,

        title = "Practice",

        subtitle = "Input ${practiceLangs.inputLanguage.uppercase()} → output ${practiceLangs.outputLanguage.uppercase()}",

    ) {

        if (languageOptions.isNotEmpty()) {

            item {

                PracticeLanguageSelectors(
                    languageOptions = languageOptions,
                    inputLanguage = practiceLangs.inputLanguage,
                    outputLanguage = practiceLangs.outputLanguage,
                    onInputSelected = { viewModel.setInputLanguage(it.code) },
                    onOutputSelected = { viewModel.setOutputLanguage(it.code) },
                )

            }

        }



        item {

            InputChannelBar(

                selected = inputChannel,

                onSelect = { channel ->

                    inputChannel = channel

                    when (channel) {

                        InputChannel.SCAN -> onScanClick()

                        InputChannel.CAMERA -> onCameraClick()

                        InputChannel.IMPORT -> onImportClick()

                        InputChannel.LISTEN -> {

                            syncedLine = hubState.aiOutput ?: hubState.typedInput

                            if (syncedLine.isNotBlank()) viewModel.speak(syncedLine)

                        }

                        InputChannel.VOICE -> withMicPermission { viewModel.startVoiceInput() }

                        InputChannel.TYPE -> viewModel.stopVoiceInput()

                    }

                },

                channels = listOf(

                    InputChannel.SCAN,

                    InputChannel.CAMERA,

                    InputChannel.IMPORT,

                    InputChannel.TYPE,

                    InputChannel.VOICE,

                    InputChannel.LISTEN,

                ),

                iconsOnly = true,

            )

        }



        item {

            PracticeInputCard(

                hubState = hubState,

                onInputChange = viewModel::updateTypedInput,

                onProcess = viewModel::processTypedInputWithSavedLanguages,

                onStartVoice = { withMicPermission { viewModel.startVoiceInput() } },

                onStopVoice = viewModel::stopVoiceInput,

                onSpeakOutput = viewModel::speak,

            )

        }



        item {

            VoiceCalibrationPanel(

                state = hubState,

                onSelectLanguage = viewModel::selectCalibrationLanguage,

                onSelectTier = viewModel::selectCalibrationTier,

                onStartMic = { withMicPermission { viewModel.startCalibrationMic() } },

                onStopMic = viewModel::stopVoiceInput,

            )

        }



        item {

            PracticeQuickActions(

                onOpenModeSelection = onOpenModeSelection,

                onScanClick = onScanClick,

                onStartVoice = { withMicPermission { viewModel.startVoiceInput() } },

                onSpeakSynced = {

                    syncedLine = hubState.aiOutput ?: hubState.typedInput

                    viewModel.speak(syncedLine)

                },

                onRecordPractice = viewModel::recordPractice,

            )

        }



        if (syncedLine.isNotBlank()) {

            item { SyncedTextDisplay(text = syncedLine) }

        }

    }

}



@Composable

internal fun PracticeLanguageSelectors(
    languageOptions: List<PracticeLanguageOption>,
    inputLanguage: String,
    outputLanguage: String,
    onInputSelected: (PracticeLanguageOption) -> Unit,
    onOutputSelected: (PracticeLanguageOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedInput = languageOptions.firstOrNull {
        it.code.equals(inputLanguage, ignoreCase = true)
    } ?: languageOptions.first()
    val selectedOutput = languageOptions.firstOrNull {
        it.code.equals(outputLanguage, ignoreCase = true)
    } ?: languageOptions.first()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Phones are ~328dp wide after screen padding; 480dp threshold wrongly forced two rows.
        val stackVertically = maxWidth < 300.dp
        if (stackVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CheradipDropdown(
                    label = "Input language",
                    options = languageOptions,
                    selected = selectedInput,
                    onSelected = onInputSelected,
                    optionLabel = { it.label },
                )
                CheradipDropdown(
                    label = "Output language",
                    options = languageOptions,
                    selected = selectedOutput,
                    onSelected = onOutputSelected,
                    optionLabel = { it.label },
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CheradipDropdown(
                    label = "Input language",
                    options = languageOptions,
                    selected = selectedInput,
                    onSelected = onInputSelected,
                    optionLabel = { it.label },
                    modifier = Modifier.weight(1f),
                )
                CheradipDropdown(
                    label = "Output language",
                    options = languageOptions,
                    selected = selectedOutput,
                    onSelected = onOutputSelected,
                    optionLabel = { it.label },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}



@Composable

fun SyncedTextDisplay(text: String, modifier: Modifier = Modifier) {

    Card(modifier = modifier.fillMaxWidth()) {

        Column(

            modifier = Modifier.padding(16.dp),

            horizontalAlignment = Alignment.CenterHorizontally,

        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                Icon(

                    Icons.AutoMirrored.Filled.VolumeUp,

                    contentDescription = null,

                    tint = MaterialTheme.colorScheme.primary,

                )

                Text(

                    " Synced playback",

                    style = MaterialTheme.typography.labelMedium,

                    modifier = Modifier.padding(start = 4.dp),

                )

            }

            Text(

                text,

                style = MaterialTheme.typography.headlineSmall,

                modifier = Modifier.padding(top = 8.dp),

            )

        }

    }

}


