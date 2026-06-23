package org.tpi.questlytales.config;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri.users}")
    private String usersMongoUri;

    @Bean(name = "usersMongoClient")
    public MongoClient usersMongoClient() {
        return MongoClients.create(usersMongoUri);
    }

    @Bean(name = "usersMongoTemplate")
    public MongoTemplate usersMongoTemplate() {
        MongoClient client = usersMongoClient();
        if (client == null) throw new IllegalStateException("usersMongoClient is null");
        // Le nom de la base est lu directement depuis l'URI (source de vérité unique),
        // au lieu d'être codé en dur. Les deux templates pointent ainsi sur la même base.
        String database = new ConnectionString(usersMongoUri).getDatabase();
        if (database == null || database.isBlank()) database = "questlytales";
        return new MongoTemplate(client, database);
    }
}
