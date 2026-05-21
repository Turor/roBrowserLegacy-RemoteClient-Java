package turoran.robrowser.grfloader.loader;

import org.junit.jupiter.api.Test;
import turoran.robrowser.grfloader.loader.BufferPool;

import java.nio.ByteBuffer;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class BufferPoolTest {

    @Test
    public void testAcquireAndRelease() {
        BufferPool pool = new BufferPool();
        
        // Acquire a small buffer (should be 1024 pool)
        ByteBuffer b1 = pool.acquire(500);
        assertEquals(1024, b1.capacity());
        assertEquals(500, b1.limit());
        
        List<BufferPool.PoolStats> stats = pool.stats();
        BufferPool.PoolStats s1024 = stats.stream().filter(s -> s.size == 1024).findFirst().orElseThrow();
        assertEquals(1, s1024.total);
        assertEquals(1, s1024.inUse);
        
        // Release buffer
        pool.release(b1);
        stats = pool.stats();
        s1024 = stats.stream().filter(s -> s.size == 1024).findFirst().orElseThrow();
        assertEquals(1, s1024.total);
        assertEquals(0, s1024.inUse);
        
        // Re-acquire (should get the same one)
        ByteBuffer b2 = pool.acquire(800);
        assertSame(b1, b2);
        assertEquals(800, b2.limit());
        
        // Large buffer (not pooled)
        ByteBuffer bLarge = pool.acquire(500000);
        assertEquals(500000, bLarge.capacity());
        stats = pool.stats();
        assertTrue(stats.stream().allMatch(s -> s.total == 0 || (s.size == 1024 && s.total == 1)));
    }

    @Test
    public void testMaxPoolSize() {
        BufferPool pool = new BufferPool();
        for (int i = 0; i < 15; i++) {
            pool.acquire(100);
        }
        
        List<BufferPool.PoolStats> stats = pool.stats();
        BufferPool.PoolStats s1024 = stats.stream().filter(s -> s.size == 1024).findFirst().orElseThrow();
        assertEquals(10, s1024.total);
        assertEquals(10, s1024.inUse);
    }
}
