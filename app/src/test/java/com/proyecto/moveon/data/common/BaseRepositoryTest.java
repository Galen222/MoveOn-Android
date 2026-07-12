package com.proyecto.moveon.data.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.io.IOException;

import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
/**
 * Pruebas para validar el comportamiento de base.
 */
public class BaseRepositoryTest {

    @Test
    public void enqueueTracked_removesCallOnResponse() {
        InspectableRepository repo = new InspectableRepository();
        FakeCall<String> call = new FakeCall<>("ok");

        repo.enqueueForTest(call);
        assertEquals(1, repo.trackedCount());

        call.triggerResponse();
        assertEquals(0, repo.trackedCount());
    }

    @Test
    public void enqueueTracked_removesCallOnFailure() {
        InspectableRepository repo = new InspectableRepository();
        FakeCall<String> call = new FakeCall<>("ok");

        repo.enqueueForTest(call);
        assertEquals(1, repo.trackedCount());

        call.triggerFailure(new IOException("network down"));
        assertEquals(0, repo.trackedCount());
    }

    @Test
    public void cancelAll_cancelsTrackedCalls() {
        InspectableRepository repo = new InspectableRepository();
        FakeCall<String> first = new FakeCall<>("a");
        FakeCall<String> second = new FakeCall<>("b");

        repo.trackForTest(first);
        repo.trackForTest(second);
        assertEquals(2, repo.trackedCount());

        repo.cancelAll();

        assertTrue(first.isCanceled());
        assertTrue(second.isCanceled());
        assertEquals(0, repo.trackedCount());
    }

    private static final class InspectableRepository extends BaseRepository {
        void enqueueForTest(Call<String> call) {
            enqueueTracked(call, new Callback<>() {
                @Override public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {}
                @Override public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {}
            });
        }

        void trackForTest(Call<?> call) {
            trackCall(call);
        }

        int trackedCount() {
            return getTrackedCallCountForTest();
        }
    }

    private static final class FakeCall<T> implements Call<T> {
        private final T defaultBody;
        private boolean executed = false;
        private boolean canceled = false;
        private Callback<T> callback;

        FakeCall(T defaultBody) {
            this.defaultBody = defaultBody;
        }

        @NonNull
        @Override
        public Response<T> execute() {
            executed = true;
            return Response.success(defaultBody);
        }

        @Override
        public void enqueue(@NonNull Callback<T> callback) {
            executed = true;
            this.callback = callback;
        }

        void triggerResponse() {
            if (callback != null) {
                callback.onResponse(this, Response.success(defaultBody));
            }
        }

        void triggerFailure(Throwable throwable) {
            if (callback != null) {
                callback.onFailure(this, throwable);
            }
        }

        @Override public boolean isExecuted() { return executed; }
        @Override public void cancel() { canceled = true; }
        @Override public boolean isCanceled() { return canceled; }
        @NonNull
        @Override
        @SuppressWarnings("MethodDoesntCallSuperMethod")
        public Call<T> clone() { return new FakeCall<>(defaultBody); }
        @NonNull @Override public Request request() { return new Request.Builder().url("https://example.com/").build(); }
        @NonNull @Override public Timeout timeout() { return Timeout.NONE; }
    }
}
