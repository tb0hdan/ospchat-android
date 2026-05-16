package com.ospchat.android.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/**
 * What to render for a peer / self avatar.
 *
 * - [Custom] points at a local JPEG file (the user's saved avatar, or a
 *   peer's cached avatar pulled via `GET /v1/avatar`).
 * - [Initials] renders a colored circle with two uppercase letters; the
 *   color is deterministic per `seed` so the same peer always gets the same
 *   background. The natural seed is the peer's UUID.
 */
sealed interface AvatarModel {
    data class Custom(
        val localPath: String,
    ) : AvatarModel

    data class Initials(
        val letters: String,
        val seed: String,
    ) : AvatarModel
}

@Composable
fun Avatar(
    model: AvatarModel,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    when (model) {
                        is AvatarModel.Custom -> Color.Transparent
                        is AvatarModel.Initials -> initialsBackgroundColor(model.seed)
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        when (model) {
            is AvatarModel.Custom -> {
                AsyncImage(
                    model = File(model.localPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is AvatarModel.Initials -> {
                Text(
                    text = model.letters,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.4f).sp),
                )
            }
        }
    }
}

/**
 * Compute the two-letter initials for a nickname. Multi-word nicknames take
 * the first letter of each of the first two words; single-word nicknames
 * take the first two characters. Result is uppercased.
 */
fun computeInitials(nickname: String): String {
    val trimmed = nickname.trim()
    if (trimmed.isEmpty()) return "?"
    val words = trimmed.split(Regex("\\s+"))
    val candidate =
        when {
            words.size >= 2 -> words[0].take(1) + words[1].take(1)
            else -> words[0].take(2)
        }
    return candidate.uppercase()
}

private fun initialsBackgroundColor(seed: String): Color {
    // Deterministic per-seed hue; fixed saturation / lightness keep the
    // colors muted enough that white text stays readable.
    val hue = ((seed.hashCode() and 0x7fff_ffff) % 360).toFloat()
    return Color.hsl(hue = hue, saturation = 0.55f, lightness = 0.45f)
}
