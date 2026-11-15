package grupo22.eventocatalogo_service.recommendations;
import org.springframework.stereotype.Component;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.Document;
import com.mongodb.client.model.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
@Component
public class PreferenceStore {
 private final MongoTemplate mongo;
 public PreferenceStore(MongoTemplate mongo){this.mongo=mongo;}
 public record Preferences(List<String> favoriteTypes,List<Long> favoriteParticipantIds,String preferredCity,Instant updatedAt){}
 public static Preferences empty(){return new Preferences(List.of(),List.of(),null,null);}
 public Preferences get(String user){
  try{var d=mongo.getCollection("preferences").find(Filters.eq("_id",user)).first();
   if(d==null)return empty();
   return new Preferences(List.copyOf(d.getList("favoriteTypes",String.class)),d.getList("favoriteParticipantIds",Number.class).stream().map(Number::longValue).toList(),d.getString("preferredCity"),d.getDate("updatedAt").toInstant());
  }catch(com.mongodb.MongoException|org.springframework.dao.DataAccessException ex){throw unavailable();}
 }
 public Preferences put(String user,Preferences preferences){
  var values=new Document("favoriteTypes",preferences.favoriteTypes()).append("favoriteParticipantIds",preferences.favoriteParticipantIds()).append("preferredCity",preferences.preferredCity()).append("updatedAt",Date.from(preferences.updatedAt()));
  try{var result=mongo.getCollection("preferences").updateOne(Filters.eq("_id",user),new Document("$set",values),new UpdateOptions().upsert(true));if(!result.wasAcknowledged())throw unavailable();return preferences;
  }catch(com.mongodb.MongoException|org.springframework.dao.DataAccessException ex){throw unavailable();}
 }
 private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Preferences unavailable");}
}
