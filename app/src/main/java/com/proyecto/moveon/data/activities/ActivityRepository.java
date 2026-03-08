package com.proyecto.moveon.data.activities;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.activities.dto.GuardarActividadRequestDto;
import com.proyecto.moveon.data.activities.dto.GuardarActividadResponseDto;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.data.remote.AuthenticatedApiClient;

/**
 * Repositorio de actividades deportivas.

 * Encapsula las llamadas a:
 * - POST /actividad/guardar
 * - GET /perfil/información (para leer el peso y calcular calorías en cliente)
 */
public final class ActivityRepository {

    private static final String ENDPOINT_GUARDAR_ACTIVIDAD = "actividad/guardar";
    private static final String ENDPOINT_PERFIL_INFO       = "perfil/informacion";

    private final AuthenticatedApiClient apiClient;
    private final Gson                   gson;

    public ActivityRepository(@NonNull Context context) {
        this.apiClient = new AuthenticatedApiClient(context.getApplicationContext());
        this.gson      = new Gson();
    }

    // -------------------------------------------------------------------------
    // Interfaz de callback
    // -------------------------------------------------------------------------

    public interface Callback<T> {
        void onResult(ApiResult<T> result);
    }

    // -------------------------------------------------------------------------
    // Guardar actividad
    // -------------------------------------------------------------------------

    /**
     * Envía una actividad finalizada al backend.
     *
     * @param request  DTO con los datos de la actividad
     * @param callback resultado envuelto en {@link ApiResult}
     */
    public void guardarActividad(
            @NonNull GuardarActividadRequestDto request,
            @NonNull Callback<GuardarActividadResponseDto> callback) {

        JsonElement body = gson.toJsonTree(request);

        apiClient.postJson(
                ENDPOINT_GUARDAR_ACTIVIDAD,
                body,
                json -> {
                    if (json == null || !json.isJsonObject()) return null;
                    return gson.fromJson(json, GuardarActividadResponseDto.class);
                },
                result -> callback.onResult(result)
        );
    }

    // -------------------------------------------------------------------------
    // Obtener perfil (peso para calcular calorías)
    // -------------------------------------------------------------------------

    /**
     * Obtiene el perfil del usuario autenticado.

     * Se usa únicamente para leer {@code peso} al arrancar el ViewModel.
     *
     * @param callback resultado envuelto en {@link ApiResult}
     */
    public void obtenerPerfil(@NonNull Callback<ProfileInfoDto> callback) {
        apiClient.get(
                ENDPOINT_PERFIL_INFO,
                json -> {
                    if (json == null || !json.isJsonObject()) return null;
                    return gson.fromJson(json, ProfileInfoDto.class);
                },
                result -> callback.onResult(result)
        );
    }

    // -------------------------------------------------------------------------
    // Ciclo de vida
    // -------------------------------------------------------------------------

    /** Cancela todas las peticiones en vuelo. Llamar desde {@code onCleared()} del ViewModel. */
    public void cancelAll() {
        apiClient.cancelAll();
    }
}