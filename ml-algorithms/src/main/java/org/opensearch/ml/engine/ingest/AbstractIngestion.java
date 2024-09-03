/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.ingest;

import static org.opensearch.ml.common.utils.StringUtils.getJsonPath;
import static org.opensearch.ml.common.utils.StringUtils.obtainFieldNameFromJsonPath;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;
import org.opensearch.ml.common.utils.StringUtils;

import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class AbstractIngestion implements Ingestable {
    public static final String OUTPUT = "output";
    public static final String INPUT = "input";
    public static final String OUTPUTIELDS = "output_names";
    public static final String INPUTFIELDS = "input_names";
    public static final String INGESTFIELDS = "ingest_fields";
    public static final String IDFIELD = "id_field";

    private final Client client;

    public AbstractIngestion(Client client) {
        this.client = client;
    }

    protected ActionListener<BulkResponse> getBulkResponseListener(
        AtomicInteger successfulBatches,
        AtomicInteger failedBatches,
        CompletableFuture<Void> future
    ) {
        return ActionListener.wrap(bulkResponse -> {
            if (bulkResponse.hasFailures()) {
                failedBatches.incrementAndGet();
                future.completeExceptionally(new RuntimeException(bulkResponse.buildFailureMessage()));  // Mark the future as completed
                // with an exception
                return;
            }
            log.debug("Batch Ingestion successfully");
            successfulBatches.incrementAndGet();
            future.complete(null); // Mark the future as completed successfully
        }, e -> {
            log.error("Failed to Batch Ingestion", e);
            failedBatches.incrementAndGet();
            future.completeExceptionally(e);  // Mark the future as completed with an exception
        });
    }

    protected double calculateSuccessRate(List<Double> successRates) {
        return successRates
            .stream()
            .min(Double::compare)
            .orElseThrow(
                () -> new OpenSearchStatusException(
                    "Failed to batch ingest data as not success rate is returned",
                    RestStatus.INTERNAL_SERVER_ERROR
                )
            );
    }

    /**
     * Filters fields in the map where the value contains the specified source index as a prefix.
     *
     * @param mlBatchIngestionInput The MLBatchIngestionInput.
     * @param index    The source index to filter by.
     * @return A new map with only the entries that match the specified source index.
     */
    protected Map<String, Object> filterFieldMapping(MLBatchIngestionInput mlBatchIngestionInput, int index) {
        Map<String, Object> fieldMap = mlBatchIngestionInput.getFieldMapping();
        int indexInFieldMap = index + 1;
        String prefix = "source[" + indexInFieldMap + "]";

        Map<String, Object> filteredFieldMap = fieldMap.entrySet().stream().filter(entry -> {
            Object value = entry.getValue();
            if (value instanceof String) {
                return ((String) value).contains(prefix);
            } else if (value instanceof String[]) {
                return Arrays.stream((String[]) value).anyMatch(val -> val.contains(prefix));
            }
            return false;
        }).collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            Object value = entry.getValue();
            if (value instanceof String) {
                return value;
            } else if (value instanceof String[]) {
                return Arrays.stream((String[]) value).filter(val -> val.contains(prefix)).toArray(String[]::new);
            }
            return null;
        }));

        if (filteredFieldMap.containsKey(OUTPUT)) {
            filteredFieldMap.put(OUTPUTIELDS, fieldMap.get(OUTPUTIELDS));
        }
        if (filteredFieldMap.containsKey(INPUT)) {
            filteredFieldMap.put(INPUTFIELDS, fieldMap.get(INPUTFIELDS));
        }
        return filteredFieldMap;
    }

    /**
     * Produce the source as a Map to be ingested in to OpenSearch.
     *
     * @param jsonStr The MLBatchIngestionInput.
     * @param fieldMapping  The field mapping that includes all the field name and Json Path for the data.
     * @return A new map that contains all the fields and data for ingestion.
     */
    protected Map<String, Object> processFieldMapping(String jsonStr, Map<String, Object> fieldMapping) {
        String inputJsonPath = fieldMapping.containsKey(INPUT) ? getJsonPath((String) fieldMapping.get(INPUT)) : null;
        List<String> remoteModelInput = (List<String>) JsonPath.read(jsonStr, inputJsonPath);
        List<String> inputFieldNames = inputJsonPath != null ? (List<String>) fieldMapping.get(INPUTFIELDS) : null;

        String outputJsonPath = fieldMapping.containsKey(OUTPUT) ? getJsonPath((String) fieldMapping.get(OUTPUT)) : null;
        List<List> remoteModelOutput = (List<List>) JsonPath.read(jsonStr, outputJsonPath);
        List<String> outputFieldNames = outputJsonPath != null ? (List<String>) fieldMapping.get(OUTPUTIELDS) : null;

        List<String> ingestFieldsJsonPath = ((List<String>) fieldMapping.get(INGESTFIELDS))
            .stream()
            .map(StringUtils::getJsonPath)
            .collect(Collectors.toList());

        if (remoteModelInput.size() != inputFieldNames.size() || remoteModelOutput.size() != outputFieldNames.size()) {
            throw new IllegalArgumentException("the fieldMapping and source data do not match");
        }
        Map<String, Object> jsonMap = new HashMap<>();

        for (int index = 0; index < remoteModelInput.size(); index++) {
            jsonMap.put(inputFieldNames.get(index), remoteModelInput.get(index));
            jsonMap.put(outputFieldNames.get(index), remoteModelOutput.get(index));
        }

        for (String fieldPath : ingestFieldsJsonPath) {
            jsonMap.put(obtainFieldNameFromJsonPath(fieldPath), JsonPath.read(jsonStr, fieldPath));
        }

        if (fieldMapping.containsKey(IDFIELD)) {
            List<String> docIdJsonPath = Optional
                .ofNullable((List<String>) fieldMapping.get(IDFIELD))
                .stream()
                .flatMap(Collection::stream)
                .map(StringUtils::getJsonPath)
                .collect(Collectors.toList());
            if (docIdJsonPath.size() != 1) {
                throw new IllegalArgumentException("The Id field must contains only 1 jsonPath for each source");
            }
            jsonMap.put("_id", JsonPath.read(jsonStr, docIdJsonPath.get(0)));
        }
        return jsonMap;
    }

    protected void batchIngest(
        List<String> sourceLines,
        MLBatchIngestionInput mlBatchIngestionInput,
        ActionListener<BulkResponse> bulkResponseListener,
        int sourceIndex,
        boolean isSoleSource
    ) {
        BulkRequest bulkRequest = new BulkRequest();
        sourceLines.stream().forEach(jsonStr -> {
            Map<String, Object> filteredMapping = isSoleSource
                ? mlBatchIngestionInput.getFieldMapping()
                : filterFieldMapping(mlBatchIngestionInput, sourceIndex);
            Map<String, Object> jsonMap = processFieldMapping(jsonStr, filteredMapping);
            if (isSoleSource || sourceIndex == 0) {
                IndexRequest indexRequest = new IndexRequest(mlBatchIngestionInput.getIndexName()).source(jsonMap);
                if (jsonMap.containsKey("_id")) {
                    indexRequest.id((String) jsonMap.get("_id"));
                }
                bulkRequest.add(indexRequest);
            } else {
                // bulk update docs as they were partially ingested
                if (!jsonMap.containsKey("_id")) {
                    throw new IllegalArgumentException("The id filed must be provided to match documents for multiple sources");
                }
                String id = (String) jsonMap.get("_id");
                UpdateRequest updateRequest = new UpdateRequest(mlBatchIngestionInput.getIndexName(), id).doc(jsonMap).upsert(jsonMap);
                bulkRequest.add(updateRequest);
            }
        });
        client.bulk(bulkRequest, bulkResponseListener);
    }
}
