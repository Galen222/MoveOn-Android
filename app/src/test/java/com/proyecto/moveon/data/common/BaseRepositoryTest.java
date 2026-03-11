package com.proyecto.moveon.data.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;

import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BaseRepositoryTest {

    @Test
    public void enqueueTracked_removesCallOnResponse() {
        InspectableRepository repo = new InspectableRepository();
        FakeCall<String> call = new FakeCall<>("ok");

        repo.enqueueForTest(call);
        assertEquals(1, repo.trackedCount());

        call.triggerResponse("ok");
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
            enqueueTracked(call, new Callback<String>() {
                @Override public void onResponse(Call<String> call, Response<String> response) {}
                @Override public void onFailure(Call<String> call, Throwable t) {}
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

        @Override
        public Response<T> execute() {
            executed = true;
            return Response.success(defaultBody);
        }

        @Override
        public void enqueue(Callback<T> callback) {
            executed = true;
            this.callback = callback;
        }

        void triggerResponse(T body) {
            if (callback != null) {
                callback.onResponse(this, Response.success(body));
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
        @Override public Call<T> clone() { return new FakeCall<>(defaultBody); }
        @Override public Request request() { return new Request.Builder().url("https://example.com/").build(); }
        @Override public Timeout timeout() { return Timeout.NONE; }
    }
}
