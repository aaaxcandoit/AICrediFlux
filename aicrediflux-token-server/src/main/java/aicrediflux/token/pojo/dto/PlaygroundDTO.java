package aicrediflux.token.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Playground 请求 DTO  *
 * @author aicrediflux
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaygroundDTO {

    private String model;
    private String group;
}
