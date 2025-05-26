/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.stat.impl;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import eu.openanalytics.containerproxy.stat.StatCollectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.security.core.Authentication;

import javax.annotation.PostConstruct;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class CSVCollector extends AbstractDbCollector implements AutoCloseable {

    private final Path url;
    private final List<StatCollectorFactory.UsageStatsAttribute> usageStatsAttributes;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private FileWriter fileWriter;
    private SequenceWriter writer;
    private CsvSchema schema;
    private CsvMapper csvMapper;

    public CSVCollector(String url, List<StatCollectorFactory.UsageStatsAttribute> usageStatsAttributes) {
        this.url = Path.of(url);
        this.usageStatsAttributes = usageStatsAttributes;
    }

    @PostConstruct
    public void init() throws IOException {
        csvMapper = new CsvMapper();
        csvMapper.enable(CsvGenerator.Feature.ALWAYS_QUOTE_STRINGS);
        csvMapper.enable(CsvGenerator.Feature.ALWAYS_QUOTE_EMPTY_STRINGS);
        CsvSchema.Builder schemaBuilder = CsvSchema.builder();
        if (Files.exists(url) && Files.size(url) > 0) {
            CsvSchema csvSchema = csvMapper.typedSchemaFor(Map.class).withHeader();
            try {
                try (MappingIterator<Map<String, String>> it = csvMapper.readerFor(Map.class)
                    .with(csvSchema.withColumnSeparator(','))
                    .readValues(url.toFile())) {
                    for (String existingColumn : it.next().keySet()) {
                        schemaBuilder.addColumn(existingColumn);
                    }
                }
                fileWriter = new FileWriter(url.toFile(), true);
            } catch (Exception e) {
                String newUrl = url.toString().replace(".csv", "-" + UUID.randomUUID() + ".csv");
                logger.warn("Not re-using existing csv file for usage stats (not in expected format), writing to {}", newUrl, e);
                fileWriter = new FileWriter(newUrl);
                schemaBuilder.setUseHeader(true);
            }
        } else {
            fileWriter = new FileWriter(url.toFile());
            schemaBuilder.setUseHeader(true);
        }
        if (!schemaBuilder.hasColumn("event_time")) {
            schemaBuilder.addColumn("event_time");
        }
        if (!schemaBuilder.hasColumn("username")) {
            schemaBuilder.addColumn("username");
        }
        if (!schemaBuilder.hasColumn("type")) {
            schemaBuilder.addColumn("type");
        }
        if (!schemaBuilder.hasColumn("data")) {
            schemaBuilder.addColumn("data");
        }

        if (usageStatsAttributes != null) {
            for (StatCollectorFactory.UsageStatsAttribute attribute : usageStatsAttributes) {
                if (!schemaBuilder.hasColumn(attribute.getName())) {
                    schemaBuilder.addColumn(attribute.getName());
                }
            }
        }

        schema = schemaBuilder.build();
        writer = csvMapper.writer(schema).writeValues(fileWriter);
        schema = schemaBuilder.setUseHeader(false).build(); // don't write header when writer re-starts
    }

    @Override
    protected synchronized void writeToDb(ApplicationEvent event, long timestamp, String userId, String type, String data, Authentication authentication) throws Exception {
        Map<String, String> row = new HashMap<>();
        for (String column : schema.getColumnNames()) {
            row.put(column, "");
        }
        row.put("event_time", Long.toString(timestamp));
        row.put("username", Objects.requireNonNullElse(userId, ""));
        row.put("type", Objects.requireNonNullElse(type, ""));
        row.put("data", Objects.requireNonNullElse(data, ""));
        row.putAll(resolveAttributes(authentication, event, usageStatsAttributes));
        try {
            writer.write(row);
        } catch (Exception e) {
            logger.warn("Error while writing to CSV file, data: {}", row, e);
            writer = csvMapper.writer(schema).writeValues(fileWriter);
        }
    }

    @Override
    public void close() throws Exception {
        writer.close();
        fileWriter.close();
    }
}
