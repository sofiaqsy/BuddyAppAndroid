package com.buddy.app.core

import java.security.MessageDigest

// ════════════════════════════════════════════════════════════════════════════
// Alias de viajero — identidad estable sin pedir datos personales
//
// La mayoría de viajeros entra como invitado y no tiene nombre, nacionalidad ni
// avatar: para un buddy con dos solicitudes abiertas, ambas se ven como
// "Viajero" y son imposibles de distinguir. El alias resuelve eso sin meter un
// formulario en el peor momento posible (alguien pidiendo ayuda), y sin exponer
// el nombre real de nadie ante un desconocido.
//
// Se DERIVA del UUID, no viaja por la API: el servidor y cada cliente llegan al
// mismo alias por su cuenta, así que no hizo falta migración, backfill, columna
// nueva ni cambiar un solo endpoint.
//
// ⚠️  GEMELO de buddy-core/src/lib/travelerAlias.js y de
// buddyapp/BuddyApp/Sources/Services/TravelerAlias.swift. Las listas, su ORDEN
// y la forma de derivar los índices son un contrato entre las tres: si divergen,
// el push diría un nombre y la tarjeta otro. Reordenar, insertar en medio o
// quitar una entrada le cambia el alias a gente que ya lo tenía — solo AÑADIR
// AL FINAL es seguro, y hay que hacerlo en los tres archivos a la vez.
// ════════════════════════════════════════════════════════════════════════════

object TravelerAlias {

    // feminine = género gramatical, para que el color concuerde
    // ("Vicuña Dorada", no "Vicuña Dorado")
    private data class Noun(val word: String, val feminine: Boolean)

    private sealed interface ColorWord {
        fun form(feminine: Boolean): String

        data class Invariable(val word: String) : ColorWord {
            override fun form(feminine: Boolean) = word
        }

        data class Gendered(val m: String, val f: String) : ColorWord {
            override fun form(feminine: Boolean) = if (feminine) f else m
        }
    }

    private val nouns = listOf(
        // Fauna peruana — identidad local, fácil de recordar
        Noun("Cóndor", false),
        Noun("Llama", true),
        Noun("Puma", false),
        Noun("Vicuña", true),
        Noun("Colibrí", false),
        Noun("Alpaca", true),
        Noun("Jaguar", false),
        Noun("Delfín", false),
        Noun("Tucán", false),
        Noun("Zorro", false),
        Noun("Nutria", true),
        Noun("Búho", false),
        Noun("Garza", true),
        Noun("Flamenco", false),
        Noun("Tortuga", true),
        Noun("Ballena", true),
        Noun("Guanaco", false),
        Noun("Pelícano", false),
        Noun("Chinchilla", true),
        Noun("Anaconda", true),
        // Naturaleza — más neutro, para quien no conecta con la fauna
        Noun("Río", false),
        Noun("Bosque", false),
        Noun("Nevado", false),
        Noun("Brisa", true),
        Noun("Océano", false),
        Noun("Aurora", true),
        Noun("Duna", true),
        Noun("Volcán", false),
        Noun("Cascada", true),
        Noun("Selva", true),
        Noun("Valle", false),
        Noun("Glaciar", false),
        Noun("Arrecife", false),
        Noun("Páramo", false),
        Noun("Laguna", true),
        Noun("Sendero", false),
        Noun("Manglar", false),
        Noun("Cráter", false),
        Noun("Estepa", true),
        Noun("Cumbre", true),
    )

    private val colors = listOf<ColorWord>(
        ColorWord.Invariable("Azul"), ColorWord.Invariable("Verde"),
        ColorWord.Invariable("Coral"), ColorWord.Invariable("Ámbar"),
        ColorWord.Invariable("Índigo"), ColorWord.Invariable("Turquesa"),
        ColorWord.Invariable("Carmesí"), ColorWord.Invariable("Esmeralda"),
        ColorWord.Invariable("Púrpura"), ColorWord.Invariable("Marfil"),
        ColorWord.Invariable("Cobre"), ColorWord.Invariable("Zafiro"),
        ColorWord.Invariable("Jade"), ColorWord.Invariable("Violeta"),
        ColorWord.Invariable("Magenta"), ColorWord.Invariable("Añil"),
        ColorWord.Invariable("Ocre"), ColorWord.Invariable("Lila"),
        ColorWord.Invariable("Escarlata"), ColorWord.Invariable("Gris"),
        ColorWord.Invariable("Bronce"), ColorWord.Invariable("Perla"),
        ColorWord.Invariable("Cian"), ColorWord.Invariable("Malva"),
        ColorWord.Invariable("Salmón"), ColorWord.Invariable("Vino"),
        ColorWord.Gendered("Dorado", "Dorada"),
        ColorWord.Gendered("Plateado", "Plateada"),
    )

    /**
     * Alias determinístico para un traveler_id. El mismo id da siempre el mismo
     * alias, en este dispositivo, en otro, y en el servidor.
     * Solo texto — nada de emojis: tiene que leerse como el nombre de una
     * persona, y aparece en sitios (títulos de push) donde un icono desentona.
     */
    fun alias(travelerId: String?): String {
        if (travelerId.isNullOrEmpty()) return "Viajero"

        val digest = MessageDigest.getInstance("SHA-256").digest(travelerId.toByteArray(Charsets.UTF_8))
        // Dos tramos independientes del hash para que sustantivo y color no se
        // correlacionen. Big-endian sobre 4 bytes = los 8 primeros hex chars que
        // leen los gemelos en JS y Swift.
        val noun = nouns[(be32(digest, 0) % nouns.size.toUInt()).toInt()]
        val color = colors[(be32(digest, 4) % colors.size.toUInt()).toInt()]

        return "${noun.word} ${color.form(noun.feminine)}"
    }

    /** Cómo mostrar a una persona: su nombre real si lo dio, y si no su alias. */
    fun displayName(realName: String?, id: String?): String {
        val real = realName?.trim().orEmpty()
        return if (real.isNotEmpty()) real else alias(id)
    }

    /**
     * Igual que [displayName] pero solo el primer nombre — para encabezados de
     * chat y listas donde el nombre completo no cabe. El alias no se parte:
     * "Llama Coral" pierde todo su sentido reducido a "Llama".
     */
    fun shortDisplayName(realName: String?, id: String?): String {
        val real = realName?.trim().orEmpty()
        if (real.isNotEmpty()) {
            return real.split(" ").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: real
        }
        return alias(id)
    }

    /**
     * Iniciales para el avatar sin foto. Con alias salen dos letras
     * ("Tortuga Azul" → "TA"), que distinguen mejor que una sola.
     */
    fun initials(realName: String?, id: String?): String {
        val name = displayName(realName, id)
        val letters = name.split(" ").take(2).mapNotNull { it.firstOrNull() }
        return if (letters.isEmpty()) "?" else letters.joinToString("").uppercase()
    }

    private fun be32(bytes: ByteArray, offset: Int): UInt =
        (bytes[offset].toUInt() and 0xFFu shl 24) or
        (bytes[offset + 1].toUInt() and 0xFFu shl 16) or
        (bytes[offset + 2].toUInt() and 0xFFu shl 8) or
        (bytes[offset + 3].toUInt() and 0xFFu)
}
