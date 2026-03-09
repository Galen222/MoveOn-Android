package com.proyecto.moveon.domain.profile;

import androidx.annotation.Nullable;

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
