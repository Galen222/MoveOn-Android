package com.proyecto.moveon.data.activities;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.data.activities.dto.GuardarActividadRequestDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Tests de la rama de validación interna de {@link ActivityRepository},
 * accediendo por reflexión al método privado {@code validateRequest} y al
 * estado {@link ActivityRepository.SyncResult}.
 *
 * <p>Se ejecuta bajo {@link RobolectricTestRunner} porque las ramas de error
 * resuelven mensajes vía {@code Context#getString} con recursos reales del
 * módulo {@code app}.</p>
 *
 * <p>El repositorio se instancia saltándose el constructor real con
 * {@code Unsafe} para no tocar {@code AppDatabase}, {@code WorkManager} ni el
 * Keystore en JVM. Sólo se inyecta el {@code appContext}, que es lo único
 * que el método de validación necesita.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class ActivityRepositoryValidationTest {

    private ActivityRepository repository;

    /**
     * Construye un {@link ActivityRepository} con el constructor saltado y
     * sólo {@code appContext} inyectado, suficiente para ejercitar
     * {@code validateRequest}.
     */
    @Before
    public void setUp() throws Exception {
        repository = allocate(ActivityRepository.class);
        Context ctx = ApplicationProvider.getApplicationContext();
        setField(repository, "appContext", ctx);
    }

    /**
     * Verifica que un request totalmente válido devuelve {@code null} y se
     * considera aceptado por la validación.
     */
    @Test
    public void validateRequest_validRequest_returnsNull() throws Exception {
        ApiError error = invokeValidate(buildValidRequest());

        assertNull(error);
    }

    /**
     * Verifica que un tipo no incluido en el set permitido genera error de validación.
     */
    @Test
    public void validateRequest_invalidTipo_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().tipo("Nadar").build();

        ApiError error = invokeValidate(req);

        assertNotNull(error);
        assertEquals(ApiErrorType.VALIDATION, error.getType());
    }

    /**
     * Verifica que distancia 0 cae en la rama de distancia inválida.
     */
    @Test
    public void validateRequest_zeroDistance_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().distancia(0).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que distancia muy grande cae en la rama de distancia inválida.
     */
    @Test
    public void validateRequest_excessiveDistance_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().distancia(300_001).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que duración total 0 cae en la rama correspondiente.
     */
    @Test
    public void validateRequest_zeroTotalDuration_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().duracionTotal(0).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que duración total fuera de rango cae en la rama correspondiente.
     */
    @Test
    public void validateRequest_excessiveTotalDuration_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().duracionTotal(86_401).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que duración en movimiento mayor que la total cae en la rama de
     * movimiento inválido.
     */
    @Test
    public void validateRequest_movingDurationOverflow_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder()
                .duracionTotal(1000)
                .duracionMovimiento(1500)
                .duracionParado(0)
                .build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que duración parado negativa cae en la rama correspondiente.
     */
    @Test
    public void validateRequest_negativeStoppedDuration_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder()
                .duracionTotal(1000)
                .duracionMovimiento(900)
                .duracionParado(-100)
                .build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que la suma movimiento+parado distinta del total cae en la rama
     * de mismatch.
     */
    @Test
    public void validateRequest_durationMismatch_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder()
                .duracionTotal(1000)
                .duracionMovimiento(800)
                .duracionParado(100)
                .build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que pausa manual negativa cae en la rama correspondiente.
     */
    @Test
    public void validateRequest_negativeManualPause_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().duracionPausaManual(-1).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que calorías 0 caen en la rama correspondiente.
     */
    @Test
    public void validateRequest_zeroCalories_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().caloriasQuemadas(0).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que ritmo medio movimiento 0 cae en la rama correspondiente.
     */
    @Test
    public void validateRequest_zeroMovingPace_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().ritmoMedioMovimiento(0).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que ritmo medio total fuera de rango cae en la rama correspondiente.
     */
    @Test
    public void validateRequest_excessiveTotalPace_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().ritmoMedioTotal(3601).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que ritmo máximo negativo cae en la rama correspondiente.
     */
    @Test
    public void validateRequest_negativeMaxPace_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().ritmoMaximo(-1).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que velocidad media 0 cae en la rama correspondiente.
     */
    @Test
    public void validateRequest_zeroAverageSpeed_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().velocidadMediaKmhX100(0).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que velocidad máxima 0 cae en la rama correspondiente.
     */
    @Test
    public void validateRequest_zeroMaxSpeed_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().velocidadMaxKmhX100(0).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que velocidad máxima menor que la media cae en la rama
     * correspondiente del check combinado.
     */
    @Test
    public void validateRequest_maxSpeedBelowAverage_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder()
                .velocidadMediaKmhX100(800)
                .velocidadMaxKmhX100(500)
                .build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que un contador de auto-pausas negativo cae en la rama de
     * contadores inválidos.
     */
    @Test
    public void validateRequest_negativeAutoPauses_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().autoPausas(-1).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que un contador de pausas manuales fuera de rango cae en la rama
     * de contadores inválidos.
     */
    @Test
    public void validateRequest_excessiveManualPauses_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().pausasManuales(501).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que un contador de alertas de velocidad negativo cae en la rama
     * de contadores inválidos.
     */
    @Test
    public void validateRequest_negativeSpeedAlerts_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().alertasVelocidad(-1).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que una polilínea no nula y demasiado corta cae en la rama
     * correspondiente.
     */
    @Test
    public void validateRequest_polylineTooShort_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().rutaPolilinea("a").build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que una polilínea {@code null} se considera ausencia de ruta y
     * no falla la validación.
     */
    @Test
    public void validateRequest_nullPolyline_acceptsAsAbsentRoute() throws Exception {
        GuardarActividadRequestDto req = builder().rutaPolilinea(null).build();

        ApiError error = invokeValidate(req);

        assertNull(error);
    }

    /**
     * Verifica que una fecha futura más allá del margen cae en la rama de
     * fecha en el futuro.
     */
    @Test
    public void validateRequest_futureDate_returnsValidationError() throws Exception {
        String future = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        GuardarActividadRequestDto req = builder().fechaRuta(future).build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que una fecha en formato no ISO-8601 cae en la rama de formato
     * de fecha inválido.
     */
    @Test
    public void validateRequest_invalidDateFormat_returnsValidationError() throws Exception {
        GuardarActividadRequestDto req = builder().fechaRuta("ayer").build();
        assertValidationFailure(req);
    }

    /**
     * Verifica que las factorías de {@link ActivityRepository.SyncResult} producen
     * los flags coherentes para los tres estados publicados.
     */
    @Test
    public void syncResult_factories_exposeCoherentFlags() {
        ActivityRepository.SyncResult noop = ActivityRepository.SyncResult.successNoop();
        ActivityRepository.SyncResult completed = ActivityRepository.SyncResult.successCompleted();
        ActivityRepository.SyncResult retry = ActivityRepository.SyncResult.retry();

        assertFalse(noop.retry);
        assertFalse(noop.completedPendingWork);

        assertFalse(completed.retry);
        assertTrue(completed.completedPendingWork);

        assertTrue(retry.retry);
        assertFalse(retry.completedPendingWork);
    }

    /**
     * Verifica que el endpoint que expone el repositorio para sincronización en
     * background es estable, ya que es referenciado por workers externos.
     */
    @Test
    public void uniqueSyncWorkName_isStableContract() {
        assertEquals("sync_actividades", ActivityRepository.UNIQUE_SYNC_WORK_NAME);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Asercion utilitaria que verifica que el request entregado falla con
     * {@link ApiErrorType#VALIDATION}.
     *
     * @param req request a validar.
     */
    private void assertValidationFailure(GuardarActividadRequestDto req) throws Exception {
        ApiError error = invokeValidate(req);
        assertNotNull(error);
        assertEquals(ApiErrorType.VALIDATION, error.getType());
    }

    /**
     * Invoca por reflexión el método privado {@code validateRequest} del repositorio.
     *
     * @param request request a validar.
     * @return error producido por la validación o {@code null} si pasa todas las reglas.
     */
    private ApiError invokeValidate(GuardarActividadRequestDto request) throws Exception {
        Method m = ActivityRepository.class.getDeclaredMethod(
                "validateRequest", GuardarActividadRequestDto.class);
        m.setAccessible(true);
        return (ApiError) m.invoke(repository, request);
    }

    /**
     * Construye un request totalmente válido como base para variar campo por campo.
     *
     * @return request consistente que pasa todas las reglas de validación.
     */
    private static GuardarActividadRequestDto buildValidRequest() {
        return builder().build();
    }

    /**
     * Devuelve un nuevo builder con valores por defecto que pasan todas las
     * reglas de validación.
     *
     * @return builder de request válido.
     */
    private static Builder builder() {
        return new Builder();
    }

    /**
     * Builder ad-hoc para componer requests válidos por defecto y mutar
     * sólo el campo bajo prueba en cada caso.
     */
    private static final class Builder {
        String tipo = "Correr";
        int distancia = 5_000;
        int duracionTotal = 1_800;
        int duracionMovimiento = 1_700;
        int duracionParado = 100;
        int duracionPausaManual = 0;
        int caloriasQuemadas = 350;
        int ritmoMedioMovimiento = 360;
        int ritmoMedioTotal = 380;
        int ritmoMaximo = 320;
        int velocidadMediaKmhX100 = 1000;
        int velocidadMaxKmhX100 = 1500;
        int autoPausas = 1;
        int pausasManuales = 0;
        int alertasVelocidad = 0;
        String rutaPolilinea = "abcdef";
        String fechaRuta = OffsetDateTime.now(ZoneOffset.UTC)
                .minusMinutes(30)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Builder tipo(String v) { this.tipo = v; return this; }
        Builder distancia(int v) { this.distancia = v; return this; }
        Builder duracionTotal(int v) { this.duracionTotal = v; return this; }
        Builder duracionMovimiento(int v) { this.duracionMovimiento = v; return this; }
        Builder duracionParado(int v) { this.duracionParado = v; return this; }
        Builder duracionPausaManual(int v) { this.duracionPausaManual = v; return this; }
        Builder caloriasQuemadas(int v) { this.caloriasQuemadas = v; return this; }
        Builder ritmoMedioMovimiento(int v) { this.ritmoMedioMovimiento = v; return this; }
        Builder ritmoMedioTotal(int v) { this.ritmoMedioTotal = v; return this; }
        Builder ritmoMaximo(int v) { this.ritmoMaximo = v; return this; }
        Builder velocidadMediaKmhX100(int v) { this.velocidadMediaKmhX100 = v; return this; }
        Builder velocidadMaxKmhX100(int v) { this.velocidadMaxKmhX100 = v; return this; }
        Builder autoPausas(int v) { this.autoPausas = v; return this; }
        Builder pausasManuales(int v) { this.pausasManuales = v; return this; }
        Builder alertasVelocidad(int v) { this.alertasVelocidad = v; return this; }
        Builder rutaPolilinea(String v) { this.rutaPolilinea = v; return this; }
        Builder fechaRuta(String v) { this.fechaRuta = v; return this; }

        GuardarActividadRequestDto build() {
            return new GuardarActividadRequestDto(
                    tipo, distancia, duracionTotal, duracionMovimiento, duracionParado,
                    duracionPausaManual, caloriasQuemadas, ritmoMedioMovimiento,
                    ritmoMedioTotal, ritmoMaximo, velocidadMediaKmhX100,
                    velocidadMaxKmhX100, autoPausas, pausasManuales, alertasVelocidad,
                    rutaPolilinea, fechaRuta
            );
        }
    }

    /**
     * Inyecta un valor en un campo declarado de {@link ActivityRepository} accesibilizándolo previamente.
     *
     * @param target instancia objetivo.
     * @param name nombre del campo.
     * @param value valor a publicar.
     */
    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = ActivityRepository.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /**
     * Crea una instancia de la clase indicada saltándose su constructor real,
     * útil para clases Android que tocarían base de datos o WorkManager.
     *
     * @param type clase a instanciar.
     * @param <T> tipo devuelto.
     * @return instancia recién creada sin invocar al constructor.
     */
    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Method m = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (T) m.invoke(unsafe, type);
    }
}
