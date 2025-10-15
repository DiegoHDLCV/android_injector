package com.vigatec.injector.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Componente de esqueleto con efecto shimmer para mostrar mientras se cargan datos
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .clip(shape)
            .background(brush)
    )
}

/**
 * Esqueleto para las tarjetas de estadísticas del dashboard
 */
@Composable
fun StatCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono esqueleto
            SkeletonBox(
                modifier = Modifier.size(32.dp),
                height = 32.dp,
                shape = RoundedCornerShape(16.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                // Título esqueleto
                SkeletonBox(
                    modifier = Modifier.width(80.dp),
                    height = 12.dp
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Valor esqueleto
                SkeletonBox(
                    modifier = Modifier.width(40.dp),
                    height = 20.dp
                )
            }
        }
    }
}

/**
 * Esqueleto para las tarjetas de perfil
 */
@Composable
fun ProfileCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header con avatar y info
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar esqueleto
                SkeletonBox(
                    modifier = Modifier.size(64.dp),
                    height = 64.dp,
                    shape = RoundedCornerShape(16.dp)
                )
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Nombre esqueleto
                    SkeletonBox(
                        modifier = Modifier.width(120.dp),
                        height = 16.dp
                    )
                    // Descripción esqueleto
                    SkeletonBox(
                        modifier = Modifier.width(200.dp),
                        height = 12.dp
                    )
                }
            }
            
            // Barra de estado esqueleto
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(),
                height = 40.dp,
                shape = RoundedCornerShape(12.dp)
            )
            
            // Configuraciones esqueleto
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(
                    modifier = Modifier.width(100.dp),
                    height = 14.dp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        SkeletonBox(
                            modifier = Modifier.width(80.dp),
                            height = 32.dp,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Esqueleto para las tarjetas de llaves inyectadas
 */
@Composable
fun InjectedKeyCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    // Tipo de llave esqueleto
                    SkeletonBox(
                        modifier = Modifier.width(100.dp),
                        height = 14.dp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Algoritmo esqueleto
                    SkeletonBox(
                        modifier = Modifier.width(80.dp),
                        height = 12.dp
                    )
                }
                
                // Estado esqueleto
                SkeletonBox(
                    modifier = Modifier.width(60.dp),
                    height = 24.dp,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            
            // Detalles
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SkeletonBox(
                            modifier = Modifier.width(60.dp),
                            height = 12.dp
                        )
                        SkeletonBox(
                            modifier = Modifier.width(100.dp),
                            height = 12.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Esqueleto para la pantalla completa de carga
 */
@Composable
fun FullScreenSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header esqueleto
        SkeletonBox(
            modifier = Modifier.fillMaxWidth(),
            height = 60.dp,
            shape = RoundedCornerShape(12.dp)
        )
        
        // Estadísticas esqueleto
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SkeletonBox(
                modifier = Modifier.width(150.dp),
                height = 20.dp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCardSkeleton(modifier = Modifier.weight(1f))
                StatCardSkeleton(modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCardSkeleton(modifier = Modifier.weight(1f))
                StatCardSkeleton(modifier = Modifier.weight(1f))
            }
        }
        
        // Contenido esqueleto
        repeat(3) {
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(),
                height = 120.dp,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
} 