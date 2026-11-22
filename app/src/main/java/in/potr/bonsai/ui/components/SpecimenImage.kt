package `in`.potr.bonsai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import `in`.potr.bonsai.R
import `in`.potr.bonsai.data.model.AssetPhotoKeys
import `in`.potr.bonsai.ui.theme.JetBrainsMonoFamily
import java.io.File

fun resolveStudioDrawable(key: String): Int = when (key) {
    AssetPhotoKeys.JUNIPER -> R.drawable.img_bonsai_juniper_1795322875377
    AssetPhotoKeys.MAPLE -> R.drawable.img_bonsai_maple_1795322834577
    AssetPhotoKeys.BOUGAINVILLEA -> R.drawable.img_bonsai_bougainvillea_1795322816979
    else -> R.drawable.img_bonsai_ficus_1795322818427
}

@Composable
fun SpecimenPhoto(
    imagePath: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (imagePath.startsWith("asset:") || imagePath.isBlank()) {
        Image(
            painter = painterResource(id = resolveStudioDrawable(imagePath)),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        val context = LocalContext.current
        val file = remember(imagePath) { File(imagePath) }
        if (file.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                error = painterResource(id = R.drawable.img_bonsai_ficus_1795322818427)
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_bonsai_ficus_1795322818427),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
    }
}

@Composable
fun CodeBadge(
    code: String,
    modifier: Modifier = Modifier,
    dark: Boolean = false
) {
    val bg = if (dark) Color(0xB31F2421) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = fg
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}
