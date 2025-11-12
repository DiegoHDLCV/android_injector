package com.vigatec.injector.util

import com.vigatec.injector.data.local.entity.Permission

/**
 * Catálogo centralizado de permisos del sistema Injector.
 *
 * Mantiene los identificadores, nombres descriptivos y descripciones de cada permiso,
 * además de colecciones utilitarias para inicialización y asignaciones por defecto.
 */
object PermissionsCatalog {

    // Identificadores de permisos del sistema
    const val KEY_VAULT = "key_vault"
    const val SELECT_KTK = "select_ktk"
    const val EXECUTE_INJECTION = "execute_injection"
    const val MANAGE_PROFILES = "manage_profiles"
    const val CEREMONY_KEK = "ceremony_kek"
    const val CEREMONY_OPERATIONAL = "ceremony_operational"
    const val VIEW_LOGS = "view_logs"
    const val MANAGE_USERS = "manage_users"
    const val EXPORT_IMPORT_KEYS = "export_import_keys"

    /**
     * Lista completa de permisos soportados por la aplicación.
     */
    val SYSTEM_PERMISSIONS: List<Permission> = listOf(
        Permission(
            id = KEY_VAULT,
            name = "Ver almacén de llaves",
            description = "Permite visualizar el almacén de llaves y consultar KCV existentes."
        ),
        Permission(
            id = SELECT_KTK,
            name = "Seleccionar KTK",
            description = "Autoriza la selección de llaves maestras (KTK) disponibles para inyección."
        ),
        Permission(
            id = EXECUTE_INJECTION,
            name = "Ejecutar inyecciones",
            description = "Permite ejecutar inyecciones utilizando perfiles existentes y llaves aprobadas."
        ),
        Permission(
            id = MANAGE_PROFILES,
            name = "Gestionar perfiles",
            description = "Habilita la creación, edición y eliminación de perfiles de inyección."
        ),
        Permission(
            id = CEREMONY_KEK,
            name = "Ceremonia KEK",
            description = "Autoriza la ejecución de ceremonias para llaves KEK."
        ),
        Permission(
            id = CEREMONY_OPERATIONAL,
            name = "Ceremonia operacional",
            description = "Autoriza ceremonias para llaves operacionales."
        ),
        Permission(
            id = VIEW_LOGS,
            name = "Ver bitácoras",
            description = "Permite acceder al historial de logs y registros de auditoría."
        ),
        Permission(
            id = MANAGE_USERS,
            name = "Gestionar usuarios",
            description = "Autoriza la creación, modificación y desactivación de cuentas de usuario."
        ),
        Permission(
            id = EXPORT_IMPORT_KEYS,
            name = "Exportar/Importar llaves",
            description = "Permite exportar o importar llaves desde y hacia la aplicación."
        )
    )

    /**
     * Conjunto de IDs de permisos permitidos para operadores.
     */
    val OPERATOR_DEFAULT_PERMISSION_IDS: Set<String> = setOf(
        KEY_VAULT,
        SELECT_KTK,
        EXECUTE_INJECTION
    )

    /**
     * Conjunto de todos los IDs de permisos disponibles.
     */
    val SYSTEM_PERMISSION_IDS: Set<String> =
        SYSTEM_PERMISSIONS.map { it.id }.toSet()
}

