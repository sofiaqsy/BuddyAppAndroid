package com.buddy.app.features.trips.map

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.buddy.app.BuildConfig
import com.buddy.app.core.data.model.ChatCard
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddySheet
import com.buddy.app.features.conexiones.ConexionesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Elegir a quién mandarle un lugar recomendado — espejo de
 * CompartirEnChatSheet (iOS).
 *
 * La lista son las CONVERSACIONES que ya existen, no una agenda: Buddy no tiene
 * grafo de amigos, tiene matches. Compartir con alguien con quien nunca
 * hablaste sería empezar una conversación, que es otra decisión de producto y
 * otro flujo.
 *
 * Una sola persona por envío. La selección múltiple es fácil de añadir después
 * y no aporta nada el primer día — mientras que sí obliga a decidir qué pasa si
 * dos de tres envíos fallan.
 */
@Composable
fun CompartirLugarSheet(
    card: ChatCard.Place,
    onDismiss: () -> Unit,
    vm: ConexionesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()

    /**
     * Solo conversaciones VIVAS.
     *
     * `connections` trae todos los matches y los terminados son la mayoría:
     * gente a la que ayudaste una vez hace meses. Además un chat completado es
     * de solo lectura, así que el mensaje llegaría a un sitio donde nadie puede
     * contestar — mandar algo a un buzón mudo es peor que no ofrecer el destino.
     *
     * Mismo criterio que la sección "activas" del tab Conexiones: si esa regla
     * cambia, tiene que cambiar en los dos sitios a la vez.
     */
    val conversaciones = remember(state.connections) {
        state.connections.filter { it.match.status in setOf("pending", "accepted", "active") }
    }

    // La hoja puede abrirse sin haber pisado nunca el tab Conexiones, y
    // entonces el ViewModel todavía no cargó: sin esto la lista diría "no
    // tienes conversaciones" cuando en realidad no se han pedido.
    LaunchedEffect(Unit) { if (!state.hasLoadedOnce) vm.load() }

    // TEMPORAL — quitar antes de publicar. Dice cuántas conexiones hay y en qué
    // estado, para poder distinguir "no tienes conversaciones" de "el filtro se
    // comió las que sí tenías".
    LaunchedEffect(state.connections) {
        android.util.Log.d(
            "CompartirLugar",
            "conexiones=${state.connections.size} estados=${state.connections.groupingBy { it.match.status ?: "nil" }.eachCount()} → se ofrecen ${conversaciones.size}",
        )
    }

    var enviandoA by remember { mutableStateOf<String?>(null) }
    var enviadoA by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fallo by remember { mutableStateOf(false) }
    var preparandoImagen by remember { mutableStateOf(false) }
    // Se rasteriza mientras se lee la lista, para que "Compartir fuera" no se
    // quede pensando cuando lo toquen.
    var imagen by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(card.spotId) {
        imagen = withContext(Dispatchers.IO) { rasterizarTarjeta(contexto, card) }
    }

    BuddySheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                "Compartir lugar",
                style = BuddyType.Headline, color = BuddyColor.Ink,
                modifier = Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.sm),
            )

            // Encabezado: qué se está mandando. Sin esto, tras dos toques ya no
            // se sabe cuál de los lugares del mapa es el que va a viajar.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.edge, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (card.photoUrl != null) {
                    AsyncImage(
                        model = card.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                            .background(BuddyColor.SurfaceRaised),
                    )
                }
                Column {
                    Text(card.name, style = BuddyType.FootnoteBold, color = BuddyColor.Ink, maxLines = 1)
                    if (!card.category.isNullOrEmpty()) {
                        Text(card.category, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                    }
                }
            }

            if (conversaciones.isEmpty()) {
                Column(
                    Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("No tienes conversaciones abiertas", style = BuddyType.Callout, color = BuddyColor.Ink)
                    // Nombra el porqué: con apoyos terminados a la espalda, un
                    // "todavía no tienes conversaciones" se leería como que la
                    // app perdió algo.
                    Text(
                        "Los apoyos ya cerrados no admiten mensajes nuevos. Cuando tengas una conversación en curso podrás compartirle lugares desde aquí.",
                        style = BuddyType.Footnote, color = BuddyColor.InkMuted,
                    )
                }
            } else {
                conversaciones.forEach { conn ->
                    val yaEnviado = conn.id in enviadoA
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enviandoA == null && !yaEnviado) {
                                val contenido = ChatCard.encode(card)
                                if (contenido == null) { fallo = true; return@clickable }
                                enviandoA = conn.id
                                scope.launch {
                                    val ok = vm.enviarMensaje(conn.match.id, contenido)
                                    enviandoA = null
                                    if (ok) enviadoA = enviadoA + conn.id else fallo = true
                                }
                            }
                            .padding(horizontal = Spacing.edge, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(BuddyColor.SurfaceRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (conn.avatarUrl != null) {
                                AsyncImage(
                                    model = conn.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                )
                            } else {
                                Icon(Icons.Filled.Person, null, Modifier.size(16.dp), tint = BuddyColor.InkMuted)
                            }
                        }
                        Text(
                            conn.displayName, style = BuddyType.Footnote, color = BuddyColor.Ink,
                            maxLines = 1, modifier = Modifier.weight(1f),
                        )
                        // "Enviado" se queda marcado en vez de cerrar la hoja:
                        // así se puede mandar a varias personas seguidas sin
                        // volver a abrirla, y queda claro a quién ya se le mandó.
                        when {
                            yaEnviado -> Icon(Icons.Filled.Check, "Enviado", Modifier.size(18.dp), tint = BuddyColor.Accent)
                            enviandoA == conn.id -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = BuddyColor.Brand)
                            else -> Text("Enviar", style = BuddyType.Caption1, color = BuddyColor.Brand)
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 68.dp), color = BuddyColor.Hairline)
                }
            }

            // WhatsApp, Mensajes, Notas… lo que el sistema ofrezca.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !preparandoImagen) {
                        preparandoImagen = true
                        scope.launch {
                            val uri = imagen ?: withContext(Dispatchers.IO) { rasterizarTarjeta(contexto, card) }
                            imagen = uri
                            preparandoImagen = false
                            compartirFuera(contexto, card, uri)
                            onDismiss()
                        }
                    }
                    .padding(horizontal = Spacing.edge, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                    if (preparandoImagen) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = BuddyColor.Brand)
                    } else {
                        Icon(Icons.Filled.Share, null, Modifier.size(18.dp), tint = BuddyColor.Ink)
                    }
                }
                Text("Compartir fuera de Buddy", style = BuddyType.Footnote, color = BuddyColor.Ink)
            }

            if (fallo) {
                Text(
                    "No pudimos enviarlo. Revisa tu conexión e inténtalo de nuevo.",
                    style = BuddyType.Caption1, color = BuddyColor.ErrorRed,
                    modifier = Modifier.padding(horizontal = Spacing.edge, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/**
 * Fuera de Buddy va la MISMA tarjeta, como imagen, más el texto.
 *
 * Solo texto llegaba a WhatsApp como un renglón suelto: el lugar se comparte
 * porque SE VE bien, y una frase no enseña nada.
 *
 * Si no hubo imagen (el lugar no tiene foto, o falló la descarga) viaja el texto
 * solo: una tarjeta con un hueco gris es peor que la frase, y el enlace —que es
 * lo que hace que el mensaje sirva de algo— siempre va.
 */
private fun compartirFuera(contexto: Context, card: ChatCard.Place, imagen: Uri?) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, textoParaFuera(card))
        if (imagen != null) {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imagen)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    contexto.startActivity(Intent.createChooser(intent, "Compartir ${card.name}"))
}

/**
 * Lo que sale de la app, junto a la imagen.
 *
 * Nombra a la PERSONA, no a la app. Quien comparte no descubrió el lugar: lo
 * descubrió Angie, y que alguien real lo recomiende es todo el valor del
 * mensaje. "Encontré este lugar en Buddy" se lleva un crédito que no le toca y
 * debilita justo lo que hace fuerte a la recomendación.
 */
private fun textoParaFuera(card: ChatCard.Place): String {
    val autor = card.authorName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotEmpty() }
    val presentacion = if (autor != null) {
        "$autor recomendó este lugar en Buddy. Míralo aquí:"
    } else {
        "Un buddy recomendó este lugar. Míralo aquí:"
    }
    return "📍 ${card.name}\n$presentacion\n${enlacePublico(card)}"
}

/** Universal Link del lugar: con Buddy instalado abre la ficha y sin ella, la
 *  página pública. El destino viaja como `d` para que la app cargue la guía
 *  correcta sin una consulta extra. */
private fun enlacePublico(card: ChatCard.Place): String = buildString {
    append(BuildConfig.PUBLIC_BASE_URL).append("/place/").append(card.spotId)
    if (card.destinationId != null) append("?d=").append(card.destinationId)
}

/**
 * La tarjeta tal como sale de la app, dibujada a Canvas.
 *
 * No reutiliza la del chat a propósito: aquella vive dentro de una burbuja, mide
 * 240dp y hereda el fondo de la conversación. Esta se ve SOLA, en la galería de
 * otra persona y sin nada alrededor, así que necesita su propio respiro y el
 * nombre de Buddy — fuera de la app nadie sabe de dónde salió.
 *
 * A Canvas y no componiendo fuera de pantalla: no hay ventana que mida un
 * composable sin adjuntarlo, y las medidas aquí son fijas de todos modos.
 * 3x sobre 320dp porque la imagen se mira a pantalla completa; a 1x el texto
 * sale borroso.
 */
private suspend fun rasterizarTarjeta(contexto: Context, card: ChatCard.Place): Uri? {
    val url = card.photoUrl ?: return null
    val foto = runCatching {
        val resultado = contexto.imageLoader.execute(
            ImageRequest.Builder(contexto).data(url).allowHardware(false).build(),
        )
        (resultado.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
    }.getOrNull() ?: return null

    val e = 3f                       // escala
    val ancho = (320 * e).toInt()
    val altoFoto = (320 * e).toInt()
    val pad = 16 * e

    val autor = card.authorName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotEmpty() }
    val lineas = 1 + (if (card.category.isNullOrEmpty()) 0 else 1) + (if (autor == null) 0 else 1)
    val altoTexto = pad * 2 + 26 * e + lineas * 22 * e
    val bitmap = Bitmap.createBitmap(ancho, (altoFoto + altoTexto).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    // Recorte centrado tipo scaledToFill: encajar la foto entera deformaría el
    // lugar, y una banda gris al lado lo haría parecer un error.
    val escala = maxOf(ancho / foto.width.toFloat(), altoFoto / foto.height.toFloat())
    val recorteAncho = (ancho / escala).toInt().coerceAtMost(foto.width)
    val recorteAlto = (altoFoto / escala).toInt().coerceAtMost(foto.height)
    canvas.drawBitmap(
        foto,
        Rect(
            (foto.width - recorteAncho) / 2, (foto.height - recorteAlto) / 2,
            (foto.width + recorteAncho) / 2, (foto.height + recorteAlto) / 2,
        ),
        RectF(0f, 0f, ancho.toFloat(), altoFoto.toFloat()),
        Paint(Paint.FILTER_BITMAP_FLAG),
    )

    val tinta = Paint(Paint.ANTI_ALIAS_FLAG)
    var y = altoFoto + pad + 12 * e

    if (!card.category.isNullOrEmpty()) {
        tinta.color = android.graphics.Color.parseColor("#6F625D")
        tinta.textSize = 11 * e
        tinta.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(card.category.uppercase(), pad, y, tinta)
        y += 22 * e
    }

    tinta.color = android.graphics.Color.parseColor("#2B1C18")
    tinta.textSize = 20 * e
    tinta.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText(card.name, pad, y, tinta)
    y += 22 * e

    if (autor != null) {
        tinta.color = android.graphics.Color.parseColor("#6F625D")
        tinta.textSize = 13 * e
        tinta.typeface = Typeface.DEFAULT
        canvas.drawText("Recomendado por $autor", pad, y, tinta)
        y += 22 * e
    }

    // La firma: fuera de la app esto es lo único que dice de dónde viene la
    // recomendación, y esta imagen acaba en la galería de gente que todavía no
    // conoce Buddy.
    tinta.color = android.graphics.Color.parseColor("#8B4A32")
    tinta.textSize = 12 * e
    tinta.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("BuddyApp", pad, y + 4 * e, tinta)

    return runCatching {
        val dir = File(contexto.cacheDir, "compartir").apply { mkdirs() }
        val archivo = File(dir, "lugar-${card.spotId}.png")
        archivo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", archivo)
    }.getOrNull()
}
