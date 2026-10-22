package com.whereismymoneygoing.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix="whereismymoneygoing")
public record AppProperties(@DefaultValue("local_data/merchants.local.yml") String localRulesPath) {}
