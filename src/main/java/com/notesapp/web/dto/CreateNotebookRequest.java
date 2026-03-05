package com.notesapp.web.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class CreateNotebookRequest {

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 300)
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
