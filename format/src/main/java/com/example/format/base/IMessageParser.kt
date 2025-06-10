// com/example/format/base/IMessageParser.kt
package com.example.format.base

import com.example.format.SerialMessage

interface IMessageParser {
    fun appendData(newData: ByteArray)
    fun nextMessage(): SerialMessage?
}