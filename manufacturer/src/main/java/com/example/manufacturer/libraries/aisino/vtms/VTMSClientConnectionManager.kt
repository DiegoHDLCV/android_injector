package com.example.manufacturer.libraries.aisino.vtms

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.vtms.client.VTmsManager
import com.vtms.client.param.IParamManager
import com.vtms.client.OnTransResultListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Manager para conexión con el servicio VTMS (Vanstone Terminal Management System) mediante AIDL.
 * Gestiona el ciclo de vida de la conexión y la descarga de parámetros desde el servidor VTMS.
 */
object VTMSClientConnectionManager {

    private const val TAG = "VTMSClientConnectionManager"
    private const val VTMS_SERVICE_ACTION = "com.vtms.api_service"
    private const val VTMS_SERVICE_PACKAGE = "com.vtms.client"
    private const val VTMS_SERVICE_CLASS = "com.vtms.client.service.TMSService" // Servicio descubierto
    private const val CONNECTING_PENDING_DURATION = 20 // 20 segundos timeout

    private var context: Application? = null
    private var paramDownloadService: IParamManager? = null
    private var vtmsRemoteService: VTmsManager? = null
    private var isConnectedFailed = false
    
    private val scope = CoroutineScope(Dispatchers.IO)

    private val isConnected: Boolean
        get() = paramDownloadService != null

    /**
     * Método auxiliar para inspeccionar los métodos disponibles en un objeto usando reflexión.
     */
    private fun inspectAvailableMethods(obj: Any) {
        try {
            val methods = obj.javaClass.methods
                .filter { !it.name.startsWith("access$") } // Filtrar métodos de acceso sintéticos
                .filter { !it.declaringClass.name.startsWith("java.lang") } // Filtrar métodos de Object
                .filter { !it.declaringClass.name.startsWith("android.os.IInterface") } // Filtrar métodos de IInterface
                .sortedBy { it.name }
            
            Log.d(TAG, "Total de métodos encontrados: ${methods.size}")
            Log.d(TAG, "")
            
            methods.forEachIndexed { index, method ->
                val params = method.parameterTypes.joinToString(", ") { it.simpleName }
                val returnType = method.returnType.simpleName
                Log.d(TAG, "${index + 1}. ${method.name}($params): $returnType")
            }
            
            if (methods.isEmpty()) {
                Log.d(TAG, "⚠ No se encontraron métodos públicos en el objeto")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al inspeccionar métodos: ${e.message}", e)
        }
    }

    /**
     * Inicializa el manager con el contexto de la aplicación.
     * Debe llamarse una vez al inicio de la aplicación.
     */
    @Synchronized
    fun init(context: Application) {
        this.context = context
        Log.d(TAG, "VTMSClientConnectionManager inicializado con contexto de aplicación")
    }

    /**
     * Connection callback para el servicio VTMS.
     */
    private val connection by lazy {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.i(TAG, "✓ Servicio VTMS conectado exitosamente")

                // Inspeccionar el IBinder para diagnóstico
                try {
                    Log.d(TAG, "═══ Inspeccionando IBinder recibido ═══")
                    service?.let { binder ->
                        Log.d(TAG, "IBinder class: ${binder.javaClass.name}")
                        try {
                            val descriptor = binder.interfaceDescriptor
                            Log.d(TAG, "Interface descriptor: $descriptor")
                        } catch (e: Exception) {
                            Log.w(TAG, "No se pudo obtener interface descriptor: ${e.message}")
                        }
                    }
                    Log.d(TAG, "═════════════════════════════════════")
                } catch (e: Exception) {
                    Log.w(TAG, "Error inspeccionando IBinder: ${e.message}")
                }

                try {
                    // Obtener referencia al servicio remoto usando VTmsManager (interfaz correcta)
                    vtmsRemoteService = VTmsManager.Stub.asInterface(service)
                    Log.d(TAG, "  - VTmsManager.Stub.asInterface() exitoso")

                    // Obtener el gestor de parámetros
                    paramDownloadService = vtmsRemoteService?.paramManager
                    Log.d(TAG, "  - ParamManager obtenido: ${paramDownloadService != null}")

                    // Inspeccionar el IParamManager para diagnóstico
                    paramDownloadService?.let { paramManager ->
                        try {
                            Log.d(TAG, "═══ Inspeccionando IParamManager recibido ═══")
                            Log.d(TAG, "IParamManager class: ${paramManager.javaClass.name}")
                            val interfaceDescriptor = try {
                                paramManager.asBinder().interfaceDescriptor
                            } catch (e: Exception) {
                                "No se pudo obtener"
                            }
                            Log.d(TAG, "Interface descriptor: $interfaceDescriptor")
                            Log.d(TAG, "✓ Interface correcta: com.vtms.client.param.IParamManager")
                            
                            // Listar métodos disponibles en IParamManager
                            Log.d(TAG, "")
                            Log.d(TAG, "═══ Métodos disponibles en IParamManager ═══")
                            inspectAvailableMethods(paramManager)
                            
                            Log.d(TAG, "═════════════════════════════════════════")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error inspeccionando IParamManager: ${e.message}")
                        }
                    }

                    // Inspeccionar el VTmsManager
                    vtmsRemoteService?.let { vtmsService ->
                        try {
                            Log.d(TAG, "")
                            Log.d(TAG, "═══ Inspeccionando VTmsManager recibido ═══")
                            Log.d(TAG, "VTmsManager class: ${vtmsService.javaClass.name}")
                            val interfaceDescriptor = try {
                                vtmsService.asBinder().interfaceDescriptor
                            } catch (e: Exception) {
                                "No se pudo obtener"
                            }
                            Log.d(TAG, "Interface descriptor: $interfaceDescriptor")
                            
                            // Listar métodos disponibles en VTmsManager
                            Log.d(TAG, "")
                            Log.d(TAG, "═══ Métodos disponibles en VTmsManager ═══")
                            inspectAvailableMethods(vtmsService)
                            
                            Log.d(TAG, "═════════════════════════════════════════")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error inspeccionando VTmsManager: ${e.message}")
                        }
                    }

                    isConnectedFailed = false
                    Log.i(TAG, "✓ Conexión VTMS completada y lista para uso")
                } catch (e: SecurityException) {
                    Log.e(TAG, "═══ SecurityException al acceder a VTmsManager ═══")
                    Log.e(TAG, "El servicio TMSService NO implementa com.vtms.client.VTmsManager correctamente")
                    service?.let {
                        try {
                            Log.e(TAG, "Interface descriptor del servicio: ${it.interfaceDescriptor}")
                        } catch (ex: Exception) {
                            Log.e(TAG, "No se pudo determinar la interfaz del servicio")
                        }
                    }
                    Log.e(TAG, "═══════════════════════════════════════════════", e)
                    isConnectedFailed = true
                    paramDownloadService = null
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error al obtener servicios VTMS después de conexión", e)
                    isConnectedFailed = true
                    paramDownloadService = null
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.w(TAG, "⚠ Servicio VTMS desconectado inesperadamente")
                isConnectedFailed = true
                paramDownloadService = null
                vtmsRemoteService = null
            }

            override fun onBindingDied(name: ComponentName?) {
                super.onBindingDied(name)
                Log.e(TAG, "✗ Binding del servicio VTMS murió")
                isConnectedFailed = true
                paramDownloadService = null
                vtmsRemoteService = null
            }
        }
    }

    /**
     * Establece el binding con el servicio VTMS.
     * @return true si la conexión fue exitosa, false en caso contrario.
     */
    private fun bindService(context: Context): Boolean {
        Log.d(TAG, "════════════════════════════════════════════════════════════")
        Log.d(TAG, "Iniciando binding con servicio VTMS...")
        
        // Intentar primero con action (método documentado)
        var intent = Intent().apply {
            action = VTMS_SERVICE_ACTION
            setPackage(VTMS_SERVICE_PACKAGE)
        }
        
        Log.d(TAG, "Intento 1: Binding con action")
        Log.d(TAG, "  - Action: $VTMS_SERVICE_ACTION")
        Log.d(TAG, "  - Package: $VTMS_SERVICE_PACKAGE")

        var bound = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Excepción al intentar bindService con action", e)
            false
        }

        if (!bound) {
            Log.w(TAG, "✗ bindService() con action falló")
            Log.d(TAG, "Intento 2: Binding con ComponentName explícito")
            Log.d(TAG, "  - Package: $VTMS_SERVICE_PACKAGE")
            Log.d(TAG, "  - Class: $VTMS_SERVICE_CLASS")
            
            // Intentar con ComponentName explícito
            intent = Intent().apply {
                component = android.content.ComponentName(VTMS_SERVICE_PACKAGE, VTMS_SERVICE_CLASS)
            }
            
            bound = try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.e(TAG, "✗ Excepción al intentar bindService con ComponentName", e)
                false
            }
            
            if (!bound) {
                Log.e(TAG, "✗ Ambos intentos de binding fallaron")
                Log.e(TAG, "  Posibles causas:")
                Log.e(TAG, "  1. El servicio TMSService no es el correcto para AIDL")
                Log.e(TAG, "  2. Faltan permisos en el Manifest")
                Log.e(TAG, "  3. El servicio requiere autenticación adicional")
                Log.d(TAG, "════════════════════════════════════════════════════════════")
                return false
            } else {
                Log.i(TAG, "✓ bindService() con ComponentName EXITOSO")
            }
        } else {
            Log.i(TAG, "✓ bindService() con action EXITOSO")
        }

        Log.d(TAG, "✓ bindService() exitoso, esperando onServiceConnected()...")

        // Esperar hasta que la conexión se establezca o timeout
        val startBindingStamp = System.currentTimeMillis()
        var lastCheckStamp = System.currentTimeMillis()
        var isOverPendingTime = false

        while (!isConnected && !isConnectedFailed && !isOverPendingTime) {
            if (System.currentTimeMillis() - lastCheckStamp >= 1000) {
                val elapsed = (System.currentTimeMillis() - startBindingStamp) / 1000
                Log.d(TAG, "  ⏳ Esperando conexión VTMS... (${elapsed}s)")
                lastCheckStamp = System.currentTimeMillis()
                isOverPendingTime = (lastCheckStamp - startBindingStamp) / 1000 > CONNECTING_PENDING_DURATION
            }
        }

        return when {
            isOverPendingTime -> {
                Log.e(TAG, "✗ Timeout esperando conexión VTMS (${CONNECTING_PENDING_DURATION}s)")
                Log.d(TAG, "════════════════════════════════════════════════════════════")
                false
            }
            isConnectedFailed -> {
                Log.e(TAG, "✗ Conexión VTMS falló")
                Log.d(TAG, "════════════════════════════════════════════════════════════")
                false
            }
            else -> {
                Log.i(TAG, "✓ Conexión VTMS establecida exitosamente")
                Log.d(TAG, "════════════════════════════════════════════════════════════")
                true
            }
        }
    }

    /**
     * Construye la conexión con el servicio VTMS.
     */
    private fun buildConnection(
        onSucceed: () -> Unit,
        onFailed: ((message: String?) -> Unit)?
    ) {
        if (context == null) {
            val errorMsg = "VTMSClientConnectionManager no inicializado. Llame a init() primero."
            Log.e(TAG, errorMsg)
            throw RuntimeException(errorMsg)
        }

        scope.launch {
            if (isConnected) {
                Log.d(TAG, "Servicio VTMS ya está conectado, reutilizando conexión")
                onSucceed()
                return@launch
            }

            Log.d(TAG, "Servicio VTMS no conectado, iniciando binding...")
            val deferred = async {
                bindService(context!!.applicationContext)
            }

            Log.d(TAG, "Esperando resultado del binding...")
            val isConnected = deferred.await()

            if (isConnected) {
                Log.i(TAG, "✓ Conexión establecida, invocando callback de éxito")
                onSucceed()
            } else {
                val errorMsg = "No se pudo conectar con el servicio VTMS"
                Log.e(TAG, "✗ $errorMsg")
                onFailed?.invoke(errorMsg)
            }
        }
    }

    /**
     * Solicita la descarga de parámetros de aplicación desde el servidor VTMS.
     *
     * @param packageName Nombre del paquete de la aplicación para la cual descargar parámetros.
     * @param onSucceed Callback invocado cuando la descarga es exitosa, con el JSON de parámetros.
     * @param onFailed Callback invocado cuando hay un error, con el mensaje de error.
     */
    fun requestApplicationParameter(
        packageName: String,
        onSucceed: (param: String?) -> Unit,
        onFailed: ((message: String?) -> Unit)? = null
    ) {
        Log.d(TAG, "════════════════════════════════════════════════════════════")
        Log.d(TAG, "Solicitando parámetros de aplicación desde VTMS")
        Log.d(TAG, "  - Package: $packageName")

        buildConnection(
            onSucceed = {
                if (paramDownloadService == null) {
                    val errorMsg = "Servicio remoto VTMS es null después de conectar"
                    Log.e(TAG, "✗ $errorMsg")
                    Log.d(TAG, "════════════════════════════════════════════════════════════")
                    onFailed?.invoke(errorMsg)
                    return@buildConnection
                }

                Log.d(TAG, "Invocando paramDownLoad() en servicio VTMS...")
                try {
                    paramDownloadService?.paramDownLoad(
                        packageName,
                        object : OnTransResultListener.Stub() {
                            override fun onSuccess(param: String?) {
                                val threadName = Thread.currentThread().name
                                Log.i(TAG, "✓ Parámetros descargados exitosamente")
                                Log.d(TAG, "  - Thread: $threadName")
                                Log.d(TAG, "  - Datos recibidos (primeros 200 chars): ${param?.take(200) ?: "null"}")
                                Log.d(TAG, "════════════════════════════════════════════════════════════")
                                onSucceed.invoke(param)
                            }

                            override fun onFailed(errorCode: Int, message: String?) {
                                val threadName = Thread.currentThread().name
                                Log.e(TAG, "✗ Fallo al descargar parámetros")
                                Log.e(TAG, "  - Thread: $threadName")
                                Log.e(TAG, "  - Error code: $errorCode")
                                Log.e(TAG, "  - Mensaje: $message")
                                Log.d(TAG, "════════════════════════════════════════════════════════════")
                                onFailed?.invoke(message)
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Excepción al invocar paramDownLoad()", e)
                    Log.d(TAG, "════════════════════════════════════════════════════════════")
                    onFailed?.invoke("Error al comunicarse con VTMS: ${e.message}")
                }
            },
            onFailed = { message ->
                Log.e(TAG, "✗ Fallo al establecer conexión: $message")
                Log.d(TAG, "════════════════════════════════════════════════════════════")
                onFailed?.invoke(message)
            }
        )
    }

    /**
     * Cierra la conexión con el servicio VTMS y libera recursos.
     */
    fun closeConnection() {
        Log.d(TAG, "Cerrando conexión con servicio VTMS...")
        try {
            context?.unbindService(connection)
            paramDownloadService = null
            vtmsRemoteService = null
            isConnectedFailed = false
            Log.i(TAG, "✓ Conexión VTMS cerrada y recursos liberados")
        } catch (e: Exception) {
            Log.w(TAG, "Error al cerrar conexión VTMS (puede ser normal si no estaba conectado)", e)
        }
    }

    /**
     * Verifica si el servicio VTMS está disponible en el dispositivo.
     * @return true si el servicio está disponible, false en caso contrario.
     */
    fun isVtmsServiceAvailable(context: Context): Boolean {
        Log.d(TAG, "════════════════════════════════════════════════════════════")
        Log.d(TAG, "Verificando disponibilidad del servicio VTMS...")
        
        val intent = Intent().apply {
            action = VTMS_SERVICE_ACTION
            setPackage(VTMS_SERVICE_PACKAGE)
        }
        
        Log.d(TAG, "  - Action: $VTMS_SERVICE_ACTION")
        Log.d(TAG, "  - Package: $VTMS_SERVICE_PACKAGE")
        
        val packageManager = context.packageManager
        val resolveInfo = packageManager.resolveService(intent, 0)
        
        val isAvailable = resolveInfo != null
        
        if (isAvailable) {
            Log.i(TAG, "✓ Servicio VTMS disponible con action")
            Log.d(TAG, "  - Service: ${resolveInfo?.serviceInfo?.name}")
            Log.d(TAG, "════════════════════════════════════════════════════════════")
            return true
        } else {
            Log.w(TAG, "✗ Servicio VTMS NO disponible con action: $VTMS_SERVICE_ACTION")
            
            // Intentar verificar si el paquete al menos existe
            try {
                val packageInfo = packageManager.getPackageInfo(VTMS_SERVICE_PACKAGE, 0)
                Log.w(TAG, "  ✓ El paquete VTMS SÍ está instalado (versión ${packageInfo.versionName})")
                Log.w(TAG, "  ✗ Pero el servicio con action '$VTMS_SERVICE_ACTION' no es accesible")
                
                // Intentar listar todos los servicios del paquete VTMS
                Log.d(TAG, "  → Intentando descubrir servicios disponibles en el paquete...")
                try {
                    val allPackages = packageManager.getInstalledPackages(android.content.pm.PackageManager.GET_SERVICES)
                    val vtmsPackage = allPackages.find { it.packageName == VTMS_SERVICE_PACKAGE }
                    
                    // Usar variable local para evitar problemas de smart cast
                    val services = vtmsPackage?.services
                    if (services != null && services.isNotEmpty()) {
                        Log.d(TAG, "  → Servicios encontrados en VTMS (${services.size}):")
                        services.forEach { service ->
                            Log.d(TAG, "    • ${service.name}")
                            Log.d(TAG, "      - Exported: ${service.exported}")
                            Log.d(TAG, "      - Enabled: ${service.enabled}")
                        }
                    } else {
                        Log.w(TAG, "  → No se encontraron servicios en el paquete VTMS")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "  → No se pudieron listar servicios: ${e.message}")
                }
                
                // Intentar con ComponentName explícito del servicio descubierto
                Log.d(TAG, "  → Intentando con ComponentName explícito...")
                Log.d(TAG, "    - Usando servicio: $VTMS_SERVICE_CLASS")
                try {
                    val componentIntent = Intent().apply {
                        component = android.content.ComponentName(VTMS_SERVICE_PACKAGE, VTMS_SERVICE_CLASS)
                    }
                    val componentResolve = packageManager.resolveService(componentIntent, 0)
                    if (componentResolve != null) {
                        Log.i(TAG, "  ✓ Servicio VTMS accesible con ComponentName explícito")
                        Log.i(TAG, "    - Service: ${componentResolve.serviceInfo.name}")
                        Log.i(TAG, "    - Exported: ${componentResolve.serviceInfo.exported}")
                        Log.d(TAG, "════════════════════════════════════════════════════════════")
                        return true // ¡Encontrado con ComponentName!
                    } else {
                        Log.w(TAG, "  ✗ No se pudo resolver con ComponentName")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "  ✗ Error al intentar ComponentName: ${e.message}")
                }
                
            } catch (e: Exception) {
                Log.w(TAG, "  ✗ El paquete VTMS NO está instalado: ${e.message}")
            }
            
            Log.d(TAG, "════════════════════════════════════════════════════════════")
        }
        
        return false // No se pudo conectar con ningún método
    }
}

