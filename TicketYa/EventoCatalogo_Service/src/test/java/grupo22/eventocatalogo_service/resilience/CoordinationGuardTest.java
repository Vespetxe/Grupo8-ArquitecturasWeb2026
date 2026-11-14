package grupo22.eventocatalogo_service.resilience;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.ResourceAccessException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class CoordinationGuardTest {
 static class TestClock extends Clock{Instant now=Instant.now();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}}
 @ParameterizedTest @EnumSource(PilotReadGuard.Family.class) void isolatedCircuitsRejectWithoutDispatchAndRecover(PilotReadGuard.Family family){var env=new MockEnvironment().withProperty("ticketya.resilience.read.window","2").withProperty("ticketya.resilience.read.minimum","2");var clock=new TestClock();var guard=new PilotReadGuard(env,clock,family);for(int i=0;i<2;i++)assertThrows(ResourceAccessException.class,()->guard.execute(()->{throw new ResourceAccessException("controlled");}));assertEquals(CircuitBreaker.State.OPEN,guard.circuit().getState());assertFalse(assertThrows(PilotReadGuard.Unavailable.class,()->guard.execute(()->"never")).dispatched());for(var other:PilotReadGuard.Family.values())if(other!=family)assertEquals("real",new PilotReadGuard(env,clock,other).execute(()->"real"));clock.now=clock.now.plusSeconds(11);assertEquals("real",guard.execute(()->"real"));assertEquals(CircuitBreaker.State.HALF_OPEN,guard.circuit().getState());assertEquals("real",guard.execute(()->"real"));assertEquals(CircuitBreaker.State.CLOSED,guard.circuit().getState());}
 @ParameterizedTest @EnumSource(PilotReadGuard.Family.class) void saturatedBulkheadIsNotASentCommand(PilotReadGuard.Family family){var guard=new PilotReadGuard(new MockEnvironment().withProperty("ticketya.resilience.read.concurrent","1"),family);assertTrue(guard.bulkhead().tryAcquirePermission());try{var failure=assertThrows(PilotReadGuard.Unavailable.class,()->guard.execute(()->"never"));assertEquals("BULKHEAD_FULL",failure.category());assertFalse(failure.dispatched());assertEquals(0,guard.circuit().getMetrics().getNumberOfBufferedCalls());}finally{guard.bulkhead().onComplete();}assertEquals("real",guard.execute(()->"real"));}
}
