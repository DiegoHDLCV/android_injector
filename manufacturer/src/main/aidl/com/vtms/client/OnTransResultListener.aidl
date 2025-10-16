package com.vtms.client;

interface OnTransResultListener {
    /**
     * Callback de éxito de transacción
     * @param result resultado
     **/
    void onSuccess(String result);

    /**
     * Callback de fallo de transacción
     * @param errorCode código de error
     * @param message mensaje de error
     **/
    void onFailed(int errorCode, String message);
}

