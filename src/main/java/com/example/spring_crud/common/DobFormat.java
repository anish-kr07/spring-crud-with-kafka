package com.example.spring_crud.common;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Meta-annotation: @DobFormat itself carries @JsonFormat. Anywhere we put
// @DobFormat on a field, Jackson sees the embedded @JsonFormat and uses its pattern.
//
// Why a custom annotation instead of repeating @JsonFormat(pattern="dd-MM-yyyy")?
//   - Single source of truth: change the pattern in one place.
//   - Self-documenting: "@DobFormat" reads like intent, "@JsonFormat(pattern=...)" reads like config.
//   - Pairs well with the global default: most dates use the app-wide yyyy-MM-dd
//     (set in application.properties), and DOB intentionally overrides to dd-MM-yyyy.
//
// Annotation meta-annotations:
//   @Retention(RUNTIME) — must be visible at runtime so Jackson can read it via reflection.
//   @Target — restrict where it can be placed; matches @JsonFormat's allowed targets.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@JsonFormat(pattern = "dd-MM-yyyy")
public @interface DobFormat {
}
