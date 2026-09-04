package com.moveinsync.mobilitycopilot.conversation.application;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public final class ContextualQuestionService {

    public List<String> suggestedQuestions() {
        return List.of(
                "Where is this anomaly concentrated?",
                "Did every high-volume vendor deteriorate?",
                "What evidence supports the recommended action?");
    }
}
