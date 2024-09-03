/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.batch;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.getOrderedMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Getter;

/**
 * ML batch ingestion data: index, field mapping and input and out files.
 */
public class MLBatchIngestionInput implements ToXContentObject, Writeable {

    public static final String INDEX_NAME_FIELD = "index_name";
    public static final String TEXT_EMBEDDING_FIELD_MAP_FIELD = "text_embedding_field_map";
    public static final String DATA_SOURCE_FIELD = "data_source";
    public static final String CONNECTOR_CREDENTIAL_FIELD = "credential";
    @Getter
    private String indexName;
    @Getter
    private Map<String, String> fieldMapping;
    @Getter
    private Map<String, String> dataSources;
    @Getter
    private Map<String, String> credential;

    @Builder(toBuilder = true)
    public MLBatchIngestionInput(
        String indexName,
        Map<String, String> fieldMapping,
        Map<String, String> dataSources,
        Map<String, String> credential
    ) {
        this.indexName = indexName;
        this.fieldMapping = fieldMapping;
        this.dataSources = dataSources;
        this.credential = credential;
    }

    public static MLBatchIngestionInput parse(XContentParser parser) throws IOException {
        String indexName = null;
        Map<String, String> fieldMapping = null;
        Map<String, String> dataSources = null;
        Map<String, String> credential = new HashMap<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case INDEX_NAME_FIELD:
                    indexName = parser.text();
                    break;
                case TEXT_EMBEDDING_FIELD_MAP_FIELD:
                    fieldMapping = getOrderedMap(parser.mapOrdered());
                    break;
                case CONNECTOR_CREDENTIAL_FIELD:
                    credential = parser.mapStrings();
                    break;
                case DATA_SOURCE_FIELD:
                    dataSources = parser.mapStrings();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLBatchIngestionInput(indexName, fieldMapping, dataSources, credential);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (indexName != null) {
            builder.field(INDEX_NAME_FIELD, indexName);
        }
        if (fieldMapping != null) {
            builder.field(TEXT_EMBEDDING_FIELD_MAP_FIELD, fieldMapping);
        }
        if (dataSources != null) {
            builder.field(DATA_SOURCE_FIELD, dataSources);
        }
        if (credential != null) {
            builder.field(CONNECTOR_CREDENTIAL_FIELD, credential);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        output.writeOptionalString(indexName);
        if (fieldMapping != null) {
            output.writeBoolean(true);
            output.writeMap(fieldMapping, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            output.writeBoolean(false);
        }

        if (dataSources != null) {
            output.writeBoolean(true);
            output.writeMap(dataSources, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            output.writeBoolean(false);
        }

        if (credential != null) {
            output.writeBoolean(true);
            output.writeMap(credential, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            output.writeBoolean(false);
        }
    }

    public MLBatchIngestionInput(StreamInput input) throws IOException {
        indexName = input.readOptionalString();
        if (input.readBoolean()) {
            fieldMapping = input.readMap(s -> s.readString(), s -> s.readString());
        }
        if (input.readBoolean()) {
            dataSources = input.readMap(s -> s.readString(), s -> s.readString());
        }
        if (input.readBoolean()) {
            credential = input.readMap(s -> s.readString(), s -> s.readString());
        }
    }

}
