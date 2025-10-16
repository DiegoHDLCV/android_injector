package com.vtms.client.param;

import com.vtms.client.OnTransResultListener;

interface IParamManager {
    // 下载应用参数 (Descargar parámetros de aplicación)
    void paramDownLoad(in String packName, OnTransResultListener listener);

    // 参数下载使用结果 (Resultado de uso de parámetros descargados)
    void paramUseResult(in String packName, int code, String msg);
}

