package com.proyecto.moveon.data.profile.local;

import static org.junit.Assert.*;

import okhttp3.HttpUrl;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Tests de utilidades puras y de E/S local de {@link ProfilePhotoStorage} sin depender de Android {@code Context}.
 */
public class ProfilePhotoStorageReflectionTest {

    /**
     * Verifica que las extensiones se normalizan y que los casos inseguros caen a jpg.
     */
    @Test
    public void safeExtension_normalizesAllowedExtensionsAndFallsBackToJpg() throws Exception {
        assertEquals(".png", invoke("safeExtension", new Class<?>[]{String.class}, "avatar.PNG?cache=1"));
        assertEquals(".webp", invoke("safeExtension", new Class<?>[]{String.class}, "folder/avatar.webp"));
        assertEquals(".jpg", invoke("safeExtension", new Class<?>[]{String.class}, "avatar"));
        assertEquals(".jpg", invoke("safeExtension", new Class<?>[]{String.class}, "avatar.toolong"));
        assertEquals(".jpg", invoke("safeExtension", new Class<?>[]{String.class}, new Object[]{null}));
    }

    /**
     * Verifica que la sanitización de cuenta mantiene caracteres seguros y sustituye el resto.
     */
    @Test
    public void sanitize_lowercasesAndReplacesUnsafeCharacters() throws Exception {
        assertEquals("user.name_42", invoke("sanitize", new Class<?>[]{String.class}, "User.Name#42"));
        assertEquals("a_b-c.1", invoke("sanitize", new Class<?>[]{String.class}, "A B-C.1"));
    }

    /**
     * Verifica que la extensión se deduce por MIME con fallback a la ruta de la URL.
     */
    @Test
    public void extensionFromContentType_prefersMimeAndFallsBackToUrlPath() throws Exception {
        assertEquals(".jpg", invoke("extensionFromContentType", new Class<?>[]{String.class, String.class}, "image/jpeg", "/x/avatar.png"));
        assertEquals(".png", invoke("extensionFromContentType", new Class<?>[]{String.class, String.class}, "image/png", "/x/avatar.jpg"));
        assertEquals(".webp", invoke("extensionFromContentType", new Class<?>[]{String.class, String.class}, "image/webp", "/x/avatar.jpg"));
        assertEquals(".gif", invoke("extensionFromContentType", new Class<?>[]{String.class, String.class}, "image/gif", "/x/avatar.jpg"));
        assertEquals(".png", invoke("extensionFromContentType", new Class<?>[]{String.class, String.class}, "application/octet-stream", "/x/avatar.png"));
    }

    /**
     * Verifica que la comparación de origen exige mismo esquema, host y puerto.
     */
    @Test
    public void isSameOrigin_requiresSchemeHostAndPortToMatch() throws Exception {
        HttpUrl base = HttpUrl.parse("https://api.example.com:443/v1");
        HttpUrl same = HttpUrl.parse("https://api.example.com/users/avatar.png");
        HttpUrl differentScheme = HttpUrl.parse("http://api.example.com/users/avatar.png");
        HttpUrl differentHost = HttpUrl.parse("https://cdn.example.com/users/avatar.png");

        assertEquals(Boolean.TRUE, invoke("isSameOrigin", new Class<?>[]{HttpUrl.class, HttpUrl.class}, same, base));
        assertEquals(Boolean.FALSE, invoke("isSameOrigin", new Class<?>[]{HttpUrl.class, HttpUrl.class}, differentScheme, base));
        assertEquals(Boolean.FALSE, invoke("isSameOrigin", new Class<?>[]{HttpUrl.class, HttpUrl.class}, differentHost, base));
    }

    /**
     * Verifica que solo se permiten hosts de mismo origen o Cloudinary.
     */
    @Test
    public void isAllowedRemoteHost_allowsBackendAndCloudinaryOnly() throws Exception {
        HttpUrl backend = HttpUrl.parse("https://api.moveon.test/");
        HttpUrl sameBackend = HttpUrl.parse("https://api.moveon.test/media/avatar.jpg");
        HttpUrl cloudinary = HttpUrl.parse("https://res.cloudinary.com/demo/image/upload/avatar.jpg");
        HttpUrl rootCloudinary = HttpUrl.parse("https://cloudinary.com/avatar.jpg");
        HttpUrl attacker = HttpUrl.parse("https://evil-cloudinary.com/avatar.jpg");

        assertEquals(Boolean.TRUE, invoke("isAllowedRemoteHost", new Class<?>[]{HttpUrl.class, HttpUrl.class}, sameBackend, backend));
        assertEquals(Boolean.TRUE, invoke("isAllowedRemoteHost", new Class<?>[]{HttpUrl.class, HttpUrl.class}, cloudinary, backend));
        assertEquals(Boolean.TRUE, invoke("isAllowedRemoteHost", new Class<?>[]{HttpUrl.class, HttpUrl.class}, rootCloudinary, null));
        assertEquals(Boolean.FALSE, invoke("isAllowedRemoteHost", new Class<?>[]{HttpUrl.class, HttpUrl.class}, attacker, backend));
    }

    /**
     * Verifica la validación de URL remota con casos permitidos e inválidos.
     */
    @Test
    public void validateRemoteUrl_acceptsAllowedUrlsAndRejectsUnsafeOnes() throws Exception {
        HttpUrl backend = HttpUrl.parse("https://api.moveon.test/");

        assertEquals("res.cloudinary.com",
                ((HttpUrl) invoke("validateRemoteUrl", new Class<?>[]{String.class, HttpUrl.class}, "https://res.cloudinary.com/demo/avatar.png", backend)).host());
        assertEquals("api.moveon.test",
                ((HttpUrl) invoke("validateRemoteUrl", new Class<?>[]{String.class, HttpUrl.class}, "https://api.moveon.test/avatar.png", backend)).host());

        assertThrows(IOException.class, () -> invoke("validateRemoteUrl", new Class<?>[]{String.class, HttpUrl.class}, "ftp://res.cloudinary.com/avatar.png", backend));
        assertThrows(IOException.class, () -> invoke("validateRemoteUrl", new Class<?>[]{String.class, HttpUrl.class}, "https://example.org/avatar.png", backend));
        assertThrows(IOException.class, () -> invoke("validateRemoteUrl", new Class<?>[]{String.class, HttpUrl.class}, "no es url", backend));
    }

    /**
     * Verifica que la copia con límite escribe el contenido completo cuando cabe.
     */
    @Test
    public void copyWithLimit_copiesContentWithinLimit() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        invoke("copyWithLimit", new Class<?>[]{java.io.InputStream.class, java.io.OutputStream.class, long.class}, input, output, 3L);

        assertEquals("abc", output.toString("UTF-8"));
    }

    /**
     * Verifica que la copia con límite aborta cuando se supera el máximo permitido.
     */
    @Test
    public void copyWithLimit_throwsWhenLimitIsExceeded() {
        ByteArrayInputStream input = new ByteArrayInputStream("abcd".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThrows(IOException.class,
                () -> invoke("copyWithLimit", new Class<?>[]{java.io.InputStream.class, java.io.OutputStream.class, long.class}, input, output, 3L));
    }

    /**
     * Verifica las utilidades públicas de existencia y borrado silencioso de archivos.
     */
    @Test
    public void existsAndDeleteFileSilently_trackFileLifecycle() throws Exception {
        File temp = File.createTempFile("moveon-photo", ".jpg");
        assertTrue(ProfilePhotoStorage.exists(temp.getAbsolutePath()));

        ProfilePhotoStorage.deleteFileSilently(temp.getAbsolutePath());

        assertFalse(ProfilePhotoStorage.exists(temp.getAbsolutePath()));
        ProfilePhotoStorage.deleteFileSilently(null);
    }

    /**
     * Verifica que el borrado recursivo privado elimina directorios con hijos anidados.
     */
    @Test
    public void deleteRecursively_removesNestedFilesAndDirectories() throws Exception {
        File root = Files.createTempDirectory("moveon-photo-root").toFile();
        File nested = new File(root, "a/b");
        assertTrue(nested.mkdirs());
        File file = new File(nested, "avatar.jpg");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(1);
        }

        invoke("deleteRecursively", new Class<?>[]{File.class}, root);

        assertFalse(root.exists());
    }

    private static Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = ProfilePhotoStorage.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }
}
