package com.proyecto.moveon.domain.profile;

import androidx.annotation.Nullable;
/**
 * Clase responsable de perfil usuario.
 */
public final class PerfilUsuario {
    public final String nombreUsuario;
    public final String email;
    public final String fechaNacimiento;
    public final int totalPuntos;
    @Nullable public final String nombreReal;
    @Nullable public final String genero;
    @Nullable public final Integer altura;
    @Nullable public final Double peso;
    @Nullable public final String provincia;
    @Nullable public final String fotoPerfil;
    public final int fotoVersion;
    @Nullable public final String localPhotoPath;
    @Nullable public final String pendingLocalPhotoPath;
    @Nullable public final String photoSyncState;
    public final boolean perfilVisible;

    /**
     * Construye la representación de dominio del perfil del usuario a partir
     * de los datos recibidos del backend y del estado local de la foto.
     *
     * <p>Los campos relativos a la foto ({@code localPhotoPath},
     * {@code pendingLocalPhotoPath} y {@code photoSyncState}) modelan el
     * cambio offline-first: la UI ve primero la foto local y la
     * sincronización la sustituye por la URL remota cuando el backend
     * responde OK.</p>
     *
     * @param nombreUsuario nombre de usuario público.
     * @param email email de la cuenta.
     * @param fechaNacimiento fecha de nacimiento en formato {@code yyyy-MM-dd}.
     * @param totalPuntos puntos totales del ranking.
     * @param nombreReal nombre real opcional (distinto del nombre de usuario).
     * @param genero género declarado, o {@code null} si no se ha fijado.
     * @param altura altura en centímetros, o {@code null} si no se ha fijado.
     * @param peso peso en kilogramos, o {@code null} si no se ha fijado.
     * @param provincia provincia asociada, usada para el ranking provincial.
     * @param fotoPerfil URL remota de la foto actual, o {@code null} si no hay.
     * @param fotoVersion versión numérica usada para invalidar la caché de la foto.
     * @param localPhotoPath ruta en disco a la foto ya sincronizada con el servidor.
     * @param pendingLocalPhotoPath ruta en disco a una foto nueva que aún no ha subido.
     * @param photoSyncState estado de sincronización de la foto (sincronizada, pendiente, con error).
     * @param perfilVisible {@code true} si el perfil aparece en ranking y búsquedas.
     */
    public PerfilUsuario(
            String nombreUsuario,
            String email,
            String fechaNacimiento,
            int totalPuntos,
            @Nullable String nombreReal,
            @Nullable String genero,
            @Nullable Integer altura,
            @Nullable Double peso,
            @Nullable String provincia,
            @Nullable String fotoPerfil,
            int fotoVersion,
            @Nullable String localPhotoPath,
            @Nullable String pendingLocalPhotoPath,
            @Nullable String photoSyncState,
            boolean perfilVisible) {
        this.nombreUsuario = nombreUsuario;
        this.email = email;
        this.fechaNacimiento = fechaNacimiento;
        this.totalPuntos = totalPuntos;
        this.nombreReal = nombreReal;
        this.genero = genero;
        this.altura = altura;
        this.peso = peso;
        this.provincia = provincia;
        this.fotoPerfil = fotoPerfil;
        this.fotoVersion = fotoVersion;
        this.localPhotoPath = localPhotoPath;
        this.pendingLocalPhotoPath = pendingLocalPhotoPath;
        this.photoSyncState = photoSyncState;
        this.perfilVisible = perfilVisible;
    }
}
