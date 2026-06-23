package org.tpi.questlytales.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConditionDTO {
    private String type;
    private Map<String, Object> params;
}
