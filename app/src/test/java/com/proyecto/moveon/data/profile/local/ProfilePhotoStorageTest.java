package com.proyecto.moveon.data.profile.local;

import static org.junit.Assert.*;

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.testutil.MemoryContext;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import okhttp3.HttpUrl;

/**
 * Tests JVM de la lógica pura de almacenamiento local de fotos de perfil.
 */
public class ProfilePhotoStorageTest {

    /**
     * Verifica que los directorios raíz y de cuenta se crean usando una clave saneada.
     */
    @Test
    public void getRootAndAccountDir_createDirectoriesWithSanitizedAccountKey() throws Exception {
        MemoryContext context = context();

        File root = ProfilePhotoStorage.getRootDir(context);
        File accountDir = ProfilePhotoStorage.getAccountDir(context, "User 42/@X");

        assertTrue(root.isDirectory());
        assertTrue(accountDir.isDirectory());
        assertEquals("profile_photos", root.getName());
        assertEquals("user_42__x", accountDir.getName());
        assertEquals(root.getAbsolutePath(), accountDir.getParentFile().getAbsolutePath());
    }

    /**
     * Verifica que savePendingPhoto copia bytes y normaliza la extensión del fichero destino.
     */
    @Test
    public void savePendingPhoto_copiesSourceUsingSafeLowercaseExtension() throws Exception {
        MemoryContext context = context();
        File source = writeFile(context.getFilesDir(), "selected.PNG", "avatar".getBytes(StandardCharsets.UTF_8));

        String pendingPath = ProfilePhotoStorage.savePendingPhoto(context, "uid_1", source);

        File pending = new File(pendingPath);
        assertTrue(pending.isFile());
        assertEquals("avatar_pending.png", pending.getName());
        assertEquals("avatar", new String(Files.readAllBytes(pending.toPath()), StandardCharsets.UTF_8));
    }

    /**
     * Verifica que promotePendingToCurrent mueve el pendiente, conserva la versión nueva y borra versiones anteriores.
     */
    @Test
    public void promotePendingToCurrent_movesPendingAndDeletesOlderCurrentAvatars() throws Exception {
        MemoryContext context = context();
        File accountDir = ProfilePhotoStorage.getAccountDir(context, "uid_2");
        File pending = writeFile(accountDir, "avatar_pending.webp", "new".getBytes(StandardCharsets.UTF_8));
        File oldOne = writeFile(accountDir, "avatar_v1.jpg", "old1".getBytes(StandardCharsets.UTF_8));
        File oldTwo = writeFile(accountDir, "avatar_v2.png", "old2".getBytes(StandardCharsets.UTF_8));
        File unrelated = writeFile(accountDir, "notes.txt", "keep".getBytes(StandardCharsets.UTF_8));

        String currentPath = ProfilePhotoStorage.promotePendingToCurrent(context, "uid_2", pending.getAbsolutePath(), 3);

        File current = new File(currentPath);
        assertEquals("avatar_v3.webp", current.getName());
        assertTrue(current.isFile());
        assertFalse(pending.exists());
        assertFalse(oldOne.exists());
        assertFalse(oldTwo.exists());
        assertTrue(unrelated.exists());
        assertEquals("new", new String(Files.readAllBytes(current.toPath()), StandardCharsets.UTF_8));
    }

    /**
     * Verifica que promotePendingToCurrent informa con IOException cuando la ruta pendiente no existe.
     */
    @Test
    public void promotePendingToCurrent_missingPendingFileThrowsIOException() throws Exception {
        MemoryContext context = context();

        try {
            ProfilePhotoStorage.promotePendingToCurrent(context, "uid_3", new File(context.getFilesDir(), "missing.jpg").getAbsolutePath(), 1);
            fail("Debe fallar si la foto pendiente no existe");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("pendiente"));
        }
    }

    /**
     * Verifica que exists y deleteFileSilently toleran nulos, rutas inexistentes y ficheros reales.
     */
    @Test
    public void existsAndDeleteFileSilently_handleNullMissingAndExistingPaths() throws Exception {
        MemoryContext context = context();
        File file = writeFile(context.getFilesDir(), "delete-me.jpg", "x".getBytes(StandardCharsets.UTF_8));

        assertFalse(ProfilePhotoStorage.exists(null));
        assertFalse(ProfilePhotoStorage.exists(new File(context.getFilesDir(), "missing.jpg").getAbsolutePath()));
        assertTrue(ProfilePhotoStorage.exists(file.getAbsolutePath()));

        ProfilePhotoStorage.deleteFileSilently(file.getAbsolutePath());
        ProfilePhotoStorage.deleteFileSilently(null);

        assertFalse(file.exists());
    }

    /**
     * Verifica que deleteAll elimina recursivamente el almacén de fotos sin tocar el directorio base del contexto.
     */
    @Test
    public void deleteAll_removesProfilePhotoTreeOnly() throws Exception {
        MemoryContext context = context();
        File accountDir = ProfilePhotoStorage.getAccountDir(context, "uid_4");
        writeFile(accountDir, "avatar_v1.jpg", "x".getBytes(StandardCharsets.UTF_8));

        ProfilePhotoStorage.deleteAll(context);

        assertFalse(new File(context.getFilesDir(), "profile_photos").exists());
        assertTrue(context.getFilesDir().exists());
    }

    /**
     * Verifica que extensionFromContentType prioriza tipos MIME de imagen conocidos y usa fallback seguro.
     */
    @Test
    public void extensionFromContentType_mapsKnownImagesAndFallsBackToSafePathExtension() throws Exception {
        assertEquals(".jpg", invokeString("extensionFromContentType", new Class<?>[]{String.class, String.class}, "image/jpeg", "/ignored.png"));
        assertEquals(".png", invokeString("extensionFromContentType", new Class<?>[]{String.class, String.class}, "image/png", "/ignored.jpg"));
        assertEquals(".webp", invokeString("extensionFromContentType", new Class<?>[]{String.class, String.class}, "image/webp", "/ignored.jpg"));
        assertEquals(".gif", invokeString("extensionFromContentType", new Class<?>[]{String.class, String.class}, "image/gif", "/ignored.jpg"));
        assertEquals(".png", invokeString("extensionFromContentType", new Class<?>[]{String.class, String.class}, "application/octet-stream", "/avatar.PNG?cache=1"));
    }

    /**
     * Verifica que safeExtension descarta entradas nulas, sin extensión, incompletas o demasiado largas.
     */
    @Test
    public void safeExtension_returnsJpegFallbackForUnsafeValues() throws Exception {
        assertEquals(".jpg", invokeString("safeExtension", new Class<?>[]{String.class}, new Object[]{null}));
        assertEquals(".jpg", invokeString("safeExtension", new Class<?>[]{String.class}, "avatar"));
        assertEquals(".jpg", invokeString("safeExtension", new Class<?>[]{String.class}, "avatar."));
        assertEquals(".jpg", invokeString("safeExtension", new Class<?>[]{String.class}, "avatar.verylong"));
        assertEquals(".jpeg", invokeString("safeExtension", new Class<?>[]{String.class}, "avatar.JPEG?version=2"));
    }

    /**
     * Verifica que validateRemoteUrl permite mismo origen/backend y Cloudinary, y rechaza esquemas u hosts no permitidos.
     */
    @Test
    public void validateRemoteUrl_acceptsAllowedOriginsAndRejectsUnsafeInputs() throws Exception {
        HttpUrl backendBase = HttpUrl.get(BuildConfig.BASE_URL);
        Method validate = method("validateRemoteUrl", String.class, HttpUrl.class);

        HttpUrl sameOrigin = (HttpUrl) validate.invoke(null, BuildConfig.BASE_URL + "static/avatar.jpg", backendBase);
        HttpUrl cloudinary = (HttpUrl) validate.invoke(null, "https://res.cloudinary.com/demo/image/upload/avatar.jpg", backendBase);

        assertEquals(backendBase.host(), sameOrigin.host());
        assertEquals("res.cloudinary.com", cloudinary.host());

        expectIOException(validate, "javascript:alert(1)", backendBase);
        expectIOException(validate, "https://example.com/avatar.jpg", backendBase);
    }

    /**
     * Verifica que isSameOrigin compara esquema, host y puerto de forma estricta.
     */
    @Test
    public void isSameOrigin_requiresSameSchemeHostAndPort() throws Exception {
        Method sameOrigin = method("isSameOrigin", HttpUrl.class, HttpUrl.class);
        HttpUrl base = HttpUrl.get("https://api.example.com:8443/root/");
        HttpUrl same = HttpUrl.get("https://api.example.com:8443/other");
        HttpUrl differentPort = HttpUrl.get("https://api.example.com:443/other");
        HttpUrl differentScheme = HttpUrl.get("http://api.example.com:8443/other");

        assertEquals(Boolean.TRUE, sameOrigin.invoke(null, same, base));
        assertEquals(Boolean.FALSE, sameOrigin.invoke(null, differentPort, base));
        assertEquals(Boolean.FALSE, sameOrigin.invoke(null, differentScheme, base));
    }

    /**
     * Verifica que copyWithLimit copia bajo el límite y aborta cuando el stream lo supera.
     */
    @Test
    public void copyWithLimit_copiesUntilLimitAndThrowsWhenExceeded() throws Exception {
        Method copyWithLimit = method("copyWithLimit", java.io.InputStream.class, java.io.OutputStream.class, long.class);
        ByteArrayOutputStream ok = new ByteArrayOutputStream();

        copyWithLimit.invoke(null, new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), ok, 3L);

        assertEquals("abc", ok.toString("UTF-8"));
        expectIOException(copyWithLimit, new ByteArrayInputStream("abcd".getBytes(StandardCharsets.UTF_8)), new ByteArrayOutputStream(), 3L);
    }

    private static MemoryContext context() throws IOException {
        return new MemoryContext(Files.createTempDirectory("profile-photo-storage-test-").toFile());
    }

    private static File writeFile(File dir, String name, byte[] content) throws IOException {
        File file = new File(dir, name);
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), content);
        return file;
    }

    private static String invokeString(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        return (String) method(name, parameterTypes).invoke(null, args);
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = ProfilePhotoStorage.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static void expectIOException(Method method, Object... args) throws Exception {
        try {
            method.invoke(null, args);
            fail("Se esperaba IOException");
        } catch (InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof IOException);
        }
    }
}
