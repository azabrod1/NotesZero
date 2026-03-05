package com.notesapp.web.dto;

import javax.validation.constraints.NotBlank;

public class ResolveClarificationRequest {

    @NotBlank
    private String selectedOption;

    public String getSelectedOption() {
        return selectedOption;
    }

    public void setSelectedOption(String selectedOption) {
        this.selectedOption = selectedOption;
    }
}
