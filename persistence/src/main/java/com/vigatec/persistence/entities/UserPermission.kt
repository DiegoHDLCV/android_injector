package com.vigatec.persistence.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "user_permissions",
    primaryKeys = ["userId", "permissionId"],
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Permission::class,
            parentColumns = ["id"],
            childColumns = ["permissionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId"), Index("permissionId")]
)
data class UserPermission(
    val userId: Int,
    val permissionId: String
)
