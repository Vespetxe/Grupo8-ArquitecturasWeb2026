package ar.edu.tudai.ticketya.servicio_entradas.observability;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.slf4j.MDC;
import static org.junit.jupiter.api.Assertions.*;

class AuditTransactionTest {
    private EmbeddedDatabase database;
    private TransactionTemplate tx;
    private JdbcTemplate jdbc;
    @BeforeEach void setup(){database=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();jdbc=new JdbcTemplate(database);jdbc.execute("CREATE TABLE audit_probe(id INT PRIMARY KEY)");tx=new TransactionTemplate(new DataSourceTransactionManager(database));}
    @AfterEach void close(){database.shutdown();MDC.clear();}
    private Audit.Event event(long id){return Audit.capture(Audit.Action.PURCHASE_CREATED,Audit.Resource.PURCHASE,id,Audit.operation(Audit.Resource.PURCHASE,id),Audit.Outcome.SUCCESS,null).by(Audit.system());}
    @Test void unknownCompletionCannotClaimCommitOrRollback(){
        var manager=org.springframework.transaction.support.TransactionSynchronizationManager.class;
        try(var logs=new AuditCapture()){
            org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            try{
                AuditTx.local(event(1));
                var callbacks=org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
                callbacks.forEach(callback->callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_UNKNOWN));
            }finally{org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
            assertEquals(1,logs.rows().size());assertEquals("PENDING",logs.rows().get(0).get("outcome"));assertEquals("TX_OUTCOME_UNKNOWN",logs.rows().get(0).get("errorCode"));assertFalse(logs.rows().get(0).containsKey("resourceId"));
        }
    }
    @Test void successIsEmittedOnlyAfterRealCommit(){
        try(var logs=new AuditCapture()){
            tx.executeWithoutResult(status->{jdbc.update("INSERT INTO audit_probe VALUES(1)");AuditTx.local(event(1));assertTrue(logs.rows().isEmpty());});
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_probe",Integer.class));
            assertEquals("SUCCESS",logs.rows().get(0).get("outcome"));
        }
    }
    @Test void rollbackNeverProducesSuccessOrClaimsAnUncommittedResource(){
        try(var logs=new AuditCapture()){
            assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{jdbc.update("INSERT INTO audit_probe VALUES(1)");AuditTx.local(event(1));throw new IllegalStateException("sentinel-42-db");}));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_probe",Integer.class));
            assertEquals(1,logs.rows().size());assertEquals("FAILED",logs.rows().get(0).get("outcome"));
            assertEquals("TX_ROLLBACK",logs.rows().get(0).get("errorCode"));assertFalse(logs.rows().get(0).containsKey("resourceId"));
        }
    }
    @Test void requiresNewCommitSurvivesOuterRollbackWithoutGlobalSuccess(){
        var inner=new TransactionTemplate(tx.getTransactionManager());inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try(var logs=new AuditCapture()){
            tx.executeWithoutResult(status->{AuditTx.local(event(1));inner.executeWithoutResult(nested->{jdbc.update("INSERT INTO audit_probe VALUES(2)");AuditTx.local(event(2));});status.setRollbackOnly();});
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_probe",Integer.class));
            assertEquals(1,logs.rows().stream().filter(r->"SUCCESS".equals(r.get("outcome"))).count());
            assertEquals("2",logs.rows().stream().filter(r->"SUCCESS".equals(r.get("outcome"))).findFirst().orElseThrow().get("resourceId"));
        }
    }
    @Test void snapshotSurvivesContextCleanupAndMissingTransactionsCannotClaimSuccess(){
        try(var logs=new AuditCapture()){
            MDC.put("traceId","1234567890abcdef1234567890abcdef");
            tx.executeWithoutResult(status->{AuditTx.local(event(1));MDC.clear();});
            assertEquals("1234567890abcdef1234567890abcdef",logs.rows().get(0).get("trace.id"));
            AuditTx.local(event(2));assertEquals("PENDING",logs.rows().get(1).get("outcome"));assertEquals("NO_LOCAL_TRANSACTION",logs.rows().get(1).get("errorCode"));
        }
    }
}
