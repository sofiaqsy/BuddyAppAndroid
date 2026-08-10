package com.buddy.app.core.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Contenido rico dentro de un mensaje de chat — espejo de ChatCard.swift.
 *
 * POR QUÉ UN PREFIJO Y NO UN message_type NUEVO
 *
 * `message.type` es un ENUM de Postgres (`text, audio, image, system`).
 * Mandar un valor que el enum no conoce no falla en el cliente: falla en la
 * base con un 500. Si la app se publica antes que la migración, ENVIAR DEJA DE
 * FUNCIONAR. Los mensajes ricos viajan como `type='text'` con un prefijo en
 * `content`.
 *
 * `card:` generaliza las tres convenciones sueltas que ya había (`place:`,
 * `location:`, `category_card:`), cada una con su formato posicional propio: un
 * solo prefijo, un sobre con `kind`, y las tarjetas futuras entran sin inventar
 * otro parser. Las viejas siguen vivas y se siguen dibujando — hay mensajes con
 * ese formato en la base y un chat es un registro histórico.
 */
object ChatCard {
    const val PREFIX = "card:"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Un lugar recomendado, compartido dentro del chat.
     *
     * Los datos van DENORMALIZADOS a propósito. Un mensaje es lo que se dijo en
     * ese momento: si el lugar se renombra o se borra, la tarjeta debe seguir
     * leyéndose como se envió, no convertirse en un hueco.
     */
    @Serializable
    data class Place(
        /** Versión del sobre. Un lector viejo que no la reconozca cae al texto
         *  de respaldo en vez de dibujar una tarjeta a medias. */
        val v: Int = 1,
        /** Discriminador — "place" hoy, "trip"/"event" mañana. */
        val kind: String = "place",
        /** El spot del catálogo: lo que permite abrir LA FICHA del lugar y no un
         *  pin suelto. Con solo coordenadas lo máximo es centrar un mapa. */
        val spotId: String,
        val destinationId: String? = null,
        val name: String,
        val category: String? = null,
        /** La foto que se ve en la tarjeta. Se guarda la URL enviada y no se
         *  resuelve al abrir: si el autor la borra, el mensaje sigue mostrando
         *  lo que se compartió. */
        val photoUrl: String? = null,
        val lat: Double? = null,
        val lng: Double? = null,
        /** Quién recomienda el lugar — la prueba social de la tarjeta. */
        val authorName: String? = null,
    )

    fun encode(place: Place): String? =
        runCatching { PREFIX + json.encodeToString(Place.serializer(), place) }.getOrNull()

    /**
     * La tarjeta, si [content] es un sobre `card:` de tipo place.
     *
     * Nulo ante cualquier duda —prefijo ausente, JSON roto, `kind` desconocido,
     * versión futura—. Quien llama pinta entonces el texto de respaldo: es
     * preferible una frase a una tarjeta rota, y sobre todo a un JSON crudo
     * dentro de una burbuja.
     */
    fun decodePlace(content: String?): Place? {
        if (content == null || !content.startsWith(PREFIX)) return null
        val card = runCatching {
            json.decodeFromString(Place.serializer(), content.removePrefix(PREFIX))
        }.getOrNull() ?: return null
        return card.takeIf { it.kind == "place" && it.v == 1 }
    }

    /**
     * Lo que se lee donde no hay sitio para una tarjeta: la lista de
     * conversaciones, el CTA del Home, una notificación push.
     *
     * Solo el nombre, sin emoji: esa línea suele llegar ya prefijada con "Tú: ",
     * y un pin delante dejaba «Tú: 📍 Cafetería Rosal» — tres señales
     * compitiendo en un renglón que solo tiene que recordar de qué se habló.
     */
    fun resumen(content: String?): String? = decodePlace(content)?.name
}
