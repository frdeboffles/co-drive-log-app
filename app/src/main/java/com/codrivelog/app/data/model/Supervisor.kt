package com.codrivelog.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A supervisor who accompanies the teen driver.
 *
 * @property id       Auto-generated primary key.
 * @property name     Full name of the supervisor.
 * @property initials Short initials used on the DR 2324 printed log.
 */
@Entity(tableName = "supervisors")
data class Supervisor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val initials: String,
)
