package app.ttlauncher.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.ttlauncher.WallpaperActivity

private const val IMMICH = "app.alextran.immich"

/** The ways to change the wallpaper. [fromImmich] is null when the Immich app isn't installed. */
class WallpaperActions(val pickPhoto: () -> Unit, val fromImmich: (() -> Unit)?, val systemPicker: () -> Unit)

/** Call this where it stays composed: the photo picker's answer is lost if the caller has gone by the time it returns. */
@Composable
fun rememberWallpaperActions(): WallpaperActions {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picture ->
        if (picture != null) context.startActivity(WallpaperActivity.intent(context, picture))
    }
    val immich = remember { context.packageManager.getLaunchIntentForPackage(IMMICH) }
    return WallpaperActions(
        pickPhoto = { runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
        // Immich shares pictures out but can't be asked to pick one, so the choice is made there and shared back.
        fromImmich = immich?.let { open ->
            {
                Toast.makeText(context, "Open a photo, tap Share, then choose TT Launcher", Toast.LENGTH_LONG).show()
                runCatching { context.startActivity(open) }
            }
        },
        systemPicker = { context.open(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) },
    )
}

private fun Context.open(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
