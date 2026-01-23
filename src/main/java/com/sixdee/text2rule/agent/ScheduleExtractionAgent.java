package com.sixdee.text2rule.agent;

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

/**
 * ScheduleExtractionAgent extracts schedule details from Schedule nodes.
 * Dummy implementation as requested.
 */
public class ScheduleExtractionAgent {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleExtractionAgent.class);

    private final ChatLanguageModel lang4jService;
    private CompiledGraph<ScheduleState> compiledGraph;

    public static class ScheduleState extends AgentState {
        public ScheduleState(Map<String, Object> initData) {
            super(new HashMap<>(initData));
        }

        @SuppressWarnings("unchecked")
        public RuleTree<NodeData> getTree() {
            return (RuleTree<NodeData>) this.data().get("tree");
        }

        public String getTraceId() {
            return (String) this.data().get("traceId");
        }
    }

    public ScheduleExtractionAgent(ChatLanguageModel lang4jService) {
        this.lang4jService = lang4jService;
        compile();
    }

    private void compile() {
        try {
            StateGraph<ScheduleState> graph = new StateGraph<>(ScheduleState::new);
            graph.addNode("extract_schedule", this::extractNode);
            graph.addEdge(START, "extract_schedule");
            graph.addEdge("extract_schedule", END);
            this.compiledGraph = graph.compile();
        } catch (Exception e) {
            logger.error("Failed to compile ScheduleExtractionAgent", e);
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Map<String, Object>> extractNode(ScheduleState state) {
        logger.info("ScheduleExtractionAgent: Starting extraction...");
        RuleTree<NodeData> tree = state.getTree();
        if (tree == null) {
            return CompletableFuture.completedFuture(Map.of("failed", true));
        }

        processNode(tree.getRoot(), state.getTraceId());
        return CompletableFuture.completedFuture(Map.of("tree", tree));
    }

    private void processNode(RuleNode<NodeData> node, String traceId) {
        if (node == null)
            return;

        // Check if this is a Schedule node
        if ("Schedule".equalsIgnoreCase(node.getData().getType())) {
            String scheduleText = node.getData().getInput();
            logger.info("Found Schedule node: {}", scheduleText);

            String jsonResponse = null;
            boolean success = false;
            try {
                // Get prompt from registry
                String promptTemplate = com.sixdee.text2rule.config.PromptRegistry.getInstance()
                        .get("schedule_parser_prompt");
                String prompt = promptTemplate.replace("{{ $json.output.schedule }}", scheduleText);

                logger.info("ScheduleExtractionAgent: Sending prompt to LLM...");
                // Rate limit protection: 12-second delay
                // Rate limit
                com.sixdee.text2rule.util.RateLimiter.getInstance().apply();
                jsonResponse = lang4jService.generate(prompt);
                logger.info("ScheduleExtractionAgent: Received response from LLM.");

                // Clean JSON response
                jsonResponse = cleanJson(jsonResponse);

                // Parse the response
                com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.sixdee.text2rule.dto.ScheduleParserResult scheduleResult = objectMapper.readValue(jsonResponse,
                        com.sixdee.text2rule.dto.ScheduleParserResult.class);

                // Convert parsed schedule to readable format and add as child node
                String scheduleDetails = formatScheduleDetails(scheduleResult);
                NodeData extractedData = new NodeData("ScheduleDetails", "", "", node.getData().getModelName(), "",
                        scheduleDetails);

                

                node.addChild(new RuleNode<>(extractedData));
                logger.info("Added extracted schedule details to tree.");

                success = true;

                // Observability: Capture Event (Success)
                java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
                java.util.Map<String, String> userMessage = new java.util.HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", prompt);
                messages.add(userMessage);

                com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                        traceId,
                        "ScheduleExtractionAgent",
                        messages,
                        jsonResponse,
                        "Schedule Extracted",
                        "agent-model",
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap());

            } catch (Exception e) {
                logger.error("Failed to parse schedule with LLM", e);
                jsonResponse = "Error: " + e.getMessage();

                // Observability: Capture Event (Error)
                java.util.List<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
                java.util.Map<String, String> userMessage = new java.util.HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", scheduleText); // Fallback
                messages.add(userMessage);

                com.sixdee.text2rule.observability.IntegrationFactory.getInstance().recordEvent(
                        traceId,
                        "ScheduleExtractionAgent",
                        messages,
                        jsonResponse,
                        "Schedule Extraction Failed",
                        "agent-model",
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap());

                // Rethrow exception to stop flow
                throw new RuntimeException("Schedule Extraction Failed", e);
            }
        }

        // Recursively process children
        if (node.getChildren() != null) {
            for (RuleNode<NodeData> child : node.getChildren()) {
                processNode(child, traceId);
            }
        }
    }

    private String cleanJson(String response) {
        if (response.contains("```json")) {
            response = response.replace("```json", "").replace("```", "");
        } else if (response.contains("```")) {
            response = response.replace("```", "");
        }
        return response.trim();
    }

    private String formatScheduleDetails(com.sixdee.text2rule.dto.ScheduleParserResult result) {
        if (result == null || result.getScheduleType() == null || result.getScheduleType().isEmpty()) {
            return "No schedule information available";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Schedule Type: ").append(result.getScheduleType());

        if (result.getRepeat() != null && !result.getRepeat().isEmpty()) {
            sb.append(", Repeat: ").append(result.getRepeat());
        }

        if (result.getDay() != null && !result.getDay().isEmpty()) {
            sb.append(", Day(s): ").append(result.getDay());
        }

        if (result.getStartTime() != null && !result.getStartTime().isEmpty()) {
            sb.append(", Start Time: ").append(result.getStartTime());
        }

        if (result.getInterval() != null && result.getInterval().equals("Yes")) {
            sb.append(", Interval: ").append(result.getFrequency());
            if (result.getEndTime() != null && !result.getEndTime().isEmpty()) {
                sb.append(", End Time: ").append(result.getEndTime());
            }
        }

        if (result.getSegmentRuleStartDate() != null) {
            sb.append(", Start Date: ").append(result.getSegmentRuleStartDate());
        }

        if (result.getSegmentRuleEndDate() != null) {
            sb.append(", End Date: ").append(result.getSegmentRuleEndDate());
        }

        return sb.toString();
    }

    public CompletableFuture<ScheduleState> execute(RuleTree<NodeData> tree) {
        return execute(tree, null);
    }

    public CompletableFuture<ScheduleState> execute(RuleTree<NodeData> tree, String traceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return compiledGraph.invoke(Map.of("tree", tree, "traceId",
                        traceId != null ? traceId : java.util.UUID.randomUUID().toString())).orElse(null);
            } catch (Exception e) {
                logger.error("Error executing ScheduleExtractionAgent", e);
                throw new RuntimeException(e);
            }
        });
    }
}
