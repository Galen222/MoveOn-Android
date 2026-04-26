package com.proyecto.moveon.data.profile;

import static org.junit.Assert.*;

import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;

import org.junit.Test;

/**
 * Tests de los value objects de resultado expuestos por {@link PerfilRepository}.
 */
public class PerfilRepositoryResultTest {

    /**
     * Verifica que UpdateResult.synced representa una actualización confirmada sin error asociado.
     */
    @Test
    public void updateResultSynced_hasSyncedStatusAndNoError() {
        PerfilRepository.UpdateResult result = PerfilRepository.UpdateResult.synced();

        assertEquals(PerfilRepository.UpdateResult.STATUS_SYNCED, result.status);
        assertNull(result.error);
    }

    /**
     * Verifica que UpdateResult.queued representa trabajo pendiente sin convertirlo en fallo visible.
     */
    @Test
    public void updateResultQueued_hasQueuedStatusAndNoError() {
        PerfilRepository.UpdateResult result = PerfilRepository.UpdateResult.queued();

        assertEquals(PerfilRepository.UpdateResult.STATUS_QUEUED, result.status);
        assertNull(result.error);
    }

    /**
     * Verifica que UpdateResult.failed conserva exactamente el ApiError recibido por la capa de sync.
     */
    @Test
    public void updateResultFailed_preservesProvidedError() {
        ApiError error = ApiError.typed(ApiErrorType.VALIDATION, 422, "perfil inválido");

        PerfilRepository.UpdateResult result = PerfilRepository.UpdateResult.failed(error);

        assertEquals(PerfilRepository.UpdateResult.STATUS_FAILED, result.status);
        assertSame(error, result.error);
    }

    /**
     * Verifica que SyncResult.successNoop indica éxito sin trabajo pendiente completado.
     */
    @Test
    public void syncResultSuccessNoop_hasNoRetryAndNoCompletedWork() {
        PerfilRepository.SyncResult result = PerfilRepository.SyncResult.successNoop();

        assertFalse(result.shouldRetry);
        assertFalse(result.completedPendingWork);
    }

    /**
     * Verifica que SyncResult.successCompleted indica éxito con cola pendiente vaciada.
     */
    @Test
    public void syncResultSuccessCompleted_hasNoRetryAndCompletedWork() {
        PerfilRepository.SyncResult result = PerfilRepository.SyncResult.successCompleted();

        assertFalse(result.shouldRetry);
        assertTrue(result.completedPendingWork);
    }

    /**
     * Verifica que SyncResult.retry solicita reintento sin marcar el trabajo como completado.
     */
    @Test
    public void syncResultRetry_hasRetryAndNoCompletedWork() {
        PerfilRepository.SyncResult result = PerfilRepository.SyncResult.retry();

        assertTrue(result.shouldRetry);
        assertFalse(result.completedPendingWork);
    }
}
