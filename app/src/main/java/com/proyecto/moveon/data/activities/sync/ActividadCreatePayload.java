package com.proyecto.moveon.data.activities.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.proyecto.moveon.data.local.entity.ActividadEntity;

/**
 * Payload JSON para sincronizar una actividad pendiente con el backend.
 */
public final class ActividadCreatePayload {

    private final String clientLocalId;
    private final String tipo;
    private final int distancia;
    private final int duracionTotal;
    private final int duracionMovimiento;
    private final int duracionParado;
    private final int duracionPausaManual;
    private final int caloriasQuemadas;
    @Nullable private final Integer pasos;
    private final int ritmoMedioMovimiento;
    private final int ritmoMedioTotal;
    private final int ritmoMaximo;
    private final int velocidadMediaKmhX100;
    private final int velocidadMaxKmhX100;
    private final int autoPausas;
    private final int pausasManuales;
    private final int alertasVelocidad;
    @Nullable private final String rutaPolilinea;
    @Nullable private final String rutaMapaUrl;
    private final String fechaRutaIso;

    /**
     * Empaqueta una actividad local lista para enviar al endpoint de
     * creación. Incluye el {@code clientLocalId} para que el backend
     * pueda deduplicar si el mismo payload llega dos veces (por reintentos
     * del Worker o por sincronización manual concurrente).
     *
     * @param clientLocalId UUID local estable usado como clave idempotente.
     * @param tipo tipo de actividad (p. ej. {@code "carrera"}).
     * @param distancia distancia total en metros.
     * @param duracionTotal duración total en segundos.
     * @param duracionMovimiento segundos clasificados como movimiento real.
     * @param duracionParado segundos parado sin pausar manualmente.
     * @param duracionPausaManual segundos en pausa manual.
     * @param caloriasQuemadas calorías quemadas estimadas.
     * @param pasos pasos detectados o {@code null} si el sensor no estaba disponible.
     * @param ritmoMedioMovimiento ritmo medio en movimiento en segundos por kilómetro.
     * @param ritmoMedioTotal ritmo medio total en segundos por kilómetro.
     * @param ritmoMaximo mejor ritmo sostenido en segundos por kilómetro.
     * @param velocidadMediaKmhX100 velocidad media en km/h x100.
     * @param velocidadMaxKmhX100 velocidad máxima en km/h x100.
     * @param autoPausas número de auto-pausas disparadas.
     * @param pausasManuales número de pausas manuales del usuario.
     * @param alertasVelocidad número de alertas por velocidad anómala.
     * @param rutaPolilinea polilínea codificada o {@code null} si no hay ruta.
     * @param rutaMapaUrl URL de la imagen estática del mapa o {@code null} si aún no se generó.
     * @param fechaRutaIso fecha/hora en ISO-8601.
     */
    public ActividadCreatePayload(
            @NonNull String clientLocalId,
            @NonNull String tipo,
            int distancia,
            int duracionTotal,
            int duracionMovimiento,
            int duracionParado,
            int duracionPausaManual,
            int caloriasQuemadas,
            @Nullable Integer pasos,
            int ritmoMedioMovimiento,
            int ritmoMedioTotal,
            int ritmoMaximo,
            int velocidadMediaKmhX100,
            int velocidadMaxKmhX100,
            int autoPausas,
            int pausasManuales,
            int alertasVelocidad,
            @Nullable String rutaPolilinea,
            @Nullable String rutaMapaUrl,
            @NonNull String fechaRutaIso) {
        this.clientLocalId = clientLocalId;
        this.tipo = tipo;
        this.distancia = distancia;
        this.duracionTotal = duracionTotal;
        this.duracionMovimiento = duracionMovimiento;
        this.duracionParado = duracionParado;
        this.duracionPausaManual = duracionPausaManual;
        this.caloriasQuemadas = caloriasQuemadas;
        this.pasos = pasos;
        this.ritmoMedioMovimiento = ritmoMedioMovimiento;
        this.ritmoMedioTotal = ritmoMedioTotal;
        this.ritmoMaximo = ritmoMaximo;
        this.velocidadMediaKmhX100 = velocidadMediaKmhX100;
        this.velocidadMaxKmhX100 = velocidadMaxKmhX100;
        this.autoPausas = autoPausas;
        this.pausasManuales = pausasManuales;
        this.alertasVelocidad = alertasVelocidad;
        this.rutaPolilinea = rutaPolilinea;
        this.rutaMapaUrl = rutaMapaUrl;
        this.fechaRutaIso = fechaRutaIso;
    }

    /**
     * Serializa el payload al JSON que espera el backend, con los nombres
     * en {@code snake_case}. Se hace a mano en vez de con Gson para
     * garantizar el orden y el control exacto de los campos opcionales.
     *
     * @return objeto JSON listo para adjuntar como cuerpo de la petición HTTP.
     */
    @NonNull
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("client_local_id", clientLocalId);
        json.addProperty("tipo", tipo);
        json.addProperty("distancia", distancia);
        json.addProperty("duracion_total", duracionTotal);
        json.addProperty("duracion_movimiento", duracionMovimiento);
        json.addProperty("duracion_parado", duracionParado);
        json.addProperty("duracion_pausa_manual", duracionPausaManual);
        json.addProperty("calorias_quemadas", caloriasQuemadas);
        if (pasos == null) json.add("pasos", JsonNull.INSTANCE);
        else json.addProperty("pasos", pasos);
        json.addProperty("ritmo_medio_movimiento", ritmoMedioMovimiento);
        json.addProperty("ritmo_medio_total", ritmoMedioTotal);
        json.addProperty("ritmo_maximo", ritmoMaximo);
        json.addProperty("velocidad_media_x100", velocidadMediaKmhX100);
        json.addProperty("velocidad_max_x100", velocidadMaxKmhX100);
        json.addProperty("auto_pausas", autoPausas);
        json.addProperty("pausas_manuales", pausasManuales);
        json.addProperty("alertas_velocidad", alertasVelocidad);

        if (rutaPolilinea == null) json.add("ruta_polilinea", JsonNull.INSTANCE);
        else json.addProperty("ruta_polilinea", rutaPolilinea);

        if (rutaMapaUrl == null) json.add("ruta_mapa_url", JsonNull.INSTANCE);
        else json.addProperty("ruta_mapa_url", rutaMapaUrl);

        json.addProperty("fecha_ruta", fechaRutaIso);
        return json;
    }

    /**
     * Convierte una fila de Room en un payload de creación remoto. Se usa
     * al drenar la cola de actividades pendientes: el Worker lee cada
     * entidad, la pasa por este método y la envía al backend.
     *
     * @param entity fila local de la base de datos con el estado actual de la actividad.
     * @return payload equivalente listo para serializar y enviar al endpoint de creación.
     */
    @NonNull
    public static ActividadCreatePayload fromEntity(@NonNull ActividadEntity entity) {
        return new ActividadCreatePayload(
                entity.localId,
                entity.tipo,
                entity.distancia,
                entity.duracionTotal,
                entity.duracionMovimiento,
                entity.duracionParado,
                entity.duracionPausaManual,
                entity.caloriasQuemadas,
                entity.pasos,
                entity.ritmoMedioMovimiento,
                entity.ritmoMedioTotal,
                entity.ritmoMaximo,
                entity.velocidadMediaKmhX100,
                entity.velocidadMaxKmhX100,
                entity.autoPausas,
                entity.pausasManuales,
                entity.alertasVelocidad,
                entity.rutaPolilinea,
                entity.rutaMapaUrl,
                entity.fechaRuta
        );
    }
}
