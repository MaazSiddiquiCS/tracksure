package com.tracksure.android.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tracksure.android.R

@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundBrush = Brush.verticalGradient(
        0f to colorScheme.background,
        0.45f to colorScheme.surface.copy(alpha = 0.55f),
        1f to colorScheme.background
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surface.copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_banner),
                contentDescription = "TrackSure logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        Text(
            text = "Private mesh tracking, online or offline",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Text(
            text = "Welcome. Track the devices that matter, even without the internet.",
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onBackground
        )

        MapPreviewCard(colorScheme = colorScheme)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colorScheme.surface.copy(alpha = 0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FeatureRow(
                    title = "Offline-first mesh",
                    body = "Stay connected with nearby devices using Bluetooth mesh.",
                    icon = Icons.Filled.Bluetooth,
                    colorScheme = colorScheme
                )
                FeatureRow(
                    title = "Live map view",
                    body = "See movement and proximity on a shared map.",
                    icon = Icons.Filled.Map,
                    colorScheme = colorScheme
                )
                FeatureRow(
                    title = "Privacy built in",
                    body = "Your data stays on your devices. No tracking.",
                    icon = Icons.Filled.Security,
                    colorScheme = colorScheme
                )
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Get started")
        }

        Text(
            text = "We will ask for Bluetooth and location permissions so the mesh can work.",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FeatureRow(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colorScheme: ColorScheme
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onBackground.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun MapPreviewCard(colorScheme: ColorScheme) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colorScheme.surface.copy(alpha = 0.9f),
                            colorScheme.background.copy(alpha = 0.9f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )

                val gridColor = colorScheme.primary.copy(alpha = 0.12f)
                val pathColor = colorScheme.primary.copy(alpha = 0.7f)
                val nodeColor = colorScheme.primary

                val stepX = size.width / 6f
                val stepY = size.height / 4f
                for (i in 1..5) {
                    drawLine(
                        color = gridColor,
                        start = Offset(stepX * i, 0f),
                        end = Offset(stepX * i, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                for (i in 1..3) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, stepY * i),
                        end = Offset(size.width, stepY * i),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val points = listOf(
                    Offset(size.width * 0.15f, size.height * 0.7f),
                    Offset(size.width * 0.35f, size.height * 0.45f),
                    Offset(size.width * 0.6f, size.height * 0.58f),
                    Offset(size.width * 0.82f, size.height * 0.3f)
                )

                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = pathColor,
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                points.forEachIndexed { index, offset ->
                    val radius = if (index == 0) 9.dp.toPx() else 6.dp.toPx()
                    drawCircle(
                        color = nodeColor,
                        radius = radius,
                        center = offset,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = nodeColor,
                        radius = radius - 2.dp.toPx(),
                        center = offset
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                color = colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Live mesh map",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
