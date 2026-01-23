error id: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/agent/UnifiedRuleAgent.java
file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/agent/UnifiedRuleAgent.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[142,20]

error in qdox parser
file content:
```java
offset: 5403
uri: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/agent/UnifiedRuleAgent.java
text:
```scala
package com.sixdee.text2rule.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixdee.text2rule.config.PromptRegistry;
import com.sixdee.text2rule.config.SupabaseService;
import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleNode;
import com.sixdee.text2rule.model.RuleTree;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

public class UnifiedRuleAgent {
    private static final Logger logger = LoggerFactory.getLogger(UnifiedRuleAgent.class);
    private static final String KPI_PROMPT_KEY = "unified_kpi_matching_prompt";
    private static final String IF_PROMPT_KEY = "unified_if_condition_prompt";

    private final ChatLanguageModel lang4jService;
    private final SupabaseService supabaseService;
    private final ObjectMapper objectMapper;
    private CompiledGraph<UnifiedState> compiledGraph;

    public static class UnifiedState extends AgentState {
        public UnifiedState(Map<String, Object> initData) {
            super(new HashMap<>(initData));
        }

        @SuppressWarnings("unchecked")
        public RuleTree<NodeData> getTree() {
            return (RuleTree<NodeData>) this.data().get("tree");
        }

        public boolean isFailed() {
            return (boolean) this.data().getOrDefault("failed", false);
        }

        public String getCustomPrompt() {
            return (String) this.data().get("customPrompt");
        }
    }

    public UnifiedRuleAgent(ChatLanguageModel lang4jService) {
        this.lang4jService = lang4jService;
        this.supabaseService = new SupabaseService();
        this.objectMapper = new ObjectMapper();
        compile();
    }

    private void compile() {
        try {
            StateGraph<UnifiedState> graph = new StateGraph<>(UnifiedState::new);
            graph.addNode("unified_process", this::unifiedProcessNode);
            graph.addEdge(START, "unified_process");
            graph.addEdge("unified_process", END);
            this.compiledGraph = graph.compile();
        } catch (Exception e) {
            logger.error("Failed to compile UnifiedRuleAgent", e);
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Map<String, Object>> unifiedProcessNode(UnifiedState state) {
        logger.info("UnifiedRuleAgent: Processing node...");
        RuleTree<NodeData> tree = state.getTree();
        if (tree == null) {
            return CompletableFuture.completedFuture(Map.of("failed", true));
        }

        String customPrompt = state.getCustomPrompt();

        try {
            processTree(tree, customPrompt);
        } catch (Exception e) {
            logger.error("Error in Unified Rule Agent", e);
            return CompletableFuture.completedFuture(Map.of("failed", true));
        }
        return CompletableFuture.completedFuture(Map.of("tree", tree));
    }

    private void processTree(RuleTree<NodeData> tree, String customPrompt) {
        if (tree == null || tree.getRoot() == null)
            return;
        traverseAndProcess(tree.getRoot(), customPrompt);
    }

    private void traverseAndProcess(RuleNode<NodeData> node, String customPrompt) {
        if (node == null)
            return;

        // Find "segments" node (output of RuleConverterAgent)
        if ("segments".equalsIgnoreCase(node.getData().getType())) {
            processSegmentNode(node, customPrompt);
        }

        // Recursively process children
        // Use a copy to avoid ConcurrentModificationException if tree is modified
        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : new java.util.ArrayList<>(node.getChildren())) {
                traverseAndProcess(child, customPrompt);
            }
        }
    }

    private void processSegmentNode(RuleNode<NodeData> node, String customPrompt) {
        String segmentsRaw = node.getData().getInput(); // Newline separated string
        logger.info("Processing segments: {}", segmentsRaw);

        // Fetch context
        String context = supabaseService.fetchDocument();

        // Step 1: KPI Matching
        List<String> matchedKpis = executeKpiMatching(segmentsRaw, context);

        // Step 2: IF Condition Generation
        String ifCondition = executeIfGeneration(node.getData().getInput(), context, matchedKpis, customPrompt); // Note:
                                                                                                                 // Prompt
                                                                                                                 // asks

        // Update Tree: Add IF Node and restructure
        updateTree(node, ifCondition, matchedKpis);
    }
    // for
    // CONDITION_JSON,
    // using raw text for
    // now or split

    // Update Tree: Add IF Node and restructure
    updateTree(node,@@ ifCondition, matchedKpis);
    }

    private List<String> executeKpiMatching(String segments, String context) {
        try {
            String promptTemplate = PromptRegistry.getInstance().get(KPI_PROMPT_KEY);
            String prompt = promptTemplate.replace("{{ $json.segments }}", segments)
                    .replace("{{ $json.context }}", context);

            // Rate limit protection: 12-second delay
            try {
                Thread.sleep(12000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String response = lang4jService.generate(prompt);
            response = cleanJson(response);

            return objectMapper.readValue(response, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            logger.error("KPI Matching failed", e);
            return Collections.emptyList();
        }
    }

    private String executeIfGeneration(String originalText, String context, List<String> matchedKpis,
            String customPrompt) {
        try {
            // Prompt inputs: CONDITION_JSON, CONTEXT_STR, ORIGINAL_STATEMENT
            // The prompt says "Read CONDITION_JSON".
            // We pass the list of extracted segments as JSON array to CONDITION_JSON
            // But wait, segmentsRaw is a string. RuleConverter outputs a single string for
            // "segments" node input?
            // RuleConverter output: "segments": [ "...", "..." ] -> Joined by "\n" in
            // addChildrenToNode.
            // So node.getData().getInput() is a newline separated string.
            // I should convert it back to a JSON list or just pass it as is if the prompt
            // handles it?
            // The prompt {{ $json.conditions }} expects a list. I'll split by newline.

            String[] conditionsArray = originalText.split("\n");
            String conditionsJson = objectMapper.writeValueAsString(conditionsArray);

            String promptTemplate;
            if (customPrompt != null && !customPrompt.trim().isEmpty()) {
                promptTemplate = customPrompt;
            } else {
                promptTemplate = PromptRegistry.getInstance().get(IF_PROMPT_KEY);
            }

            String prompt = promptTemplate.replace("{{ $json.conditions }}", conditionsJson)
                    .replace("{{ $json.context }}", context)
                    .replace("{{ $json.input_text }}", originalText);

            // Rate limit protection: 12-second delay
            try {
                Thread.sleep(12000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String response = lang4jService.generate(prompt);
            // Clean markdown if present, though prompt says "Return ONLY one line"
            return cleanJson(response).replace("```", "").trim();
        } catch (Exception e) {
            logger.error("IF Generation failed", e);
            return "if (error)";
        }
    }

    private void updateTree(RuleNode<NodeData> segmentNode, String ifCondition, List<String> matchedKpis) {
        // Create IF Node
        NodeData ifNodeData = new NodeData("IF_Condition", "", "", segmentNode.getData().getModelName(), "",
                ifCondition);
        RuleNode<NodeData> ifNode = new RuleNode<>(ifNodeData);

        // Add Matched KPIs as metadata or separate node?
        // User didn't specify, but helpful for debugging.
        // Let's add them as a "KPIs" node under IF node? Or just logs?
        // Let's stick to IF node as the main logic.

        // Restructure: Move Actions from Segment node (siblings?) No, Actions were
        // children of Segment?
        // Wait, RuleConverter adds "segments" and "Action" as children of ROOT (or
        // parent).
        // Let's check RuleConverterAgent again.
        // RuleConverterAgent.java:168: parent.addChild(new RuleNode<>(segments));
        // RuleConverterAgent.java:174: parent.addChild(new RuleNode<>(Action));
        // So they are SIBLINGS. `segmentNode` is the "segments" node.
        // The "Action" node is a sibling of `segmentNode`.
        // I need to find the Action node and move it under the IF node.

        RuleNode<NodeData> parent = segmentNode.getParent();
        if (parent != null) {
            RuleNode<NodeData> actionNode = null;
            // Find Action sibling
            for (RuleNode<NodeData> child : parent.getChildren()) {
                if ("Action".equalsIgnoreCase(child.getData().getType())) {
                    actionNode = child;
                    break;
                }
            }

            // Modify structure:
            // Parent -> Segments Node -> IF Node
            // Parent -> Action Node (Unchanged)

            // Attach IF Node to Segment Node
            segmentNode.addChild(ifNode);

            // Do NOT remove Segment Node. It stays.
            // Action Node stays (Sibling of Segments)
            // No changes needed for actionNode.
        }
    }

    private String cleanJson(String response) {
        // Basic cleanup
        if (response.contains("```json")) {
            response = response.replace("```json", "").replace("```", "");
        } else if (response.contains("```")) {
            response = response.replace("```", "");
        }
        return response.trim();
    }

    public CompletableFuture<UnifiedState> execute(RuleTree<NodeData> tree) {
        return execute(tree, null);
    }

    public CompletableFuture<UnifiedState> execute(RuleTree<NodeData> tree, String customPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("tree", tree);
                if (customPrompt != null) {
                    input.put("customPrompt", customPrompt);
                }
                return compiledGraph.invoke(input).orElse(null);
            } catch (Exception e) {
                logger.error("Error executing UnifiedRuleAgent", e);
                throw new RuntimeException(e);
            }
        });
    }
}

```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:546)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:677)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:674)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:674)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:912)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:691)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:500)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	java.base/java.lang.Thread.run(Thread.java:840)
```
#### Short summary: 

QDox parse error in file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/agent/UnifiedRuleAgent.java