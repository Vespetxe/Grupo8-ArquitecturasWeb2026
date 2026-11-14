package ar.edu.unicen.grupo8.comprasservice.resilience;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.ResourceAccessException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class CriticalGuardTest {
    @ParameterizedTest @EnumSource(value=PilotReadGuard.Family.class,names={"RESERVATION_CONFIRM","TICKETS_ISSUE"})
    void commandHalfOpenUsesOnlyRealRequestsAndRecovers(PilotReadGuard.Family family){
        var clock=new PilotReadGuardTest.TestClock();var env=new MockEnvironment().withProperty("ticketya.resilience.read.window","2").withProperty("ticketya.resilience.read.minimum","2");var guard=new PilotReadGuard(env,clock,family);for(int i=0;i<2;i++)assertThrows(ResourceAccessException.class,()->guard.execute(()->{throw new ResourceAccessException("controlled");}));assertEquals(CircuitBreaker.State.OPEN,guard.circuit().getState());var effects=new AtomicInteger();assertFalse(assertThrows(PilotReadGuard.Unavailable.class,()->guard.execute(effects::incrementAndGet)).dispatched());assertEquals(0,effects.get());clock.advance();assertEquals(1,guard.execute(effects::incrementAndGet));assertEquals(CircuitBreaker.State.HALF_OPEN,guard.circuit().getState());assertEquals(2,guard.execute(effects::incrementAndGet));assertEquals(CircuitBreaker.State.CLOSED,guard.circuit().getState());assertEquals(2,effects.get());
    }
    @Test void familyStatesAndCapacityAreIndependent(){
        var env=new MockEnvironment().withProperty("ticketya.resilience.read.window","2").withProperty("ticketya.resilience.read.minimum","2").withProperty("ticketya.resilience.reservation-confirm.concurrent","1");var guards=new EnumMap<PilotReadGuard.Family,PilotReadGuard>(PilotReadGuard.Family.class);for(var family:PilotReadGuard.Family.values())guards.put(family,new PilotReadGuard(env,family));assertEquals(5,guards.values().stream().map(g->g.circuit().getName()).distinct().count());var confirmation=guards.get(PilotReadGuard.Family.RESERVATION_CONFIRM);assertEquals(1,confirmation.bulkhead().getBulkheadConfig().getMaxConcurrentCalls());assertEquals(8,guards.get(PilotReadGuard.Family.RESERVATION_READ).bulkhead().getBulkheadConfig().getMaxConcurrentCalls());for(int i=0;i<2;i++)assertThrows(ResourceAccessException.class,()->confirmation.execute(()->{throw new ResourceAccessException("controlled");}));for(var entry:guards.entrySet())if(entry.getKey()!=PilotReadGuard.Family.RESERVATION_CONFIRM){assertEquals("actual",entry.getValue().execute(()->"actual"));assertEquals(CircuitBreaker.State.CLOSED,entry.getValue().circuit().getState());}
    }
}
