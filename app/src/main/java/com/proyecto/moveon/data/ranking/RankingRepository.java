package com.proyecto.moveon.data.ranking;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.ranking.dto.RankingItemDto;
import com.proyecto.moveon.data.remote.AuthenticatedApiClient;
import com.proyecto.moveon.utils.StringUtils;

import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.List;

/**
 * Repositorio del módulo de ranking.
 *
 * <p>Además de obtener el ranking, esta versión añade la operación de reportar
 * un perfil desde el listado. Se mantiene el mismo cliente autenticado que ya
 * usaba el módulo para no duplicar lógica de sesión, parsing ni manejo de
 * errores HTTP.</p>
 */
public final class RankingRepository {

    /**
     * Callback para respuestas que devuelven el listado del ranking.
     */
    public interface Callback {
        /**
         * Callback invocado con el resultado de pedir un listado de ranking.
         * Recibe la lista directamente del backend; {@link ApiResult} encapsula
         * tanto el éxito como el error para que el llamador no tenga que
         * gestionar excepciones por separado.
         *
         * @param result lista de ítems ordenados o el error que impidió obtenerlos.
         */
        void onResult(@NonNull ApiResult<List<RankingItemDto>> result);
    }

    /**
     * Callback para respuestas simples del backend tipo:
     * {@code {"estatus":"success","mensaje":"..."}}.
     */
    public interface MessageCallback {
        /**
         * Callback invocado con el resultado de una operación que devuelve un
         * único texto (por ejemplo, la provincia a la que pertenece el usuario
         * o un código de respuesta), envuelto en {@link ApiResult} para
         * distinguir éxito de error.
         *
         * @param result texto devuelto por el backend o el error ocurrido.
         */
        void onResult(@NonNull ApiResult<String> result);
    }

    private static final String ENDPOINT = "ranking/obtener";
    private static final String ENDPOINT_REPORT = "perfil/reporte";
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<RankingItemDto>>() {}.getType();

    private final AuthenticatedApiClient apiClient;

    /**
     * Crea el repositorio usando siempre el contexto de aplicación para evitar
     * fugas de Activities o Fragments.
     *
     * @param context cualquier contexto Android desde el que obtener el {@code applicationContext}.
     */
    public RankingRepository(@NonNull Context context) {
        this.apiClient = new AuthenticatedApiClient(context.getApplicationContext());
    }

    /**
     * Recupera el ranking nacional o filtrado por provincia.
     *
     * @param provincia provincia opcional. {@code null} o vacío implica ranking nacional.
     * @param callback  callback con el resultado ya parseado.
     */
    public void obtenerRanking(@Nullable String provincia, @NonNull Callback callback) {
        apiClient.get(buildUrl(provincia), this::parseRanking, callback::onResult);
    }

    /**
     * Envía un reporte de perfil al nuevo endpoint del backend.
     *
     * @param nombreUsuarioReportado usuario seleccionado en el ranking.
     * @param reportarNombre         true si el motivo incluye el nombre.
     * @param reportarFoto           true si el motivo incluye la foto.
     * @param observaciones          texto libre opcional.
     * @param callback               callback que recibe el mensaje final del backend.
     */
    public void reportarUsuario(@NonNull String nombreUsuarioReportado,
                                boolean reportarNombre,
                                boolean reportarFoto,
                                @Nullable String observaciones,
                                @NonNull MessageCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("nombre_usuario_reportado", nombreUsuarioReportado);
        body.addProperty("reportar_nombre", reportarNombre);
        body.addProperty("reportar_foto", reportarFoto);

        // El backend admite null. Solo enviamos texto si realmente contiene contenido útil.
        String observacionesLimpias = observaciones != null ? observaciones.trim() : null;
        if (StringUtils.hasText(observacionesLimpias)) {
            body.addProperty("observaciones", observacionesLimpias);
        } else {
            body.add("observaciones", JsonNull.INSTANCE);
        }

        apiClient.postJson(
                ENDPOINT_REPORT,
                body,
                this::parseMensaje,
                callback::onResult
        );
    }

    /**
     * Cancela todas las peticiones en vuelo asociadas a este repositorio.
     *
     * <p>Es útil cuando se destruye el Fragment o el BottomSheet para evitar
     * callbacks tardíos sobre una vista ya liberada.</p>
     */
    public void cancelAll() {
        apiClient.cancelAll();
    }

    /**
     * Construye la URL del ranking respetando el contrato actual del backend.
     *
     * @param provincia provincia opcional por la que filtrar.
     * @return ruta relativa que debe invocarse en el backend.
     */
    @NonNull
    private String buildUrl(@Nullable String provincia) {
        if (provincia == null || provincia.trim().isEmpty()) {
            return ENDPOINT;
        }
        try {
            //noinspection CharsetObjectCanBeUsed
            String encoded = URLEncoder.encode(provincia.trim(), "UTF-8");
            return ENDPOINT + "?provincia=" + encoded;
        } catch (java.io.UnsupportedEncodingException e) {
            return ENDPOINT;
        }
    }

    /**
     * Convierte el array JSON del backend en una lista de DTOs del ranking.
     *
     * @param json cuerpo recibido desde el endpoint de ranking.
     * @return lista de {@link RankingItemDto} o {@code null} si el payload no es un array válido.
     */
    @Nullable
    private List<RankingItemDto> parseRanking(@Nullable JsonElement json) {
        if (json == null || !json.isJsonArray()) {
            return null;
        }
        return GSON.fromJson(json, LIST_TYPE);
    }

    /**
     * Extrae el mensaje de una respuesta genérica del backend.
     *
     * @param json cuerpo recibido desde el backend.
     * @return mensaje textual o {@code "OK"} como fallback defensivo.
     */
    @NonNull
    private String parseMensaje(@Nullable JsonElement json) {
        if (json != null && json.isJsonObject()) {
            JsonObject obj = json.getAsJsonObject();
            if (obj.has("mensaje") && !obj.get("mensaje").isJsonNull()) {
                return obj.get("mensaje").getAsString();
            }
        }
        return "OK";
    }
}
