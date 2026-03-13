package com.proyecto.moveon.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Caché local de las preferencias del usuario almacenadas en el servidor.
 * La PK es el nombre de usuario en minúsculas para soportar multi-cuenta
 * en el mismo dispositivo sin mezclar datos entre cuentas.
 * Esta tabla NO es la fuente de verdad — el servidor lo es.
 * Se usa para respuesta inmediata en UI y para sobrevivir reinstalaciones
 * una vez que el servidor sincroniza los datos al hacer login.
 */
@Entity(tableName = "user_prefs")
public class UserPrefsEntity {

    /** Username en minúsculas — clave que identifica la cuenta. */
    @PrimaryKey
    @NonNull
    public String accountKey = "";

    /** Objetivo semanal de distancia en metros. Default: 50 000 m (50 km). */
    public long weeklyGoalMeters;

    /** Objetivo mensual de distancia en metros. Default: 150 000 m (150 km). */
    public long monthlyGoalMeters;

    /** Timestamp (epoch ms) de la última vez que se escribió esta fila. */
    public long updatedAtMs;
}