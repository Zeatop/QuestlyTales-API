package org.tpi.questlytales.services;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.tpi.questlytales.dtos.storydtos.StoryMetadataDTO;
import org.tpi.questlytales.dtos.storydtos.StorySubmissionDTO;
import org.tpi.questlytales.services.StoryMongoService.WriteOutcome;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoryMongoServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private StoryMongoService service;

    private static final String COLLECTION = "stories";

    private StorySubmissionDTO submission() {
        StoryMetadataDTO meta = new StoryMetadataDTO();
        meta.setTitle("Mon histoire");
        StorySubmissionDTO dto = new StorySubmissionDTO();
        dto.setMetadata(meta);
        dto.setNodes(List.of());
        return dto;
    }

    private Document existingStory(String ownerId) {
        return new Document("_id", new ObjectId())
            .append("ownerId", ownerId)
            .append("metadata", new Document("creationDate", 1000L));
    }

    // ===== saveStory =====

    @Test
    void saveStory_persistsOwnerId() {
        // insert assigne un _id comme le ferait Mongo
        doAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.put("_id", new ObjectId());
            return d;
        }).when(mongoTemplate).insert(any(Document.class), eq(COLLECTION));

        service.saveStory(submission(), "owner-1");

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).insert(captor.capture(), eq(COLLECTION));
        assertEquals("owner-1", captor.getValue().getString("ownerId"));
    }

    // ===== updateStory =====

    @Test
    void updateStory_notFound_returnsNotFound() {
        when(mongoTemplate.findById(any(), eq(Document.class), eq(COLLECTION))).thenReturn(null);

        WriteOutcome outcome = service.updateStory(new ObjectId().toString(), submission(), "owner-1");

        assertEquals(WriteOutcome.NOT_FOUND, outcome);
        verify(mongoTemplate, never()).findAndReplace(any(), any(), eq(COLLECTION));
    }

    @Test
    void updateStory_otherOwner_returnsForbidden() {
        when(mongoTemplate.findById(any(), eq(Document.class), eq(COLLECTION)))
            .thenReturn(existingStory("owner-1"));

        WriteOutcome outcome = service.updateStory(new ObjectId().toString(), submission(), "intruder");

        assertEquals(WriteOutcome.FORBIDDEN, outcome);
        verify(mongoTemplate, never()).findAndReplace(any(), any(), eq(COLLECTION));
    }

    @Test
    void updateStory_owner_succeedsAndPreservesCreationDate() {
        when(mongoTemplate.findById(any(), eq(Document.class), eq(COLLECTION)))
            .thenReturn(existingStory("owner-1"));

        WriteOutcome outcome = service.updateStory(new ObjectId().toString(), submission(), "owner-1");

        assertEquals(WriteOutcome.SUCCESS, outcome);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).findAndReplace(any(), captor.capture(), eq(COLLECTION));
        Document replacement = captor.getValue();
        assertEquals("owner-1", replacement.getString("ownerId"));
        Document meta = replacement.get("metadata", Document.class);
        assertEquals(1000L, meta.getLong("creationDate"));
    }

    // ===== deleteStory =====

    @Test
    void deleteStory_notFound_returnsNotFound() {
        when(mongoTemplate.findById(any(), eq(Document.class), eq(COLLECTION))).thenReturn(null);

        WriteOutcome outcome = service.deleteStory(new ObjectId().toString(), "owner-1");

        assertEquals(WriteOutcome.NOT_FOUND, outcome);
        verify(mongoTemplate, never()).remove(any(), eq(COLLECTION));
    }

    @Test
    void deleteStory_otherOwner_returnsForbidden() {
        when(mongoTemplate.findById(any(), eq(Document.class), eq(COLLECTION)))
            .thenReturn(existingStory("owner-1"));

        WriteOutcome outcome = service.deleteStory(new ObjectId().toString(), "intruder");

        assertEquals(WriteOutcome.FORBIDDEN, outcome);
        verify(mongoTemplate, never()).remove(any(), eq(COLLECTION));
    }

    @Test
    void deleteStory_owner_succeeds() {
        when(mongoTemplate.findById(any(), eq(Document.class), eq(COLLECTION)))
            .thenReturn(existingStory("owner-1"));

        WriteOutcome outcome = service.deleteStory(new ObjectId().toString(), "owner-1");

        assertEquals(WriteOutcome.SUCCESS, outcome);
        verify(mongoTemplate).remove(any(), eq(COLLECTION));
    }
}
