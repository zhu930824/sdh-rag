package cn.sdh.backend.dto;

/** A fixed set of published document generations, not the mutable latest document rows. */
public record IndexPublicationSnapshot(String version, int generationCount, int legacyCount, boolean summariesComplete) {}
