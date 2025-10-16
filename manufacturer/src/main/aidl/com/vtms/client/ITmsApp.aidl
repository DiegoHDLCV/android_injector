package com.vtms.client;

import com.vtms.client.OnTransResultListener;

interface ITmsApp {
    // Descargar parámetros de aplicación
    void paramDownLoad(in String packName, OnTransResultListener listener);
    
    // Resultado de uso de parámetros descargados
    void paramUseResult(in String packName, int code, String msg);
}

