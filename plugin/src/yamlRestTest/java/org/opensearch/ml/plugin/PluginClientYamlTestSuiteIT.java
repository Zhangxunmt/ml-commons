/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.plugin;

import org.opensearch.test.rest.yaml.ClientYamlTestCandidate;
import org.opensearch.test.rest.yaml.OpenSearchClientYamlSuiteTestCase;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

public class PluginClientYamlTestSuiteIT extends OpenSearchClientYamlSuiteTestCase {

    public PluginClientYamlTestSuiteIT(@Name("yaml") ClientYamlTestCandidate testCandidate) {
        super(testCandidate);
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() throws Exception {
        return OpenSearchClientYamlSuiteTestCase.createParameters();
    }
}
