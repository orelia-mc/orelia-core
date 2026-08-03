package rpg.world.dialogue.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.world.dialogue.model.DialogueChoice;
import rpg.world.dialogue.model.DialogueEntryPoint;
import rpg.world.dialogue.model.DialogueNode;
import rpg.world.dialogue.model.DialogueTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link DialogueTree}, rebuilt from {@code dialogues.yml}.
 */
public final class DialogueRepository {

    private Map<String, DialogueTree> trees = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, DialogueTree> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("dialogues");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection treeSection = section.getConfigurationSection(id);
                if (treeSection == null) {
                    continue;
                }
                loaded.put(id, parseTree(id, treeSection));
            }
        }
        this.trees = loaded;
    }

    private DialogueTree parseTree(String id, ConfigurationSection section) {
        List<DialogueEntryPoint> entryPoints = new ArrayList<>();
        ConfigurationSection entrySection = section.getConfigurationSection("entry-points");
        if (entrySection != null) {
            for (String entryId : entrySection.getKeys(false)) {
                ConfigurationSection entry = entrySection.getConfigurationSection(entryId);
                if (entry == null) {
                    continue;
                }
                entryPoints.add(new DialogueEntryPoint(entry.getString("required-flag"), entry.getString("start-node")));
            }
        }

        Map<String, DialogueNode> nodes = new LinkedHashMap<>();
        ConfigurationSection nodesSection = section.getConfigurationSection("nodes");
        if (nodesSection != null) {
            for (String nodeId : nodesSection.getKeys(false)) {
                ConfigurationSection nodeSection = nodesSection.getConfigurationSection(nodeId);
                if (nodeSection == null) {
                    continue;
                }
                nodes.put(nodeId, parseNode(nodeId, nodeSection));
            }
        }

        return new DialogueTree(id, entryPoints, nodes);
    }

    private DialogueNode parseNode(String id, ConfigurationSection section) {
        List<DialogueChoice> choices = new ArrayList<>();
        ConfigurationSection choicesSection = section.getConfigurationSection("choices");
        if (choicesSection != null) {
            for (String choiceId : choicesSection.getKeys(false)) {
                ConfigurationSection choice = choicesSection.getConfigurationSection(choiceId);
                if (choice == null) {
                    continue;
                }
                choices.add(new DialogueChoice(choice.getString("text", ""), choice.getString("next-node"), choice.getString("set-flag")));
            }
        }
        return new DialogueNode(id, section.getStringList("lines"), choices, section.getString("next-node"), section.getString("set-flag"));
    }

    public Optional<DialogueTree> findById(String id) {
        return Optional.ofNullable(trees.get(id));
    }
}
