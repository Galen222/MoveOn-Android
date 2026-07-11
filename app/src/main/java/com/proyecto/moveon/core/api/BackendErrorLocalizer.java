package com.proyecto.moveon.core.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.utils.StringUtils;

import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Clase responsable de localizar los códigos de error devueltos por el backend.
 */
@SuppressWarnings("Java9CollectionFactory")
public final class BackendErrorLocalizer {

    private static final Map<String, Integer> ERROR_RESOURCE_IDS = createErrorResourceIds();

    /**
     * Constructor privado: clase de utilidades con sólo métodos estáticos,
     * no está pensada para instanciarse.
     */
    private BackendErrorLocalizer() {}

    /**
     * Traduce un código de error del backend al mensaje legible en el idioma
     * actual de la app. Normaliza antes el código (minúsculas, guiones bajos)
     * y, si es un {@code rate_limit_exceeded} con {@code retry-after}, usa la
     * plantilla que incluye los segundos de espera.
     *
     * @param context contexto desde el que se resuelve el locale actual.
     * @param errorCode código tal y como llega del backend, admite guiones o mayúsculas.
     * @param retryAfterSeconds segundos sugeridos de espera para el caso {@code rate_limit_exceeded}.
     * @return mensaje localizado, o {@code null} si el código es vacío o no se reconoce.
     */
    @Nullable
    public static String localize(@NonNull Context context,
                                  @Nullable String errorCode,
                                  @Nullable String retryAfterSeconds) {
        context = AppLanguageManager.localizedContext(context);
        if (!StringUtils.hasText(errorCode)) return null;

        String normalized = normalizeErrorCode(errorCode);
        if (!StringUtils.hasText(normalized)) return null;

        if ("rate_limit_exceeded".equals(normalized) && StringUtils.hasText(retryAfterSeconds)) {
            return context.getString(R.string.api_error_rate_limit_retry, retryAfterSeconds);
        }

        Integer resId = ERROR_RESOURCE_IDS.get(normalized);
        if (resId == null) return null;

        return context.getString(resId);
    }

    /**
     * Normaliza un código de error a {@code snake_case} puro: recorta,
     * elimina marcas diacríticas, pasa a minúsculas, sustituye cualquier
     * carácter no alfanumérico por {@code _} y colapsa los guiones bajos repetidos. Así el mismo error
     * escrito como {@code "RATE-LIMIT EXCEEDED"} o {@code "rate_limit_exceeded"}
     * acaba resolviéndose a la misma clave de traducción.
     *
     * @param errorCode código a normalizar.
     * @return versión {@code snake_case} del código, sin guiones bajos iniciales ni finales.
     */
    @NonNull
    public static String normalizeErrorCode(@NonNull String errorCode) {
        String asciiCode = Normalizer
                .normalize(errorCode.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        return asciiCode
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
    }

    @NonNull
    private static Map<String, Integer> createErrorResourceIds() {
        Map<String, Integer> ids = new HashMap<>();
        ids.put("access_token_invalid_or_expired", R.string.backend_error_access_token_invalid_or_expired);
        ids.put("account_terms_acceptance_required", R.string.backend_error_account_terms_acceptance_required);
        ids.put("activity_date_in_future", R.string.backend_error_activity_date_in_future);
        ids.put("activity_not_found", R.string.backend_error_activity_not_found);
        ids.put("activity_type_invalid", R.string.backend_error_activity_type_invalid);
        ids.put("activity_type_must_be_text", R.string.backend_error_activity_type_must_be_text);
        ids.put("activity_type_required", R.string.backend_error_activity_type_required);
        ids.put("age_restriction_not_met", R.string.backend_error_age_restriction_not_met);
        ids.put("bad_request", R.string.backend_error_bad_request);
        ids.put("birth_date_in_future", R.string.backend_error_birth_date_in_future);
        ids.put("birth_date_invalid", R.string.backend_error_birth_date_invalid);
        ids.put("birth_date_null", R.string.backend_error_birth_date_null);
        ids.put("birth_date_required", R.string.backend_error_birth_date_required);
        ids.put("burned_calories_required", R.string.backend_error_burned_calories_required);
        ids.put("calories_must_be_integer", R.string.backend_error_calories_must_be_integer);
        ids.put("calories_must_be_positive", R.string.backend_error_calories_must_be_positive);
        ids.put("calories_out_of_range", R.string.backend_error_calories_out_of_range);
        ids.put("steps_must_be_integer", R.string.backend_error_steps_must_be_integer);
        ids.put("steps_negative", R.string.backend_error_steps_negative);
        ids.put("steps_out_of_range", R.string.backend_error_steps_out_of_range);
        ids.put("cloudinary_invalid_url", R.string.backend_error_cloudinary_invalid_url);
        ids.put("code_empty", R.string.backend_error_code_empty);
        ids.put("code_expired", R.string.backend_error_code_expired);
        ids.put("code_invalid_length", R.string.backend_error_code_invalid_length);
        ids.put("code_must_be_numeric", R.string.backend_error_code_must_be_numeric);
        ids.put("code_required", R.string.backend_error_code_required);
        ids.put("conflict", R.string.backend_error_conflict);
        ids.put("content_validation_unavailable", R.string.backend_error_content_validation_unavailable);
        ids.put("cors_origins_invalid_format", R.string.backend_error_cors_origins_invalid_format);
        ids.put("distance_must_be_integer", R.string.backend_error_distance_must_be_integer);
        ids.put("distance_must_be_positive", R.string.backend_error_distance_must_be_positive);
        ids.put("distance_out_of_range", R.string.backend_error_distance_out_of_range);
        ids.put("distance_required", R.string.backend_error_distance_required);
        ids.put("duration_must_be_integer", R.string.backend_error_duration_must_be_integer);
        ids.put("duration_must_be_positive", R.string.backend_error_duration_must_be_positive);
        ids.put("duration_required", R.string.backend_error_duration_required);
        ids.put("duration_too_long", R.string.backend_error_duration_too_long);
        ids.put("email_already_in_use", R.string.backend_error_email_already_in_use);
        ids.put("email_format_invalid", R.string.backend_error_email_format_invalid);
        ids.put("email_must_be_text", R.string.backend_error_email_must_be_text);
        ids.put("email_null", R.string.backend_error_email_null);
        ids.put("email_required", R.string.backend_error_email_required);
        ids.put("encrypted_password_empty", R.string.backend_error_encrypted_password_empty);
        ids.put("encrypted_password_must_be_string", R.string.backend_error_encrypted_password_must_be_string);
        ids.put("encrypted_password_too_long", R.string.backend_error_encrypted_password_too_long);
        ids.put("expires_at_required", R.string.backend_error_expires_at_required);
        ids.put("favicon_not_found", R.string.backend_error_favicon_not_found);
        ids.put("forbidden", R.string.backend_error_forbidden);
        ids.put("gender_invalid", R.string.backend_error_gender_invalid);
        ids.put("gender_must_be_text", R.string.backend_error_gender_must_be_text);
        ids.put("height_must_be_integer_centimeters", R.string.backend_error_height_must_be_integer_centimeters);
        ids.put("height_out_of_range", R.string.backend_error_height_out_of_range);
        ids.put("identifier_empty", R.string.backend_error_identifier_empty);
        ids.put("identifier_required", R.string.backend_error_identifier_required);
        ids.put("image_file_too_large", R.string.backend_error_image_file_too_large);
        ids.put("image_format_not_allowed", R.string.backend_error_image_format_not_allowed);
        ids.put("image_processing_failed", R.string.backend_error_image_processing_failed);
        ids.put("image_save_failed", R.string.backend_error_image_save_failed);
        ids.put("image_too_large", R.string.backend_error_image_too_large);
        ids.put("image_upload_failed", R.string.backend_error_image_upload_failed);
        ids.put("internal_server_error", R.string.backend_error_internal_server_error);
        ids.put("invalid_app_origin", R.string.backend_error_invalid_app_origin);
        ids.put("invalid_credentials", R.string.backend_error_invalid_credentials);
        ids.put("invalid_image_file", R.string.backend_error_invalid_image_file);
        ids.put("malicious_content_detected", R.string.backend_error_malicious_content_detected);
        ids.put("map_url_invalid", R.string.backend_error_map_url_invalid);
        ids.put("map_url_must_be_text", R.string.backend_error_map_url_must_be_text);
        ids.put("map_url_too_long", R.string.backend_error_map_url_too_long);
        ids.put("monthly_goal_must_be_integer", R.string.backend_error_monthly_goal_must_be_integer);
        ids.put("monthly_goal_must_be_integer_meters", R.string.backend_error_monthly_goal_must_be_integer_meters);
        ids.put("monthly_goal_null", R.string.backend_error_monthly_goal_null);
        ids.put("monthly_goal_out_of_range", R.string.backend_error_monthly_goal_out_of_range);
        ids.put("new_password_required", R.string.backend_error_new_password_required);
        ids.put("not_found", R.string.backend_error_not_found);
        ids.put("password_missing_number", R.string.backend_error_password_missing_number);
        ids.put("password_missing_uppercase", R.string.backend_error_password_missing_uppercase);
        ids.put("password_null", R.string.backend_error_password_null);
        ids.put("password_required", R.string.backend_error_password_required);
        ids.put("password_too_long_bytes", R.string.backend_error_password_too_long_bytes);
        ids.put("password_too_short", R.string.backend_error_password_too_short);
        ids.put("payload_too_large", R.string.backend_error_payload_too_large);
        ids.put("polyline_must_be_text", R.string.backend_error_polyline_must_be_text);
        ids.put("processed_image_too_large", R.string.backend_error_processed_image_too_large);
        ids.put("profile_photo_empty", R.string.backend_error_profile_photo_empty);
        ids.put("profile_photo_must_be_string", R.string.backend_error_profile_photo_must_be_string);
        ids.put("profile_photo_too_long", R.string.backend_error_profile_photo_too_long);
        ids.put("profile_photo_update_failed", R.string.backend_error_profile_photo_update_failed);
        ids.put("profile_private", R.string.backend_error_profile_private);
        ids.put("profile_visibility_must_be_boolean", R.string.backend_error_profile_visibility_must_be_boolean);
        ids.put("profile_visibility_null", R.string.backend_error_profile_visibility_null);
        ids.put("province_invalid", R.string.backend_error_province_invalid);
        ids.put("province_must_be_text", R.string.backend_error_province_must_be_text);
        ids.put("public_base_url_invalid_scheme", R.string.backend_error_public_base_url_invalid_scheme);
        ids.put("public_base_url_must_be_string", R.string.backend_error_public_base_url_must_be_string);
        ids.put("rate_limit_exceeded", R.string.backend_error_rate_limit_exceeded);
        ids.put("real_name_inappropriate_or_not_allowed", R.string.backend_error_real_name_inappropriate_or_not_allowed);
        ids.put("real_name_invalid_characters", R.string.backend_error_real_name_invalid_characters);
        ids.put("real_name_must_be_text", R.string.backend_error_real_name_must_be_text);
        ids.put("real_name_too_long", R.string.backend_error_real_name_too_long);
        ids.put("real_name_too_short", R.string.backend_error_real_name_too_short);
        ids.put("recovery_code_hash_invalid", R.string.backend_error_recovery_code_hash_invalid);
        ids.put("recovery_code_hash_must_be_string", R.string.backend_error_recovery_code_hash_must_be_string);
        ids.put("recovery_code_or_email_invalid", R.string.backend_error_recovery_code_or_email_invalid);
        ids.put("refresh_token_empty", R.string.backend_error_refresh_token_empty);
        ids.put("refresh_token_expired", R.string.backend_error_refresh_token_expired);
        ids.put("refresh_token_invalid", R.string.backend_error_refresh_token_invalid);
        ids.put("refresh_token_invalid_family", R.string.backend_error_refresh_token_invalid_family);
        ids.put("refresh_token_invalid_jti", R.string.backend_error_refresh_token_invalid_jti);
        ids.put("refresh_token_invalid_or_expired", R.string.backend_error_refresh_token_invalid_or_expired);
        ids.put("refresh_token_invalid_or_reused", R.string.backend_error_refresh_token_invalid_or_reused);
        ids.put("refresh_token_invalid_sub", R.string.backend_error_refresh_token_invalid_sub);
        ids.put("refresh_token_required", R.string.backend_error_refresh_token_required);
        ids.put("refresh_token_reused", R.string.backend_error_refresh_token_reused);
        ids.put("registration_consents_required", R.string.backend_error_registration_consents_required);
        ids.put("resource_not_found", R.string.backend_error_resource_not_found);
        ids.put("route_date_invalid", R.string.backend_error_route_date_invalid);
        ids.put("route_invalid", R.string.backend_error_route_invalid);
        ids.put("secrets_must_be_distinct", R.string.backend_error_secrets_must_be_distinct);
        ids.put("session_identifier_empty", R.string.backend_error_session_identifier_empty);
        ids.put("session_identifier_must_be_string", R.string.backend_error_session_identifier_must_be_string);
        ids.put("session_identifier_too_long", R.string.backend_error_session_identifier_too_long);
        ids.put("session_token_missing", R.string.backend_error_session_token_missing);
        ids.put("terms_acceptance_must_be_boolean", R.string.backend_error_terms_acceptance_must_be_boolean);
        ids.put("terms_accepted_at_in_future", R.string.backend_error_terms_accepted_at_in_future);
        ids.put("terms_accepted_at_invalid", R.string.backend_error_terms_accepted_at_invalid);
        ids.put("terms_accepted_at_invalid_datetime", R.string.backend_error_terms_accepted_at_invalid_datetime);
        ids.put("terms_version_must_be_text", R.string.backend_error_terms_version_must_be_text);
        ids.put("terms_version_required", R.string.backend_error_terms_version_required);
        ids.put("terms_version_too_long", R.string.backend_error_terms_version_too_long);
        ids.put("token_expired", R.string.backend_error_token_expired);
        ids.put("token_hash_invalid", R.string.backend_error_token_hash_invalid);
        ids.put("token_hash_must_be_string", R.string.backend_error_token_hash_must_be_string);
        ids.put("token_hash_required", R.string.backend_error_token_hash_required);
        ids.put("token_invalid_or_expired", R.string.backend_error_token_invalid_or_expired);
        ids.put("token_missing_valid_user", R.string.backend_error_token_missing_valid_user);
        ids.put("token_type_mismatch", R.string.backend_error_token_type_mismatch);
        ids.put("total_calories_must_be_integer", R.string.backend_error_total_calories_must_be_integer);
        ids.put("total_calories_negative", R.string.backend_error_total_calories_negative);
        ids.put("total_distance_must_be_integer", R.string.backend_error_total_distance_must_be_integer);
        ids.put("total_distance_negative", R.string.backend_error_total_distance_negative);
        ids.put("unauthorized", R.string.backend_error_unauthorized);
        ids.put("user_id_must_be_integer", R.string.backend_error_user_id_must_be_integer);
        ids.put("user_id_must_be_positive", R.string.backend_error_user_id_must_be_positive);
        ids.put("user_not_found", R.string.backend_error_user_not_found);
        ids.put("user_profile_not_found", R.string.backend_error_user_profile_not_found);
        ids.put("username_already_in_use", R.string.backend_error_username_already_in_use);
        ids.put("username_empty", R.string.backend_error_username_empty);
        ids.put("username_inappropriate_or_not_allowed", R.string.backend_error_username_inappropriate_or_not_allowed);
        ids.put("username_invalid_format", R.string.backend_error_username_invalid_format);
        ids.put("username_must_be_text", R.string.backend_error_username_must_be_text);
        ids.put("username_or_email_already_in_use", R.string.backend_error_username_or_email_already_in_use);
        ids.put("username_required", R.string.backend_error_username_required);
        ids.put("username_too_long", R.string.backend_error_username_too_long);
        ids.put("username_too_short", R.string.backend_error_username_too_short);
        ids.put("validation_error", R.string.backend_error_validation_error);
        ids.put("weekly_goal_must_be_integer", R.string.backend_error_weekly_goal_must_be_integer);
        ids.put("weekly_goal_must_be_integer_meters", R.string.backend_error_weekly_goal_must_be_integer_meters);
        ids.put("weekly_goal_null", R.string.backend_error_weekly_goal_null);
        ids.put("weekly_goal_out_of_range", R.string.backend_error_weekly_goal_out_of_range);
        ids.put("weight_must_be_kilogram_number", R.string.backend_error_weight_must_be_kilogram_number);
        ids.put("weight_out_of_range", R.string.backend_error_weight_out_of_range);
        return Collections.unmodifiableMap(ids);
    }
}
