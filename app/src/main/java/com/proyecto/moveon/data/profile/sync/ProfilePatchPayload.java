package com.proyecto.moveon.data.profile.sync;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
/**
 * Builder mutable que compone el cuerpo JSON de un parche de perfil.
 */
public final class ProfilePatchPayload {

    private final JsonObject json = new JsonObject();

    /**
     * Asigna el campo de nombre real al parche, enviando {@link JsonNull} cuando se quiere vaciar.
     *
     * @param value valor de nombre real o {@code null} para limpiar el campo.
     * @return la propia instancia para permitir encadenado fluido.
     */
    public ProfilePatchPayload nombreReal(String value) {
        if (value == null) json.add("nombre_real", JsonNull.INSTANCE);
        else json.addProperty("nombre_real", value);
        return this;
    }

    /**
     * Asigna el email al parche, preservando la intención de borrar mediante {@link JsonNull}.
     *
     * @param value nuevo email o {@code null} para limpiar el campo.
     * @return la propia instancia para seguir construyendo el payload.
     */
    public ProfilePatchPayload email(String value) {
        if (value == null) json.add("email", JsonNull.INSTANCE);
        else json.addProperty("email", value);
        return this;
    }

    /**
     * Asigna la fecha de nacimiento serializada para el parche remoto.
     *
     * @param value fecha en formato esperado por el backend o {@code null} para vaciarla.
     * @return la propia instancia del builder.
     */
    public ProfilePatchPayload fechaNacimiento(String value) {
        if (value == null) json.add("fecha_nacimiento", JsonNull.INSTANCE);
        else json.addProperty("fecha_nacimiento", value);
        return this;
    }

    /**
     * Añade el campo de género al payload del parche.
     *
     * @param value género a enviar o {@code null} para representarlo como valor nulo.
     * @return la propia instancia del builder.
     */
    public ProfilePatchPayload genero(String value) {
        if (value == null) json.add("genero", JsonNull.INSTANCE);
        else json.addProperty("genero", value);
        return this;
    }

    /**
     * Añade la altura al parche respetando la semántica de borrado mediante nulos.
     *
     * @param value altura en centímetros o {@code null} si debe limpiarse.
     * @return la propia instancia del builder.
     */
    public ProfilePatchPayload altura(Integer value) {
        if (value == null) json.add("altura", JsonNull.INSTANCE);
        else json.addProperty("altura", value);
        return this;
    }

    /**
     * Añade el peso al parche respetando la semántica de borrado mediante nulos.
     *
     * @param value peso en kilogramos o {@code null} si debe limpiarse.
     * @return la propia instancia del builder.
     */
    public ProfilePatchPayload peso(Double value) {
        if (value == null) json.add("peso", JsonNull.INSTANCE);
        else json.addProperty("peso", value);
        return this;
    }

    /**
     * Añade la provincia al parche remoto.
     *
     * @param value provincia seleccionada o {@code null} para limpiar el campo.
     * @return la propia instancia del builder.
     */
    public ProfilePatchPayload provincia(String value) {
        if (value == null) json.add("provincia", JsonNull.INSTANCE);
        else json.addProperty("provincia", value);
        return this;
    }

    /**
     * Añade el flag de visibilidad pública del perfil.
     *
     * @param value valor booleano que debe enviarse al backend.
     * @return la propia instancia del builder.
     */
    public ProfilePatchPayload perfilVisible(boolean value) {
        json.addProperty("perfil_visible", value);
        return this;
    }

    /**
     * Devuelve una copia profunda del JSON acumulado para evitar mutaciones externas.
     *
     * @return {@link JsonObject} listo para enviarse por la capa remota.
     */
    public JsonObject toJson() {
        return json.deepCopy();
    }

    /**
     * Indica si todavía no se ha añadido ningún campo al parche.
     *
     * @return {@code true} cuando el payload no contiene cambios.
     */
    public boolean isEmpty() {
        return json.isEmpty();
    }
}
