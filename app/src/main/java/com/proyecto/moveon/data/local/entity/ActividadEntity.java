package com.proyecto.moveon.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entidad Room de actividades sincronizables.
 *
 * <p>Incluye métricas enriquecidas del tracking para no perder información
 * entre la sesión local y el backend.</p>
 */
@Entity(
        tableName = "actividades_locales",
        indices = {
                @Index(value = {"accountKey", "fecha_ruta"}, name = "ix_actividad_account_fecha"),
                @Index(value = {"accountKey", "remoteId"}, unique = true, name = "ix_actividad_account_remote")
        }
)
public class ActividadEntity {

    @PrimaryKey
    @NonNull
    public String localId;

    @NonNull
    public String accountKey;

    @Nullable
    public Integer remoteId;

    @NonNull
    public String tipo;

    public int distancia;

    @ColumnInfo(name = "duracion_total")
    public int duracionTotal;

    @ColumnInfo(name = "duracion_movimiento")
    public int duracionMovimiento;

    @ColumnInfo(name = "duracion_parado")
    public int duracionParado;

    @ColumnInfo(name = "duracion_pausa_manual")
    public int duracionPausaManual;

    @ColumnInfo(name = "calorias_quemadas")
    public int caloriasQuemadas;

    @ColumnInfo(name = "ritmo_medio_movimiento")
    public int ritmoMedioMovimiento;

    @ColumnInfo(name = "ritmo_medio_total")
    public int ritmoMedioTotal;

    /** Mejor ritmo sostenido válido en seg/km. */
    @ColumnInfo(name = "ritmo_maximo")
    public int ritmoMaximo;

    @ColumnInfo(name = "velocidad_media_x100")
    public int velocidadMediaKmhX100;

    @ColumnInfo(name = "velocidad_max_x100")
    public int velocidadMaxKmhX100;

    @ColumnInfo(name = "auto_pausas")
    public int autoPausas;

    @ColumnInfo(name = "pausas_manuales")
    public int pausasManuales;

    @ColumnInfo(name = "alertas_velocidad")
    public int alertasVelocidad;

    @Nullable
    @ColumnInfo(name = "ruta_polilinea")
    public String rutaPolilinea;

    @Nullable
    @ColumnInfo(name = "ruta_mapa_url")
    public String rutaMapaUrl;

    @NonNull
    @ColumnInfo(name = "fecha_ruta")
    public String fechaRuta;

    @NonNull
    @ColumnInfo(name = "sync_state")
    public String syncState;

    @Nullable
    @ColumnInfo(name = "last_error")
    public String lastError;

    @ColumnInfo(name = "created_at_ms")
    public long createdAtMs;

    @ColumnInfo(name = "updated_at_ms")
    public long updatedAtMs;
}
