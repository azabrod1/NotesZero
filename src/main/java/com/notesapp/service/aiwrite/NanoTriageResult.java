package com.notesapp.service.aiwrite;

public record NanoTriageResult(TriageType type, String reply) {

    public enum TriageType {
        WRITE, QUESTION, CANCEL, CHITCHAT, OFFTOPIC
    }
}
