package edu.courseassist.storage;

import edu.courseassist.contract.ApplicationContracts.ProviderConfiguration;
import java.sql.SQLException;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/** Non-secret profiles and the last explicit work preference; no running tasks are restored. */
public final class ApplicationRepository {
    public record Configuration(long revision,Optional<ProviderConfiguration> profiles) {}
    private final H2Store store;
    private final JsonMapper json=JsonMapper.builder().build();
    public ApplicationRepository(H2Store store){this.store=store;}
    public Configuration initialize(){return store.transaction(c->{
        try(var s=c.createStatement()){
            s.execute("CREATE TABLE IF NOT EXISTS ca_app(singleton INT PRIMARY KEY, schema_version INT NOT NULL, revision BIGINT NOT NULL, payload CLOB)");
            s.execute("CREATE TABLE IF NOT EXISTS ca_work_preference(singleton INT PRIMARY KEY, enabled BOOLEAN NOT NULL)");
            try(var r=s.executeQuery("SELECT schema_version,revision,payload FROM ca_app WHERE singleton=1")){
                if(r.next()){if(r.getInt(1)!=1)throw new SQLException("Unsupported application schema");
                    return new Configuration(r.getLong(2),Optional.ofNullable(r.getString(3)).map(v->json.readValue(v,ProviderConfiguration.class)));}
            }
            s.execute("INSERT INTO ca_app VALUES(1,1,0,NULL)");return new Configuration(0,Optional.empty());
        }
    }).join();}
    public Configuration save(long expected,ProviderConfiguration profiles){return store.transaction(c->{
        try(var s=c.prepareStatement("UPDATE ca_app SET revision=revision+1,payload=? WHERE singleton=1 AND schema_version=1 AND revision=?")){
            s.setString(1,json.writeValueAsString(profiles));s.setLong(2,expected);if(s.executeUpdate()!=1)throw new SQLException("Application config revision changed");
        }
        return new Configuration(expected+1,Optional.of(profiles));
    }).join();}
    public boolean enabled(){return store.transaction(c->{
        try(var s=c.createStatement();var r=s.executeQuery("SELECT enabled FROM ca_work_preference WHERE singleton=1")){return r.next()&&r.getBoolean(1);}
    }).join();}
    public void enabled(boolean enabled){store.transaction(c->{
        try(var s=c.prepareStatement("MERGE INTO ca_work_preference(singleton,enabled) KEY(singleton) VALUES(1,?)")){s.setBoolean(1,enabled);s.executeUpdate();}return null;
    }).join();}

}
