package com.proyecto.moveon.data.profile.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.utils.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ProfilePhotoStorage {

    private static final String ROOT_DIR             = "profile_photos";
    private static final long   MAX_DOWNLOAD_BYTES   = 5L * 1024L * 1024L;
    private static final String CLOUDINARY_ROOT_DOMAIN = "cloudinary.com";

    /**
     * Cliente dedicado para descargas de foto remota.
     * BUG-06: followRedirects y followSslRedirects habilitados para soportar
     * redirects 301/302 habituales en CDNs como Cloudinary.
     */
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private ProfilePhotoStorage() {}

    @NonNull
    public static File getRootDir(@NonNull Context context) throws IOException {
        File root = new File(context.getFilesDir(), ROOT_DIR);
        Files.createDirectories(root.toPath());
        return root;
    }

    @NonNull
    public static File getAccountDir(@NonNull Context context, @NonNull String accountKey) throws IOException {
        File dir = new File(getRootDir(context), sanitize(accountKey));
        Files.createDirectories(dir.toPath());
        return dir;
    }

    @NonNull
    public static String savePendingPhoto(@NonNull Context context,
                                          @NonNull String accountKey,
                                          @NonNull File sourceFile) throws IOException {
        String ext = safeExtension(sourceFile.getName());
        File dst = new File(getAccountDir(context, accountKey), "avatar_pending" + ext);
        copyFile(sourceFile, dst);
        return dst.getAbsolutePath();
    }

    @NonNull
    public static String promotePendingToCurrent(@NonNull Context context,
                                                 @NonNull String accountKey,
                                                 @NonNull String pendingPath,
                                                 int version) throws IOException {
        File src = new File(pendingPath);
        if (!src.exists()) {
            throw new IOException("La foto pendiente no existe");
        }

        String ext        = safeExtension(src.getName());
        File accountDir   = getAccountDir(context, accountKey);
        File dst          = new File(accountDir, String.format(Locale.ROOT, "avatar_v%d%s", version, ext));
        moveFile(src, dst);
        deleteOtherCurrentFiles(accountDir, dst.getName());
        return dst.getAbsolutePath();
    }

    @NonNull
    public static String downloadRemotePhoto(@NonNull Context context,
                                             @NonNull String accountKey,
                                             @NonNull String remoteUrl,
                                             int version) throws IOException {
        HttpUrl backendBase    = HttpUrl.parse(BuildConfig.BASE_URL);
        HttpUrl parsedUrl      = validateRemoteUrl(remoteUrl, backendBase);

        Request.Builder requestBuilder = new Request.Builder()
                .url(parsedUrl)
                .get();

        // Si la imagen se sirve desde el mismo backend, mandamos el bearer.
        if (backendBase != null && isSameOrigin(parsedUrl, backendBase)) {
            String accessToken = SecureSessionManager.getInstance(context).getAccessToken();
            if (StringUtils.hasText(accessToken)) {
                requestBuilder.header("Authorization", "Bearer " + accessToken);
            }
        }

        try (Response response = HTTP.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("No se pudo descargar la foto remota: HTTP " + response.code());
            }

            // OkHttp garantiza body no nulo tras isSuccessful(); el try-with-resources lo cierra.
            try (ResponseBody body = response.body()) {
                long declaredLength = body.contentLength();
                if (declaredLength > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("La foto remota supera el tamaño máximo permitido");
                }

                MediaType mediaType   = body.contentType();
                String    contentType = mediaType != null ? mediaType.toString() : "";
                if (!contentType.startsWith("image/")) {
                    throw new IOException("El contenido remoto no es una imagen válida");
                }

                String ext        = extensionFromContentType(contentType, parsedUrl.encodedPath());
                File accountDir   = getAccountDir(context, accountKey);
                File dst          = new File(accountDir, String.format(Locale.ROOT, "avatar_v%d%s", version, ext));
                try (InputStream in  = body.byteStream();
                     OutputStream out = new FileOutputStream(dst, false)) {
                    copyWithLimit(in, out, MAX_DOWNLOAD_BYTES);
                }
                deleteOtherCurrentFiles(accountDir, dst.getName());
                return dst.getAbsolutePath();
            }
        }
    }

    public static boolean exists(@Nullable String path) {
        return path != null && new File(path).exists();
    }

    public static void deleteFileSilently(@Nullable String path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(new File(path).toPath());
        } catch (IOException ignored) {
        }
    }

    public static void deleteAll(@NonNull Context context) {
        try {
            deleteRecursively(getRootDir(context));
        } catch (IOException ignored) {
        }
    }

    private static void deleteRecursively(@Nullable File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException ignored) {
        }
    }

    private static void deleteOtherCurrentFiles(@NonNull File accountDir, @NonNull String keepName) {
        File[] files = accountDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("avatar_v") && !name.equals(keepName)) {
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void moveFile(@NonNull File src, @NonNull File dst) throws IOException {
        if (src.equals(dst)) return;
        if (!src.renameTo(dst)) {
            copyFile(src, dst);
            Files.deleteIfExists(src.toPath());
        }
    }

    private static void copyFile(@NonNull File src, @NonNull File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        try (InputStream in  = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst, false)) {
            copyWithLimit(in, out, Long.MAX_VALUE);
        }
    }

    private static void copyWithLimit(@NonNull InputStream in,
                                      @NonNull OutputStream out,
                                      long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total    = 0;
        int  read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("La descarga supera el tamaño máximo permitido");
            }
            out.write(buffer, 0, read);
        }
        out.flush();
    }

    @NonNull
    private static HttpUrl validateRemoteUrl(@NonNull String remoteUrl,
                                             @Nullable HttpUrl backendBase) throws IOException {
        HttpUrl url = HttpUrl.parse(remoteUrl);
        if (url == null) {
            throw new IOException("URL remota inválida");
        }

        String scheme = url.scheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IOException("Esquema de URL no permitido");
        }

        // En release solo HTTPS. En debug seguimos permitiendo HTTP para entorno local.
        if (!BuildConfig.DEBUG && !"https".equalsIgnoreCase(scheme)) {
            throw new IOException("En producción solo se permiten fotos remotas por HTTPS");
        }

        String host = url.host();
        if (!StringUtils.hasText(host)) {
            throw new IOException("URL remota sin host");
        }

        // Allowlist explícita:
        // 1) el mismo backend si en algún entorno sirve imágenes autenticadas
        // 2) Cloudinary, que es de donde viene la foto de perfil
        if (!isAllowedRemoteHost(url, backendBase)) {
            throw new IOException("Host remoto no permitido para fotos de perfil");
        }

        return url;
    }

    private static boolean isAllowedRemoteHost(@NonNull HttpUrl candidate,
                                               @Nullable HttpUrl backendBase) {
        if (backendBase != null && isSameOrigin(candidate, backendBase)) {
            return true;
        }

        String host = candidate.host().toLowerCase(Locale.ROOT);
        return host.equals(CLOUDINARY_ROOT_DOMAIN)
                || host.endsWith("." + CLOUDINARY_ROOT_DOMAIN);
    }

    private static boolean isSameOrigin(@NonNull HttpUrl a, @NonNull HttpUrl b) {
        return a.scheme().equalsIgnoreCase(b.scheme())
                && a.host().equalsIgnoreCase(b.host())
                && a.port() == b.port();
    }

    @NonNull
    private static String extensionFromContentType(@NonNull String contentType, @Nullable String urlPath) {
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.contains("jpeg") || lower.contains("jpg")) return ".jpg";
        if (lower.contains("png"))  return ".png";
        if (lower.contains("webp")) return ".webp";
        if (lower.contains("gif"))  return ".gif";
        return safeExtension(urlPath);
    }

    @NonNull
    private static String sanitize(@NonNull String accountKey) {
        return accountKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    @NonNull
    private static String safeExtension(@Nullable String value) {
        if (value == null) return ".jpg";
        String clean = value;
        int q = clean.indexOf('?');
        if (q >= 0) clean = clean.substring(0, q);
        int dot = clean.lastIndexOf('.');
        if (dot < 0 || dot == clean.length() - 1) return ".jpg";
        String ext = clean.substring(dot).toLowerCase(Locale.ROOT);
        if (ext.length() > 5) return ".jpg";
        return ext;
    }
}