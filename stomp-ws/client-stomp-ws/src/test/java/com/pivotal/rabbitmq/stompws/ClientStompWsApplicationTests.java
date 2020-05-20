package com.pivotal.rabbitmq.stompws;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ClientStompWsApplicationTests {


	@Test
	void contextLoads() throws InterruptedException, ExecutionException, TimeoutException {
		CompletableFuture<Boolean> future = new CompletableFuture<>();

		assertFalse(future.isDone());
		try {
			assertNull(future.get(0, TimeUnit.SECONDS));
			fail("Expected TimeoutException");
		}catch(TimeoutException e) {

		}

		future.complete(true);

		assertNotNull(future.get(0, TimeUnit.SECONDS));
		Runnable some = mock(Runnable.class);

		future.thenRun(some);
		verify(some).run();

	}

	@Test
	void threadpool() throws ExecutionException, InterruptedException {
		ExecutorService service = Executors.newSingleThreadExecutor();
		Future<?> a = service.submit(() -> {
			try {
				System.out.println("running a");
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
		System.out.println("scheduling b");
		Future<?> b = service.submit(() -> {
			try {
				System.out.println("running b");
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
		System.out.println("waiting a");
		a.get();
		System.out.println("waiting b");
		b.get();

	}
}
