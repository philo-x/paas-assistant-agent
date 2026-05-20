package io.agentscope.examples.paasassistant.supervisor.stream;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class ToolNarrationTestUtils {
    @SuppressWarnings("unchecked")
    public static void initCatalog() {
        try (InputStream is = ToolNarrationTestUtils.class.getResourceAsStream("/tool-narrations.yml")) {
            if (is == null) {
                throw new IllegalStateException("Could not find tool-narrations.yml on the classpath");
            }
            Yaml yaml = new Yaml();
            Map<String, Object> obj = yaml.load(is);
            Map<String, Object> agent = (Map<String, Object>) obj.get("agent");
            Map<String, Object> narration = (Map<String, Object>) agent.get("narration");
            List<Map<String, Object>> groupsList = (List<Map<String, Object>>) narration.get("groups");
            
            ToolNarrationProperties properties = new ToolNarrationProperties();
            List<ToolNarrationProperties.Group> groups = new ArrayList<>();
            for (Map<String, Object> groupMap : groupsList) {
                ToolNarrationProperties.Group group = new ToolNarrationProperties.Group();
                group.setName((String) groupMap.get("name"));
                List<Map<String, Object>> itemsList = (List<Map<String, Object>>) groupMap.get("items");
                if (itemsList != null) {
                    List<ToolNarrationProperties.Item> items = new ArrayList<>();
                    for (Map<String, Object> itemMap : itemsList) {
                        ToolNarrationProperties.Item item = new ToolNarrationProperties.Item();
                        item.setTools((List<String>) itemMap.get("tools"));
                        item.setTitle((String) itemMap.get("title"));
                        item.setDelegation(Boolean.TRUE.equals(itemMap.get("delegation")));
                        item.setAppendToolNameToTitle(!Boolean.FALSE.equals(itemMap.get("append-tool-name-to-title")));
                        items.add(item);
                    }
                    group.setItems(items);
                }
                groups.add(group);
            }
            properties.setGroups(groups);
            
            ToolNarrationCatalog catalog = new ToolNarrationCatalog(properties);
            catalog.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
