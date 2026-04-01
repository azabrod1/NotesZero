package com.notesapp.service.routing;

import java.util.List;

public record SectionDigestExtraction(
    List<SectionEntry> sections
) {
    public record SectionEntry(
        String sectionId,
        String digest,
        List<String> entityTags
    ) {
    }
}
