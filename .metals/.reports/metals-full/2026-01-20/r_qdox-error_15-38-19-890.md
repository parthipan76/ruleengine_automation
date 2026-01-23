error id: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/agent/RuleConverterAgent.java
file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/agent/RuleConverterAgent.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[249,1]

error in qdox parser
file content:
```java
offset: 10784
uri: file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/agent/RuleConverterAgent.java
text:
```scala
package com.sixdee.text2rule.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixdee.text2rule.config.PromptRegistry;
import com.sixdee.text2rule.dto.RuleConverterResult;
import com.sixdee.text2rule.model.NodeData;
import com.sixdee.text2rule.model.RuleNode;
import com.sixdee.text2rule.model.RuleTree;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

public class RuleConverterAgent {
    private static final Logger logger = LoggerFactory.getLogger(RuleConverterAgent.class);
    private static final String DEFAULT_PROMPT_KEY = "rule_converter_prompt";

    private final ChatLanguageModel lang4jService;
    private final ObjectMapper objectMapper;
    private CompiledGraph<ConverterState> compiledGraph;

    public static class ConverterState extends AgentState {
        public ConverterState(Map<String, Object> initData) {
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

    public RuleConverterAgent(ChatLanguageModel lang4jService) {
        this.lang4jService = lang4jService;
        this.objectMapper = new ObjectMapper();
        compile();
    }

    private void compile() {
        try {
            StateGraph<ConverterState> graph = new StateGraph<>(ConverterState::new);
            graph.addNode("convert", this::convertNode);
            graph.addEdge(START, "convert");
            graph.addEdge("convert", END);
            this.compiledGraph = graph.compile();
        } catch (Exception e) {
            logger.error("Failed to compile RuleConverterAgent", e);
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Map<String, Object>> convertNode(ConverterState state) {
        logger.info("RuleConverterAgent: Executing Convert Node...");
        RuleTree<NodeData> tree = state.getTree();
        if (tree == null) {
            return CompletableFuture.completedFuture(Map.of("failed", true));
        }

        try {
            convertRules(tree);
        } catch (Exception e) {
            logger.error("Error in rule conversion node", e);
            return CompletableFuture.completedFuture(Map.of("failed", true));
        }
        return CompletableFuture.completedFuture(Map.of("tree", tree));
    }

    private void convertRules(RuleTree<NodeData> tree) {
        if (tree == null || tree.getRoot() == null)
            return;
        processNode(tree.getRoot());
    }

    private void processNode(RuleNode<NodeData> node) {
        if (node == null)
            return;

        // Check if this is a "Segment" node which holds the Rule text
        if ("Segment".equalsIgnoreCase(node.getData().getType())) {
            // Check if we already processed it (e.g. if it has children, maybe skip? or
            // clear?)
            // For now, assuming we process "Segment" nodes.
            node.getChildren().clear(); // Clear existing children if any

            String ruleText = node.getData().getInput();
            logger.info("Converting rule for Segment node: {}", ruleText);

            try {
            try {
                String promptTemplate;
                String customPrompt = ((ConverterState)compiledGraph.getState(new HashMap<>())).getCustomPrompt(); // This is hard to get from here without passing state down.
                // Better to pass state or customPrompt to processNode.
                // But processNode is recursive.
                // Only "Segment" uses prompt.
                // We should pass customPrompt to convertRules -> processNode.
                // But wait, compileGraph uses convertNode(State).
                // convertNode has state.
                // convertNode calls convertRules(tree). 
                // We need to change convertRules signature.
                // And processNode signature.
                
                // Let's assume processNode takes customPrompt.
                // Refactoring processNode(node) -> processNode(node, customPrompt).
                
                // Re-writing processNode to take customPrompt... but I am in a replacement chunk for processNode body? 
                // No, I am targeting Line 103 inside processNode.
                // I need to change method signature too.
                
                // Breaking this into Steps. 
                // 1. Update execute to pass prompt.
                // 2. Update convertNode to pass prompt.
                // 3. Update convertRules/processNode.
                
                // I will cancel this tool call and do it properly with multi_replace covering methods.
             } catch (Exception e) {
             
             // Abort.

                String prompt = promptTemplate.replace("{{ $json['output.normal_statements'] }}", ruleText);
                logger.info("RuleConverterAgent: Sending prompt to LLM...");
                // Rate limit protection: 12-second delay
                try {
                    Thread.sleep(12000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                String jsonResponse = lang4jService.generate(prompt);
                logger.info("RuleConverterAgent: Received response from LLM");

                // Clean JSON
                int startIndex = jsonResponse.indexOf("{");
                int endIndex = jsonResponse.lastIndexOf("}");
                if (startIndex != -1 && endIndex != -1) {
                    jsonResponse = jsonResponse.substring(startIndex, endIndex + 1);
                } else if (jsonResponse.contains("```json")) {
                    jsonResponse = jsonResponse.replaceAll("```json", "").replaceAll("```", "").trim();
                }

                RuleConverterResult result = objectMapper.readValue(jsonResponse, RuleConverterResult.class);

                if (result != null) {
                    addChildrenToNode(node, result);
                } else {
                    logger.warn("Rule conversion returned null result");
                }

            } catch (JsonProcessingException e) {
                logger.error("Failed to parse rule conversion response", e);
            } catch (Exception e) {
                logger.error("Error during rule conversion", e);
            }
        }

        // Recursively process children
        // Note: we just added children. Should we process them? No, we decended from
        // Segment.
        // But if the tree structure was different, we might need to.
        // Since we are iterating the tree, we should be careful about concurrent
        // modification if we use a simple iterator.
        // But here we are recursing. `processNode` calls `processNode` on children.
        // We added children to `node`.
        // DO NOT recurse into the just-added children to avoid infinite loops or
        // reprocessing if they happened to be named "Segment" (unlikely).
        // So we should iterate over original children?
        // Wait, "Segment" nodes were leaf nodes (created by ConditionExtractionAgent).
        // So they shouldn't have children before we start, or if they do (Schedule?),
        // we cleared them.

        // However, we need to traverse down to find the "Segment" nodes.
        // If the node is NOT a Segment, we recurse.
        if (!"Segment".equalsIgnoreCase(node.getData().getType()) && node.getChildren() != null) {
            // Create a copy of the list to iterate if needed, but standard loop is fine if
            // we don't modify the *current* node's children list while iterating it.
            // We are modifying `node`'s children only if `node` is "Segment".
            // If `node` is NOT "Segment", we iterate its children.
            for (RuleNode<NodeData> child : node.getChildren()) {
                processNode(child);
            }
        }
    }

    private void addChildrenToNode(RuleNode<NodeData> parent, RuleConverterResult result) {
        String modelName = parent.getData().getModelName();

        // 1. Add Segments (Conditions)
        // 1. Add Segments (Conditions) as a single node
        if (result.getSegments() != null && !result.getSegments().isEmpty()) {
            String joinedSegments = String.join("\n", result.getSegments());
            NodeData n = new NodeData("segments", "", "", modelName, "", joinedSegments);
            parent.addChild(new RuleNode<>(n));
        }

        // 2. Add Actions
        if (result.getActions() != null && !result.getActions().isEmpty()) {
            NodeData n = new NodeData("Action", "", "", modelName, "", result.getActions());
            parent.addChild(new RuleNode<>(n));
        }

        // 3. Add Policy
        if (result.getPolicy() != null && !result.getPolicy().isEmpty()) {
            NodeData n = new NodeData("Policy", "", "", modelName, "", result.getPolicy());
            parent.addChild(new RuleNode<>(n));
        }

        // 4. Add Schedule
        if (result.getSchedule() != null && !result.getSchedule().isEmpty()) {
            NodeData n = new NodeData("Schedule", "", "", modelName, "", result.getSchedule());
            parent.addChild(new RuleNode<>(n));
        }

        // 5. Add Sampling
        if (result.getSampling() != null && !result.getSampling().isEmpty()) {
            NodeData n = new NodeData("Sampling", "", "", modelName, "", result.getSampling());
            parent.addChild(new RuleNode<>(n));
        }
    }

    public CompletableFuture<ConverterState> execute(RuleTree<NodeData> tree) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("tree", tree);
                return compiledGraph.invoke(input).orElse(null);
            } catch (Exception e) {
                logger.error("Error executing RuleConverterAgent", e);
                throw new RuntimeException(e);
            }
        });
    }
}
@@
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

QDox parse error in file://<WORKSPACE>/src/main/java/com/sixdee/text2rule/agent/RuleConverterAgent.java