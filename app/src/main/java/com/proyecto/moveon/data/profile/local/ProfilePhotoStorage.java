package com.proyecto.moveon.data.profile.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class ProfilePhotoStorage {

    private static final String ROOT_DIR = "profile_photos";
    private static final OkHttpClient HTTP = new OkHttpClient();

    private ProfilePhotoStorage() {}

    @NonNull
    public static File getRootDir(@NonNull Context context) {
        File root = new File(context.getFilesDir(), ROOT_DIR);
        if (!root.exists()) {
            root.mkdirs();
        }
        return root;
    }

    @NonNull
    public static File getAccountDir(@NonNull Context context, @NonNull String accountKey) {
        File dir = new File(getRootDir(context), sanitize(accountKey));
        if (!dir.exists()) {
            dir.mkdirs();
        }
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

        String ext = safeExtension(src.getName());
        File accountDir = getAccountDir(context, accountKey);
        File dst = new File(accountDir, String.format(Locale.ROOT, "avatar_v%d%s", version, ext));
        moveFile(src, dst);
        deleteOtherCurrentFiles(accountDir, dst.getName());
        return dst.getAbsolutePath();
    }

    @NonNull
    public static String downloadRemotePhoto(@NonNull Context context,
                                             @NonNull String accountKey,
                                             @NonNull String remoteUrl,
                                             int version) throws IOException {
        Request request = new Request.Builder().url(remoteUrl).get().build();
        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("No se pudo descargar la foto remota");
            }

            String ext = safeExtension(remoteUrl);
            File accountDir = getAccountDir(context, accountKey);
            File dst = new File(accountDir, String.format(Locale.ROOT, "avatar_v%d%s", version, ext));
            try (InputStream in = response.body().byteStream();
                 OutputStream out = new FileOutputStream(dst, false)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            deleteOtherCurrentFiles(accountDir, dst.getName());
            return dst.getAbsolutePath();
        }
    }

    public static boolean exists(@Nullable String path) {
        return path != null && new File(path).exists();
    }

    public static void deleteFileSilently(@Nullable String path) {
        if (path == null) return;
        try {
            File file = new File(path);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception ignored) {
        }
    }

    public static void deleteAccountDir(@NonNull Context context, @Nullable String accountKey) {
        if (accountKey == null) return;
        deleteRecursively(new File(getRootDir(context), sanitize(accountKey)));
    }

    public static void deleteAll(@NonNull Context context) {
        deleteRecursively(getRootDir(context));
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
        file.delete();
    }

    private static void deleteOtherCurrentFiles(@NonNull File accountDir, @NonNull String keepName) {
        File[] files = accountDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("avatar_v") && !name.equals(keepName)) {
                file.delete();
            }
        }
    }

    private static void moveFile(@NonNull File src, @NonNull File dst) throws IOException {
        if (src.equals(dst)) return;
        if (!src.renameTo(dst)) {
            copyFile(src, dst);
            if (!src.delete()) {
                throw new IOException("No se pudo eliminar la foto temporal tras moverla");
            }
        }
    }

    private static void copyFile(@NonNull File src, @NonNull File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
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
