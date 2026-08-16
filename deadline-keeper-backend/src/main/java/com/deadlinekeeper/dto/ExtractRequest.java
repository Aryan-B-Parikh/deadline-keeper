package com.deadlinekeeper.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ExtractRequest {
    private MultipartFile screenshot;
    private String pastedText;
}
