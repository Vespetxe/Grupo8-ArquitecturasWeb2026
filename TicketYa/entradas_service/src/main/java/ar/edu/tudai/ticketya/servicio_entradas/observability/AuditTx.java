package ar.edu.tudai.ticketya.servicio_entradas.observability;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** No outbox: stdout after commit is deliberately best effort, not durable delivery. */
public final class AuditTx {
    private AuditTx() {}
    public static void local(Audit.Event event){
        if(TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive()){
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){Audit.emit(event);}
                @Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)Audit.emit(event.rollback());else if(status!=STATUS_COMMITTED)Audit.emit(event.uncertain());}
            });
        }else Audit.emit(event.result(Audit.Outcome.PENDING,Audit.Error.NO_LOCAL_TRANSACTION));
    }
    /** Call only after a nontransactional orchestrator has observed committed local results. */
    public static void completed(Audit.Event event){
        if(TransactionSynchronizationManager.isActualTransactionActive())local(event);else Audit.emit(event);
    }
}
