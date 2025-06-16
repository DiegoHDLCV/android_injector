// com/example/format/base/IMessageFormatter.kt
package com.example.format.base

interface IMessageFormatter {
    fun format(command: String, fields: List<String>): ByteArray
    fun format(command: String, singleField: String): ByteArray
    fun format(command: String): ByteArray
}