package org.tpi.questlytales.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceDTO {
    private String choiceText;
    private List<ConditionDTO> conditions;
    private List<ActionDTO> actions;
    private Integer nextNodeId;  // null = end node
}
