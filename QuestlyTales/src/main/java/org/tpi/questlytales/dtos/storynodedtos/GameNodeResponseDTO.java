package org.tpi.questlytales.dtos.storynodedtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.tpi.questlytales.dtos.ActionDTO;
import org.tpi.questlytales.dtos.ChoiceDTO;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameNodeResponseDTO {
    private Integer id;
    private List<ActionDTO> actions;
    private List<ChoiceDTO> choices;
}
