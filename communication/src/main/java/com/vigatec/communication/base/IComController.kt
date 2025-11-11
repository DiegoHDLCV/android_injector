package com.vigatec.communication.base

interface IComController {
    fun open(): Int
    fun close(): Int
    fun init(baudRate: EnumCommConfBaudRate, parity: EnumCommConfParity, dataBits: EnumCommConfDataBits): Int
    fun write(data: ByteArray, timeout: Int): Int
    fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int
}