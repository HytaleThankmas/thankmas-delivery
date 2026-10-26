package acorn.thankmasdelivery.service;

import acorn.thankmasdelivery.persistence.DeliveryStore;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs disk work on the store's writer, then posts its result back to the world thread.
 * If that world has stopped, the caller receives a separate cleanup callback instead.
 */
final class DeliveryActions {
    private final DeliveryStore deliveries;

    DeliveryActions(DeliveryStore deliveries) {
        this.deliveries = deliveries;
    }

    <T> CompletableFuture<T> submit(Supplier<T> work) {
        try {
            return deliveries.submit(work);
        } catch (RejectedExecutionException stopped) {
            return CompletableFuture.failedFuture(stopped);
        }
    }

    <T> void onWorld(Supplier<T> work, Consumer<Runnable> dispatch,
        BiConsumer<T, Throwable> completed, Consumer<RejectedExecutionException> stopped) {
        submit(work).whenComplete((result, error) -> {
            try {
                dispatch.accept(() -> completed.accept(result, error));
            } catch (RejectedExecutionException rejected) {
                stopped.accept(rejected);
            }
        });
    }
}
