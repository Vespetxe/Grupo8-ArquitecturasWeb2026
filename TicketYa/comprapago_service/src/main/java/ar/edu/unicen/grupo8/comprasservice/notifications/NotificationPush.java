package ar.edu.unicen.grupo8.comprasservice.notifications;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.*;
@Component
public class NotificationPush {
 private final Map<Long,Set<SseEmitter>> clients=new HashMap<>();
 private final ExecutorService delivery=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"notification-push");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 public synchronized SseEmitter open(Long owner,long timeout){
  if(clients.values().stream().mapToInt(Set::size).sum()>=128||clients.getOrDefault(owner,Set.of()).size()>=4)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many notification streams");
  var emitter=new SseEmitter(timeout);clients.computeIfAbsent(owner,k->new HashSet<>()).add(emitter);
  Runnable cleanup=()->remove(owner,emitter);emitter.onCompletion(cleanup);emitter.onTimeout(cleanup);emitter.onError(e->cleanup.run());
  try{emitter.send(SseEmitter.event().name("ready").data(Map.of("inbox","/api/v1/me/notifications")));}catch(Exception e){cleanup.run();emitter.complete();}return emitter;
 }
 private synchronized void remove(Long owner,SseEmitter emitter){var list=clients.get(owner);if(list!=null){list.remove(emitter);if(list.isEmpty())clients.remove(owner);}}
 @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
 public void committed(NotificationWriter.Created event){try{delivery.execute(()->send(event));}catch(RejectedExecutionException full){/* Inbox is durable; reconnect/poll after saturation. */}}
 private void send(NotificationWriter.Created event){List<SseEmitter> targets;synchronized(this){targets=List.copyOf(clients.getOrDefault(event.owner(),Set.of()));}for(var target:targets)try{target.send(SseEmitter.event().id(event.notification().id()).name("notification").data(event.notification()));}catch(Exception failure){remove(event.owner(),target);try{target.complete();}catch(Exception ignored){}}}
 @jakarta.annotation.PreDestroy void close(){delivery.shutdownNow();synchronized(this){clients.values().forEach(set->set.forEach(SseEmitter::complete));clients.clear();}}
}
