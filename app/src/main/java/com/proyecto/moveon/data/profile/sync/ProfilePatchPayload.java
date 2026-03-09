package com.proyecto.moveon.data.profile.sync;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public final class ProfilePatchPayload {

    private final JsonObject json = new JsonObject();

    public ProfilePatchPayload nombreReal(String value) {
        if (value == null) json.add("nombre_real", JsonNull.INSTANCE);
        else json.addProperty("nombre_real", value);
        return this;
    }

    public ProfilePatchPayload email(String value) {
        if (value == null) json.add("email", JsonNull.INSTANCE);
        else json.addProperty("email", value);
        return this;
    }

    public ProfilePatchPayload fechaNacimiento(String value) {
        if (value == null) json.add("fecha_nacimiento", JsonNull.INSTANCE);
        else json.addProperty("fecha_nacimiento", value);
        return this;
    }

    public ProfilePatchPayload genero(String value) {
        if (value == null) json.add("genero", JsonNull.INSTANCE);
        else json.addProperty("genero", value);
        return this;
    }

    public ProfilePatchPayload altura(Integer value) {
        if (value == null) json.add("altura", JsonNull.INSTANCE);
        else json.addProperty("altura", value);
        return this;
    }

    public ProfilePatchPayload peso(Double value) {
        if (value == null) json.add("peso", JsonNull.INSTANCE);
        else json.addProperty("peso", value);
        return this;
    }

    public ProfilePatchPayload provincia(String value) {
        if (value == null) json.add("provincia", JsonNull.INSTANCE);
        else json.addProperty("provincia", value);
        return this;
    }

    public ProfilePatchPayload perfilVisible(boolean value) {
        json.addProperty("perfil_visible", value);
        return this;
    }

    public JsonObject toJson() {
        return json.deepCopy();
    }

    public boolean isEmpty() {
        return json.size() == 0;
    }
}
