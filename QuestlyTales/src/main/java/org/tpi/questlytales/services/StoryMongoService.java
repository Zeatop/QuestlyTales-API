package org.tpi.questlytales.services;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.tpi.questlytales.dtos.ActionDTO;
import org.tpi.questlytales.dtos.AttributeDTO;
import org.tpi.questlytales.dtos.ChoiceDTO;
import org.tpi.questlytales.dtos.ConditionDTO;
import org.tpi.questlytales.dtos.PagedResponseDTO;
import org.tpi.questlytales.dtos.storydtos.EditorStoryResponseDTO;
import org.tpi.questlytales.dtos.storydtos.GameStoryResponseDTO;
import org.tpi.questlytales.dtos.storydtos.StoryMetadataDTO;
import org.tpi.questlytales.dtos.storydtos.StoryPreviewDTO;
import org.tpi.questlytales.dtos.storydtos.StorySubmissionDTO;
import org.tpi.questlytales.dtos.storynodedtos.EditorNodeResponseDTO;
import org.tpi.questlytales.dtos.storynodedtos.GameNodeResponseDTO;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class StoryMongoService {

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String COLLECTION_NAME = "stories";

    /** Issue d'une opération d'écriture soumise à un contrôle de propriété. */
    public enum WriteOutcome { SUCCESS, NOT_FOUND, FORBIDDEN }

    public String saveStory(StorySubmissionDTO dto, String ownerId) {
        long now = new Date().getTime();
        Document metaDoc = convertMetadataToDocument(dto.getMetadata());
        metaDoc.append("creationDate", now)
               .append("lastUpdateDate", now);

        Document storyDoc = new Document()
            .append("ownerId", ownerId)
            .append("metadata", metaDoc)
            .append("nodes", convertNodesToDocuments(dto.getNodes()))
            .append("attributes", convertAttributesToDocuments(dto.getAttributes()))
            .append("images", dto.getImages())
            .append("videos", dto.getVideos())
            .append("sounds", dto.getSounds());

        mongoTemplate.insert(storyDoc, COLLECTION_NAME);
        return storyDoc.getObjectId("_id").toString();
    }

    public WriteOutcome updateStory(String id, StorySubmissionDTO dto, String currentUserId) {
        Document existing = mongoTemplate.findById(new ObjectId(id), Document.class, COLLECTION_NAME);
        if (existing == null) return WriteOutcome.NOT_FOUND;

        String ownerId = existing.getString("ownerId");
        if (ownerId != null && !ownerId.equals(currentUserId)) return WriteOutcome.FORBIDDEN;

        Document metaDoc = convertMetadataToDocument(dto.getMetadata());
        // Préserve la date de création d'origine (findAndReplace remplace tout le document)
        Document existingMeta = existing.get("metadata", Document.class);
        Long creationDate = existingMeta != null ? existingMeta.getLong("creationDate") : null;
        metaDoc.append("creationDate", creationDate)
               .append("lastUpdateDate", new Date().getTime());

        Document storyDoc = new Document()
            .append("ownerId", ownerId)
            .append("metadata", metaDoc)
            .append("nodes", convertNodesToDocuments(dto.getNodes()))
            .append("attributes", convertAttributesToDocuments(dto.getAttributes()))
            .append("images", dto.getImages())
            .append("videos", dto.getVideos())
            .append("sounds", dto.getSounds());

        Query query = new Query(Criteria.where("_id").is(new ObjectId(id)));
        mongoTemplate.findAndReplace(query, storyDoc, COLLECTION_NAME);
        return WriteOutcome.SUCCESS;
    }

    public EditorStoryResponseDTO getStoryForEditor(String id) {
        Document story = mongoTemplate.findById(new ObjectId(id), Document.class, COLLECTION_NAME);
        if (story == null) return null;

        EditorStoryResponseDTO response = new EditorStoryResponseDTO();
        response.setId(story.getObjectId("_id").toString());
        response.setMetadata(documentToMetadataDTO(story.get("metadata", Document.class)));

        List<Document> nodeDocs = story.getList("nodes", Document.class);
        if (nodeDocs != null) {
            response.setNodes(nodeDocs.stream()
                .map(this::documentToEditorNodeDTO)
                .collect(Collectors.toList()));
        }

        List<Document> attrDocs = story.getList("attributes", Document.class);
        if (attrDocs != null) {
            response.setAttributes(attrDocs.stream()
                .map(this::documentToAttributeDTO)
                .collect(Collectors.toList()));
        }

        response.setImages(documentToImages(story.get("images")));
        response.setVideos(story.getList("videos", String.class));
        response.setSounds(story.getList("sounds", String.class));

        return response;
    }

    public GameStoryResponseDTO getStoryForGame(String id) {
        Document story = mongoTemplate.findById(new ObjectId(id), Document.class, COLLECTION_NAME);
        if (story == null) return null;

        GameStoryResponseDTO response = new GameStoryResponseDTO();
        response.setId(story.getObjectId("_id").toString());
        response.setMetadata(documentToMetadataDTO(story.get("metadata", Document.class)));

        // Graphe complet : tous les nœuds (sans métadonnées d'édition x/y/color)
        List<Document> nodeDocs = story.getList("nodes", Document.class);
        if (nodeDocs != null && !nodeDocs.isEmpty()) {
            List<GameNodeResponseDTO> nodes = nodeDocs.stream()
                .map(this::documentToGameNodeDTO)
                .collect(Collectors.toList());
            response.setNodes(nodes);
            response.setStartNodeId(nodes.get(0).getId());
        } else {
            response.setNodes(List.of());
        }

        List<Document> attrDocs = story.getList("attributes", Document.class);
        if (attrDocs != null) {
            response.setPlayerAttributes(attrDocs.stream()
                .map(this::documentToAttributeDTO)
                .collect(Collectors.toList()));
        }

        response.setImages(documentToImages(story.get("images")));
        response.setVideos(story.getList("videos", String.class));
        response.setSounds(story.getList("sounds", String.class));

        return response;
    }

    public WriteOutcome deleteStory(String id, String currentUserId) {
        Document existing = mongoTemplate.findById(new ObjectId(id), Document.class, COLLECTION_NAME);
        if (existing == null) return WriteOutcome.NOT_FOUND;

        String ownerId = existing.getString("ownerId");
        if (ownerId != null && !ownerId.equals(currentUserId)) return WriteOutcome.FORBIDDEN;

        Query query = new Query(Criteria.where("_id").is(new ObjectId(id)));
        mongoTemplate.remove(query, COLLECTION_NAME);
        return WriteOutcome.SUCCESS;
    }

    /**
     * Liste paginée des stories pour le catalogue.
     * Ne renvoie que l'id et les métadonnées (les nœuds ne sont pas chargés).
     *
     * @param page   index de page (0-based)
     * @param size   nombre d'éléments par page
     * @param genre  filtre optionnel sur metadata.genre (exact)
     * @param tag    filtre optionnel : story contenant ce tag
     * @param author filtre optionnel sur metadata.author (exact)
     * @param search filtre optionnel : recherche insensible à la casse sur le titre ou la description
     */
    public PagedResponseDTO<StoryPreviewDTO> listStories(
            int page, int size, String ownerId, String genre, String tag, String author, String search) {

        Query query = new Query();
        if (ownerId != null && !ownerId.isBlank()) {
            query.addCriteria(Criteria.where("ownerId").is(ownerId));
        }
        if (genre != null && !genre.isBlank()) {
            query.addCriteria(Criteria.where("metadata.genre").is(genre));
        }
        if (tag != null && !tag.isBlank()) {
            query.addCriteria(Criteria.where("metadata.tags").is(tag));
        }
        if (author != null && !author.isBlank()) {
            query.addCriteria(Criteria.where("metadata.author").is(author));
        }
        if (search != null && !search.isBlank()) {
            String regex = Pattern.quote(search);
            query.addCriteria(new Criteria().orOperator(
                Criteria.where("metadata.title").regex(regex, "i"),
                Criteria.where("metadata.description").regex(regex, "i")
            ));
        }

        long totalElements = mongoTemplate.count(query, COLLECTION_NAME);

        // Projection : ne charger que l'owner et les métadonnées (pas le graphe de nœuds)
        query.fields().include("ownerId").include("metadata");
        query.with(Sort.by(Sort.Direction.DESC, "metadata.lastUpdateDate"));
        query.skip((long) page * size).limit(size);

        List<Document> docs = mongoTemplate.find(query, Document.class, COLLECTION_NAME);
        List<StoryPreviewDTO> content = docs.stream()
            .map(doc -> new StoryPreviewDTO(
                doc.getObjectId("_id").toString(),
                doc.getString("ownerId"),
                documentToMetadataDTO(doc.get("metadata", Document.class))))
            .collect(Collectors.toList());

        return PagedResponseDTO.of(content, page, size, totalElements);
    }

    // ===== Conversion DTO → Document =====

    private Document convertMetadataToDocument(StoryMetadataDTO meta) {
        if (meta == null) return new Document();
        return new Document()
            .append("title", meta.getTitle())
            .append("author", meta.getAuthor())
            .append("version", meta.getVersion())
            .append("language", meta.getLanguage())
            .append("description", meta.getDescription())
            .append("tags", meta.getTags())
            .append("thumbnailImage", meta.getThumbnailImage())
            .append("genre", meta.getGenre())
            .append("size", meta.getSize())
            .append("numberOfNodes", meta.getNumberOfNodes());
    }

    private List<Document> convertAttributesToDocuments(List<AttributeDTO> attributes) {
        if (attributes == null) return List.of();
        return attributes.stream()
            .map(attr -> new Document()
                .append("label", attr.getLabel())
                .append("type", attr.getType())
                .append("value", attr.getValue()))
            .collect(Collectors.toList());
    }

    private List<Document> convertNodesToDocuments(List<EditorNodeResponseDTO> nodes) {
        if (nodes == null) return List.of();
        return nodes.stream()
            .map(node -> new Document()
                .append("id", node.getId())
                .append("actions", convertActionsToDocs(node.getActions()))
                .append("choices", convertChoicesToDocuments(node.getChoices()))
                .append("x", node.getX())
                .append("y", node.getY())
                .append("color", node.getColor()))
            .collect(Collectors.toList());
    }

    private List<Document> convertChoicesToDocuments(List<ChoiceDTO> choices) {
        if (choices == null) return List.of();
        return choices.stream()
            .map(choice -> new Document()
                .append("choiceText", choice.getChoiceText())
                .append("conditions", convertConditionsToDocs(choice.getConditions()))
                .append("actions", convertActionsToDocs(choice.getActions()))
                .append("nextNodeId", choice.getNextNodeId()))
            .collect(Collectors.toList());
    }

    private List<Document> convertActionsToDocs(List<ActionDTO> actions) {
        if (actions == null) return List.of();
        return actions.stream()
            .map(a -> new Document()
                .append("type", a.getType())
                .append("params", a.getParams() != null ? new Document(a.getParams()) : new Document()))
            .collect(Collectors.toList());
    }

    private List<Document> convertConditionsToDocs(List<ConditionDTO> conditions) {
        if (conditions == null) return List.of();
        return conditions.stream()
            .map(c -> new Document()
                .append("type", c.getType())
                .append("params", c.getParams() != null ? new Document(c.getParams()) : new Document()))
            .collect(Collectors.toList());
    }

    // ===== Conversion Document → DTO =====

    private StoryMetadataDTO documentToMetadataDTO(Document doc) {
        if (doc == null) return null;
        StoryMetadataDTO meta = new StoryMetadataDTO();
        meta.setTitle(doc.getString("title"));
        meta.setAuthor(doc.getString("author"));
        meta.setVersion(doc.getString("version"));
        meta.setLanguage(doc.getString("language"));
        meta.setCreationDate(doc.getLong("creationDate"));
        meta.setLastUpdateDate(doc.getLong("lastUpdateDate"));
        meta.setDescription(doc.getString("description"));
        meta.setTags(doc.getList("tags", String.class));
        meta.setThumbnailImage(doc.getString("thumbnailImage"));
        meta.setGenre(doc.getString("genre"));
        meta.setSize(doc.getInteger("size"));
        meta.setNumberOfNodes(doc.getInteger("numberOfNodes"));
        return meta;
    }

    /**
     * Lit la cle images stockee en base. Format actuel : map { nomImage: url }.
     * Tolere l'ancien format (tableau de noms) en le convertissant en map { nom: "" }.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> documentToImages(Object raw) {
        if (raw instanceof Document doc) {
            Map<String, String> images = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                images.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
            }
            return images;
        }
        if (raw instanceof List<?> list) {
            Map<String, String> images = new LinkedHashMap<>();
            for (Object name : list) {
                if (name != null) images.put(name.toString(), "");
            }
            return images;
        }
        return new LinkedHashMap<>();
    }

    private AttributeDTO documentToAttributeDTO(Document doc) {
        AttributeDTO attr = new AttributeDTO();
        attr.setLabel(doc.getString("label"));
        attr.setType(doc.getString("type"));
        attr.setValue(doc.getString("value"));
        return attr;
    }

    private EditorNodeResponseDTO documentToEditorNodeDTO(Document doc) {
        EditorNodeResponseDTO node = new EditorNodeResponseDTO();
        node.setId(doc.getInteger("id"));
        node.setX(doc.getInteger("x"));
        node.setY(doc.getInteger("y"));
        node.setColor(doc.getString("color"));

        List<Document> actionDocs = doc.getList("actions", Document.class);
        if (actionDocs != null) {
            node.setActions(actionDocs.stream()
                .map(this::documentToActionDTO)
                .collect(Collectors.toList()));
        }

        List<Document> choiceDocs = doc.getList("choices", Document.class);
        if (choiceDocs != null) {
            node.setChoices(choiceDocs.stream()
                .map(this::documentToChoiceDTO)
                .collect(Collectors.toList()));
        }

        return node;
    }

    private GameNodeResponseDTO documentToGameNodeDTO(Document doc) {
        GameNodeResponseDTO node = new GameNodeResponseDTO();
        node.setId(doc.getInteger("id"));

        List<Document> actionDocs = doc.getList("actions", Document.class);
        if (actionDocs != null) {
            node.setActions(actionDocs.stream()
                .map(this::documentToActionDTO)
                .collect(Collectors.toList()));
        }

        List<Document> choiceDocs = doc.getList("choices", Document.class);
        if (choiceDocs != null) {
            node.setChoices(choiceDocs.stream()
                .map(this::documentToChoiceDTO)
                .collect(Collectors.toList()));
        }

        return node;
    }

    private ActionDTO documentToActionDTO(Document doc) {
        ActionDTO action = new ActionDTO();
        action.setType(doc.getString("type"));
        Document params = doc.get("params", Document.class);
        if (params != null) {
            action.setParams(new HashMap<>(params));
        }
        return action;
    }

    private ConditionDTO documentToConditionDTO(Document doc) {
        ConditionDTO condition = new ConditionDTO();
        condition.setType(doc.getString("type"));
        Document params = doc.get("params", Document.class);
        if (params != null) {
            condition.setParams(new HashMap<>(params));
        }
        return condition;
    }

    private ChoiceDTO documentToChoiceDTO(Document doc) {
        ChoiceDTO choice = new ChoiceDTO();
        choice.setChoiceText(doc.getString("choiceText"));
        choice.setNextNodeId(doc.getInteger("nextNodeId"));

        List<Document> conditionDocs = doc.getList("conditions", Document.class);
        if (conditionDocs != null) {
            choice.setConditions(conditionDocs.stream()
                .map(this::documentToConditionDTO)
                .collect(Collectors.toList()));
        }

        List<Document> actionDocs = doc.getList("actions", Document.class);
        if (actionDocs != null) {
            choice.setActions(actionDocs.stream()
                .map(this::documentToActionDTO)
                .collect(Collectors.toList()));
        }

        return choice;
    }
}
