package com.vigatec.format.base

import com.vigatec.format.ParsedMessage

/**
 * Interfaz para cualquier clase que pueda parsear un flujo de bytes en mensajes estructurados.
 */
interface IMessageParser {
    /**
     * Añade nuevos datos crudos al buffer interno del parser.
     */
    fun appendData(newData: ByteArray)

    /**
     * Intenta extraer el próximo mensaje completo y válido del buffer.
     * @return Un objeto que implementa [ParsedMessage], o null si no hay un mensaje completo.
     */
    fun nextMessage(): ParsedMessage? // <-- CORRECCIÓN: Ahora devuelve la interfaz base.
}
