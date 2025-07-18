/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.connector.ConnectorProtocols.HTTP;
import static org.opensearch.ml.common.connector.ConnectorProtocols.validateProtocol;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;
import static org.opensearch.ml.common.utils.StringUtils.isJson;
import static org.opensearch.ml.common.utils.StringUtils.parseParameters;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@NoArgsConstructor
@EqualsAndHashCode
@org.opensearch.ml.common.annotation.Connector(HTTP)
public class HttpConnector extends AbstractConnector {
    public static final String CREDENTIAL_FIELD = "credential";
    public static final String RESPONSE_FILTER_FIELD = "response_filter";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String SERVICE_NAME_FIELD = "service_name";
    public static final String REGION_FIELD = "region";

    // TODO: add RequestConfig like request time out,

    @Builder
    public HttpConnector(
        String name,
        String description,
        String version,
        String protocol,
        Map<String, String> parameters,
        Map<String, String> credential,
        List<ConnectorAction> actions,
        List<String> backendRoles,
        AccessMode accessMode,
        User owner,
        ConnectorClientConfig connectorClientConfig,
        String tenantId
    ) {
        validateProtocol(protocol);
        if (actions != null) {
            for (ConnectorAction action : actions) {
                action.validatePrePostProcessFunctions(parameters);
            }
        }
        this.name = name;
        this.description = description;
        this.version = version;
        this.protocol = protocol;
        this.parameters = parameters;
        this.credential = credential;
        this.actions = actions;
        this.backendRoles = backendRoles;
        this.access = accessMode;
        this.owner = owner;
        this.connectorClientConfig = connectorClientConfig;
        this.tenantId = tenantId;

    }

    public HttpConnector(String protocol, XContentParser parser) throws IOException {
        this.protocol = protocol;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case VERSION_FIELD:
                    version = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case PROTOCOL_FIELD:
                    this.protocol = parser.text();
                    break;
                case PARAMETERS_FIELD:
                    Map<String, Object> map = parser.map();
                    parameters = getParameterMap(map);
                    break;
                case CREDENTIAL_FIELD:
                    credential = new HashMap<>();
                    credential.putAll(parser.mapStrings());
                    break;
                case ACTIONS_FIELD:
                    actions = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        actions.add(ConnectorAction.parse(parser));
                    }
                    break;
                case BACKEND_ROLES_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    backendRoles = new ArrayList<>();
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        backendRoles.add(parser.text());
                    }
                    break;
                case OWNER_FIELD:
                    owner = User.parse(parser);
                    break;
                case ACCESS_FIELD:
                    access = AccessMode.from(parser.text());
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case CLIENT_CONFIG_FIELD:
                    connectorClientConfig = ConnectorClientConfig.parse(parser);
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(NAME_FIELD, name);
        }
        if (version != null) {
            builder.field(VERSION_FIELD, version);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (protocol != null) {
            builder.field(PROTOCOL_FIELD, protocol);
        }
        if (parameters != null) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        if (credential != null) {
            builder.field(CREDENTIAL_FIELD, credential);
        }
        if (actions != null) {
            builder.field(ACTIONS_FIELD, actions);
        }
        if (backendRoles != null) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        if (owner != null) {
            builder.field(OWNER_FIELD, owner);
        }
        if (access != null) {
            builder.field(ACCESS_FIELD, access.getValue());
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        if (connectorClientConfig != null) {
            builder.field(CLIENT_CONFIG_FIELD, connectorClientConfig);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public HttpConnector(String protocol, StreamInput input) throws IOException {
        this.protocol = protocol;
        parseFromStream(input);
    }

    public HttpConnector(StreamInput input) throws IOException {
        this.protocol = input.readString();
        parseFromStream(input);
    }

    private void parseFromStream(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        this.name = input.readOptionalString();
        this.version = input.readOptionalString();
        this.description = input.readOptionalString();
        if (input.readBoolean()) {
            parameters = input.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (input.readBoolean()) {
            credential = input.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (input.readBoolean()) {
            actions = new ArrayList<>();
            int size = input.readInt();
            for (int i = 0; i < size; i++) {
                actions.add(new ConnectorAction(input));
            }
        }
        backendRoles = input.readOptionalStringList();
        if (input.readBoolean()) {
            this.access = input.readEnum(AccessMode.class);
        }
        if (input.readBoolean()) {
            this.owner = new User(input);
        }
        this.createdTime = input.readOptionalInstant();
        this.lastUpdateTime = input.readOptionalInstant();
        if (input.readBoolean()) {
            this.connectorClientConfig = new ConnectorClientConfig(input);
        }
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeString(protocol);
        out.writeOptionalString(name);
        out.writeOptionalString(version);
        out.writeOptionalString(description);
        if (parameters != null) {
            out.writeBoolean(true);
            out.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (credential != null) {
            out.writeBoolean(true);
            out.writeMap(credential, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (actions != null) {
            out.writeBoolean(true);
            out.writeInt(actions.size());
            for (ConnectorAction action : actions) {
                action.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalStringCollection(backendRoles);
        if (access != null) {
            out.writeBoolean(true);
            out.writeEnum(access);
        } else {
            out.writeBoolean(false);
        }
        if (owner != null) {
            out.writeBoolean(true);
            owner.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdateTime);
        if (connectorClientConfig != null) {
            out.writeBoolean(true);
            connectorClientConfig.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
    }

    @Override
    public void update(MLCreateConnectorInput updateContent, BiFunction<String, String, String> function) {
        if (updateContent.getName() != null) {
            this.name = updateContent.getName();
        }
        if (updateContent.getDescription() != null) {
            this.description = updateContent.getDescription();
        }
        if (updateContent.getVersion() != null) {
            this.version = updateContent.getVersion();
        }
        if (updateContent.getProtocol() != null) {
            this.protocol = updateContent.getProtocol();
        }
        if (updateContent.getParameters() != null && !updateContent.getParameters().isEmpty()) {
            getParameters().putAll(updateContent.getParameters());
        }
        if (updateContent.getCredential() != null && !updateContent.getCredential().isEmpty()) {
            this.credential = updateContent.getCredential();
            encrypt(function, this.tenantId);
        }
        if (updateContent.getActions() != null) {
            this.actions = updateContent.getActions();
        }
        if (updateContent.getBackendRoles() != null) {
            this.backendRoles = updateContent.getBackendRoles();
        }
        if (updateContent.getAccess() != null) {
            this.access = updateContent.getAccess();
        }
        if (updateContent.getConnectorClientConfig() != null) {
            this.connectorClientConfig = updateContent.getConnectorClientConfig();
        }
    }

    @Override
    public <T> T createPayload(String action, Map<String, String> parameters) {
        Optional<ConnectorAction> connectorAction = findAction(action);
        if (connectorAction.isPresent() && connectorAction.get().getRequestBody() != null) {
            String payload = connectorAction.get().getRequestBody();
            payload = fillNullParameters(parameters, payload);
            parseParameters(parameters);
            StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            payload = substitutor.replace(payload);

            if (!isJson(payload)) {
                throw new IllegalArgumentException("Invalid payload: " + payload);
            }
            return (T) payload;
        }
        return (T) parameters.get("http_body");
    }

    protected String fillNullParameters(Map<String, String> parameters, String payload) {
        List<String> bodyParams = findStringParametersWithNullDefaultValue(payload);
        String newPayload = payload;
        for (String key : bodyParams) {
            if (!parameters.containsKey(key) || parameters.get(key) == null) {
                newPayload = newPayload.replace("\"${parameters." + key + ":-null}\"", "null");
            }
        }
        return newPayload;
    }

    private List<String> findStringParametersWithNullDefaultValue(String input) {
        String regex = "\"\\$\\{parameters\\.(\\w+):-null}\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        List<String> paramList = new ArrayList<>();
        while (matcher.find()) {
            String parameterValue = matcher.group(1);
            paramList.add(parameterValue);
        }
        return paramList;
    }

    @Override
    public void decrypt(String action, BiFunction<String, String, String> function, String tenantId) {
        Map<String, String> decrypted = new HashMap<>();
        for (String key : credential.keySet()) {
            decrypted.put(key, function.apply(credential.get(key), tenantId));
        }
        this.decryptedCredential = decrypted;
        Optional<ConnectorAction> connectorAction = findAction(action);
        Map<String, String> headers = connectorAction.map(ConnectorAction::getHeaders).orElse(null);
        this.decryptedHeaders = createDecryptedHeaders(headers);
    }

    @Override
    public Connector cloneConnector() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()) {
            this.writeTo(bytesStreamOutput);
            StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
            return new HttpConnector(streamInput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encrypt(BiFunction<String, String, String> function, String tenantId) {
        for (String key : credential.keySet()) {
            String encrypted = function.apply(credential.get(key), tenantId);
            credential.put(key, encrypted);
        }
    }

    @Override
    public String getActionHttpMethod(String action) {
        return findAction(action).get().getMethod();
    }

}
