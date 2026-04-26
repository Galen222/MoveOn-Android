package com.proyecto.moveon.testutil;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * Contexto mínimo en memoria para tests JVM que necesitan preferencias, recursos simples y permisos.
 *
 * <p>No sobrescribe {@code Context#getString(...)} porque en el SDK usado por el proyecto esos
 * métodos son finales. Los tests y los helpers testeables resuelven textos a través de
 * {@link #getResources()}, que devuelve un {@link Resources} mínimo creado sin ejecutar
 * constructores Android mockeados.</p>
 */
public final class MemoryContext extends ContextWrapper {

    private final Map<String, MemorySharedPreferences> preferences = new HashMap<>();
    private final Map<String, Object> services = new HashMap<>();
    private final Set<String> grantedPermissions = new HashSet<>();
    private final MemoryResources resources = MemoryResources.create();
    private final File filesDir;

    public MemoryContext() {
        this(createTempFilesDir());
    }

    public MemoryContext(@NonNull File filesDir) {
        super(null);
        this.filesDir = filesDir;
        //noinspection ResultOfMethodCallIgnored
        this.filesDir.mkdirs();
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }


    @Override
    public File getFilesDir() {
        return filesDir;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return preferences(name);
    }

    @Override
    public Resources getResources() {
        return resources;
    }

    @Override
    public String getPackageName() {
        return "com.proyecto.moveon.test";
    }

    @Override
    public Context createConfigurationContext(Configuration overrideConfiguration) {
        return this;
    }

    @Override
    public Object getSystemService(String name) {
        return services.get(name);
    }

    @Override
    public int checkPermission(String permission, int pid, int uid) {
        return grantedPermissions.contains(permission)
                ? PackageManager.PERMISSION_GRANTED
                : PackageManager.PERMISSION_DENIED;
    }

    private static File createTempFilesDir() {
        try {
            return Files.createTempDirectory("moveon-memory-context-").toFile();
        } catch (IOException ex) {
            throw new AssertionError("No se pudo crear directorio temporal para MemoryContext", ex);
        }
    }

    public MemorySharedPreferences preferences(String name) {
        MemorySharedPreferences prefs = preferences.get(name);
        if (prefs == null) {
            prefs = new MemorySharedPreferences();
            preferences.put(name, prefs);
        }
        return prefs;
    }

    public MemoryContext putSystemService(@NonNull String name, Object service) {
        services.put(name, service);
        return this;
    }

    public MemoryContext grantPermission(@NonNull String permission) {
        grantedPermissions.add(permission);
        return this;
    }

    public MemoryContext revokePermission(@NonNull String permission) {
        grantedPermissions.remove(permission);
        return this;
    }

    public MemoryContext putString(int resId, @NonNull String value) {
        resources.putString(resId, value);
        return this;
    }

    public MemoryContext putStringArray(int resId, @NonNull String... values) {
        resources.putStringArray(resId, values);
        return this;
    }

    /**
     * Recursos mínimos para resolver cadenas y arrays de forma estable sin Android real ni Robolectric.
     */
    private static final class MemoryResources extends Resources {

        private Map<Integer, String> strings;
        private Map<Integer, String[]> arrays;
        private Configuration configuration;

        @SuppressWarnings("deprecation")
        private MemoryResources() {
            super(null, null, null);
        }

        static MemoryResources create() {
            MemoryResources resources = allocateWithoutAndroidConstructor();
            resources.strings = new HashMap<>();
            resources.arrays = new HashMap<>();
            resources.configuration = new Configuration();
            resources.configuration.setLocale(Locale.ENGLISH);
            return resources;
        }

        private static MemoryResources allocateWithoutAndroidConstructor() {
            try {
                Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                Object unsafe = unsafeField.get(null);
                return (MemoryResources) unsafe.getClass()
                        .getMethod("allocateInstance", Class.class)
                        .invoke(unsafe, MemoryResources.class);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("No se pudo crear MemoryResources para tests JVM", ex);
            }
        }

        void putString(int id, @NonNull String value) {
            strings.put(id, value);
        }

        void putStringArray(int id, @NonNull String[] values) {
            arrays.put(id, values.clone());
        }

        @Override
        public Configuration getConfiguration() {
            return configuration;
        }

        @Override
        public String getString(int id) {
            String value = strings.get(id);
            return value != null ? value : "res-" + id;
        }

        @Override
        public String getString(int id, Object... formatArgs) {
            String template = getString(id);
            if (formatArgs == null || formatArgs.length == 0) {
                return template;
            }
            try {
                return String.format(template, formatArgs);
            } catch (RuntimeException ignored) {
                StringBuilder builder = new StringBuilder(template).append(':');
                for (int i = 0; i < formatArgs.length; i++) {
                    if (i > 0) {
                        builder.append(',');
                    }
                    builder.append(formatArgs[i]);
                }
                return builder.toString();
            }
        }

        @Override
        public String[] getStringArray(int id) {
            String[] values = arrays.get(id);
            return values != null ? values.clone() : new String[0];
        }
    }
}
