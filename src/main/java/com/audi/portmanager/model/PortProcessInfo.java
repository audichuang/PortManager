package com.audi.portmanager.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Data class to hold process information related to a port.
 */
@Data
@RequiredArgsConstructor
public class PortProcessInfo {
    private final String pid;
    private final String command;
    private final String port;
}