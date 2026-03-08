package eu.openanalytics.containerproxy.backend.spcs.client.api;

import eu.openanalytics.containerproxy.backend.spcs.client.ApiClient;

import eu.openanalytics.containerproxy.backend.spcs.client.model.ErrorResponse;
import eu.openanalytics.containerproxy.backend.spcs.client.model.FetchServiceLogs200Response;
import eu.openanalytics.containerproxy.backend.spcs.client.model.FetchServiceStatus200Response;
import eu.openanalytics.containerproxy.backend.spcs.client.model.GrantOf;
import eu.openanalytics.containerproxy.backend.spcs.client.model.JobService;
import eu.openanalytics.containerproxy.backend.spcs.client.model.Service;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ServiceContainer;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ServiceEndpoint;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ServiceInstance;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ServiceRole;
import eu.openanalytics.containerproxy.backend.spcs.client.model.ServiceRoleGrantTo;
import eu.openanalytics.containerproxy.backend.spcs.client.model.SuccessAcceptedResponse;
import eu.openanalytics.containerproxy.backend.spcs.client.model.SuccessResponse;
import java.util.UUID;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2026-03-02T16:41:25.529847700+08:00[Australia/Perth]", comments = "Generator version: 7.17.0")
public class ServiceApi {
    private ApiClient apiClient;

    public ServiceApi() {
        this(new ApiClient());
    }

    public ServiceApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Create a (or alter an existing) service.
     * Create a (or alter an existing) service. Even if the operation is just an alter, the full property set must be provided.
     * <p><b>200</b> - Successful request
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param service The service parameter
     * @return SuccessResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec createOrAlterServiceRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nonnull Service service) throws WebClientResponseException {
        Object postBody = service;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling createOrAlterService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling createOrAlterService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling createOrAlterService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'service' is set
        if (service == null) {
            throw new WebClientResponseException("Missing the required parameter 'service' when calling createOrAlterService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{name}", HttpMethod.PUT, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create a (or alter an existing) service.
     * Create a (or alter an existing) service. Even if the operation is just an alter, the full property set must be provided.
     * <p><b>200</b> - Successful request
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param service The service parameter
     * @return SuccessResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SuccessResponse> createOrAlterService(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nonnull Service service) throws WebClientResponseException {
        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return createOrAlterServiceRequestCreation(database, schema, name, service).bodyToMono(localVarReturnType);
    }

    /**
     * Create a (or alter an existing) service.
     * Create a (or alter an existing) service. Even if the operation is just an alter, the full property set must be provided.
     * <p><b>200</b> - Successful request
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param service The service parameter
     * @return ResponseEntity&lt;SuccessResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SuccessResponse>> createOrAlterServiceWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nonnull Service service) throws WebClientResponseException {
        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return createOrAlterServiceRequestCreation(database, schema, name, service).toEntity(localVarReturnType);
    }

    /**
     * Create a (or alter an existing) service.
     * Create a (or alter an existing) service. Even if the operation is just an alter, the full property set must be provided.
     * <p><b>200</b> - Successful request
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param service The service parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec createOrAlterServiceWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nonnull Service service) throws WebClientResponseException {
        return createOrAlterServiceRequestCreation(database, schema, name, service);
    }

    /**
     * Create a service
     * Create a service, with standard create modifiers as query parameters. See the Service component definition for what is required to be provided in the request body.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>408</b> - Request Timeout. This indicates that the request from the client timed out and was not completed by the server.
     * <p><b>409</b> - Conflict. The requested operation could not be performed due to a conflicting state that could not be resolved. This usually happens when a CREATE request was performed when there is a pre-existing resource with the same name, and also without one of the options orReplace/ifNotExists.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param service The service parameter
     * @param createMode Query parameter allowing support for different modes of resource creation. Possible values include: - &#x60;errorIfExists&#x60;: Throws an error if you try to create a resource that already exists. - &#x60;ifNotExists&#x60;: Creates a new resource when an alter is requested for a non-existent resource.
     * @return SuccessResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec createServiceRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull Service service, @javax.annotation.Nullable String createMode) throws WebClientResponseException {
        Object postBody = service;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling createService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling createService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'service' is set
        if (service == null) {
            throw new WebClientResponseException("Missing the required parameter 'service' when calling createService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "createMode", createMode));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services", HttpMethod.POST, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Create a service
     * Create a service, with standard create modifiers as query parameters. See the Service component definition for what is required to be provided in the request body.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>408</b> - Request Timeout. This indicates that the request from the client timed out and was not completed by the server.
     * <p><b>409</b> - Conflict. The requested operation could not be performed due to a conflicting state that could not be resolved. This usually happens when a CREATE request was performed when there is a pre-existing resource with the same name, and also without one of the options orReplace/ifNotExists.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param service The service parameter
     * @param createMode Query parameter allowing support for different modes of resource creation. Possible values include: - &#x60;errorIfExists&#x60;: Throws an error if you try to create a resource that already exists. - &#x60;ifNotExists&#x60;: Creates a new resource when an alter is requested for a non-existent resource.
     * @return SuccessResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SuccessResponse> createService(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull Service service, @javax.annotation.Nullable String createMode) throws WebClientResponseException {
        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return createServiceRequestCreation(database, schema, service, createMode).bodyToMono(localVarReturnType);
    }

    /**
     * Create a service
     * Create a service, with standard create modifiers as query parameters. See the Service component definition for what is required to be provided in the request body.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>408</b> - Request Timeout. This indicates that the request from the client timed out and was not completed by the server.
     * <p><b>409</b> - Conflict. The requested operation could not be performed due to a conflicting state that could not be resolved. This usually happens when a CREATE request was performed when there is a pre-existing resource with the same name, and also without one of the options orReplace/ifNotExists.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param service The service parameter
     * @param createMode Query parameter allowing support for different modes of resource creation. Possible values include: - &#x60;errorIfExists&#x60;: Throws an error if you try to create a resource that already exists. - &#x60;ifNotExists&#x60;: Creates a new resource when an alter is requested for a non-existent resource.
     * @return ResponseEntity&lt;SuccessResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SuccessResponse>> createServiceWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull Service service, @javax.annotation.Nullable String createMode) throws WebClientResponseException {
        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return createServiceRequestCreation(database, schema, service, createMode).toEntity(localVarReturnType);
    }

    /**
     * Create a service
     * Create a service, with standard create modifiers as query parameters. See the Service component definition for what is required to be provided in the request body.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>408</b> - Request Timeout. This indicates that the request from the client timed out and was not completed by the server.
     * <p><b>409</b> - Conflict. The requested operation could not be performed due to a conflicting state that could not be resolved. This usually happens when a CREATE request was performed when there is a pre-existing resource with the same name, and also without one of the options orReplace/ifNotExists.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param service The service parameter
     * @param createMode Query parameter allowing support for different modes of resource creation. Possible values include: - &#x60;errorIfExists&#x60;: Throws an error if you try to create a resource that already exists. - &#x60;ifNotExists&#x60;: Creates a new resource when an alter is requested for a non-existent resource.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec createServiceWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull Service service, @javax.annotation.Nullable String createMode) throws WebClientResponseException {
        return createServiceRequestCreation(database, schema, service, createMode);
    }

    /**
     * Delete a service
     * Delete a service with the given name. If ifExists is used, the operation will succeed even if the object does not exist. Otherwise, there will be a failure if the drop is unsuccessful.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param ifExists Parameter that specifies how to handle the request for a resource that does not exist: - &#x60;true&#x60;: The endpoint does not throw an error if the resource does not exist. It returns a 200 success response, but does not take any action on the resource. - &#x60;false&#x60;: The endpoint throws an error if the resource doesn&#39;t exist.
     * @return SuccessResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec deleteServiceRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Boolean ifExists) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling deleteService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling deleteService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling deleteService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "ifExists", ifExists));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{name}", HttpMethod.DELETE, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Delete a service
     * Delete a service with the given name. If ifExists is used, the operation will succeed even if the object does not exist. Otherwise, there will be a failure if the drop is unsuccessful.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param ifExists Parameter that specifies how to handle the request for a resource that does not exist: - &#x60;true&#x60;: The endpoint does not throw an error if the resource does not exist. It returns a 200 success response, but does not take any action on the resource. - &#x60;false&#x60;: The endpoint throws an error if the resource doesn&#39;t exist.
     * @return SuccessResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SuccessResponse> deleteService(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Boolean ifExists) throws WebClientResponseException {
        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return deleteServiceRequestCreation(database, schema, name, ifExists).bodyToMono(localVarReturnType);
    }

    /**
     * Delete a service
     * Delete a service with the given name. If ifExists is used, the operation will succeed even if the object does not exist. Otherwise, there will be a failure if the drop is unsuccessful.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param ifExists Parameter that specifies how to handle the request for a resource that does not exist: - &#x60;true&#x60;: The endpoint does not throw an error if the resource does not exist. It returns a 200 success response, but does not take any action on the resource. - &#x60;false&#x60;: The endpoint throws an error if the resource doesn&#39;t exist.
     * @return ResponseEntity&lt;SuccessResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SuccessResponse>> deleteServiceWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Boolean ifExists) throws WebClientResponseException {
        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return deleteServiceRequestCreation(database, schema, name, ifExists).toEntity(localVarReturnType);
    }

    /**
     * Delete a service
     * Delete a service with the given name. If ifExists is used, the operation will succeed even if the object does not exist. Otherwise, there will be a failure if the drop is unsuccessful.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param ifExists Parameter that specifies how to handle the request for a resource that does not exist: - &#x60;true&#x60;: The endpoint does not throw an error if the resource does not exist. It returns a 200 success response, but does not take any action on the resource. - &#x60;false&#x60;: The endpoint throws an error if the resource doesn&#39;t exist.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec deleteServiceWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Boolean ifExists) throws WebClientResponseException {
        return deleteServiceRequestCreation(database, schema, name, ifExists);
    }

    /**
     * Execute a job service
     * Create and execute a job service. See the JobService component definition for what is required to be provided in the request body.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>409</b> - Conflict. The requested operation could not be performed due to a conflicting state that could not be resolved. This usually happens when a CREATE request was performed when there is a pre-existing resource with the same name, and also without one of the options orReplace/ifNotExists.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param jobService The jobService parameter
     * @return SuccessResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec executeJobServiceRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull JobService jobService) throws WebClientResponseException {
        Object postBody = jobService;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling executeJobService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling executeJobService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'jobService' is set
        if (jobService == null) {
            throw new WebClientResponseException("Missing the required parameter 'jobService' when calling executeJobService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { 
            "application/json"
        };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services:execute-job", HttpMethod.POST, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * Execute a job service
     * Create and execute a job service. See the JobService component definition for what is required to be provided in the request body.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>409</b> - Conflict. The requested operation could not be performed due to a conflicting state that could not be resolved. This usually happens when a CREATE request was performed when there is a pre-existing resource with the same name, and also without one of the options orReplace/ifNotExists.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param jobService The jobService parameter
     * @return SuccessResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SuccessResponse> executeJobService(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull JobService jobService) throws WebClientResponseException {
        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return executeJobServiceRequestCreation(database, schema, jobService).bodyToMono(localVarReturnType);
    }

    /**
     * Execute a job service
     * Create and execute a job service. See the JobService component definition for what is required to be provided in the request body.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>409</b> - Conflict. The requested operation could not be performed due to a conflicting state that could not be resolved. This usually happens when a CREATE request was performed when there is a pre-existing resource with the same name, and also without one of the options orReplace/ifNotExists.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param jobService The jobService parameter
     * @return ResponseEntity&lt;SuccessResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SuccessResponse>> executeJobServiceWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull JobService jobService) throws WebClientResponseException {
        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return executeJobServiceRequestCreation(database, schema, jobService).toEntity(localVarReturnType);
    }

    /**
     * Execute a job service
     * Create and execute a job service. See the JobService component definition for what is required to be provided in the request body.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>409</b> - Conflict. The requested operation could not be performed due to a conflicting state that could not be resolved. This usually happens when a CREATE request was performed when there is a pre-existing resource with the same name, and also without one of the options orReplace/ifNotExists.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param jobService The jobService parameter
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec executeJobServiceWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull JobService jobService) throws WebClientResponseException {
        return executeJobServiceRequestCreation(database, schema, jobService);
    }

    /**
     * 
     * Fetch a service.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return Service
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec fetchServiceRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling fetchService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling fetchService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling fetchService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<Service> localVarReturnType = new ParameterizedTypeReference<Service>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{name}", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * Fetch a service.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return Service
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<Service> fetchService(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<Service> localVarReturnType = new ParameterizedTypeReference<Service>() {};
        return fetchServiceRequestCreation(database, schema, name).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * Fetch a service.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseEntity&lt;Service&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<Service>> fetchServiceWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<Service> localVarReturnType = new ParameterizedTypeReference<Service>() {};
        return fetchServiceRequestCreation(database, schema, name).toEntity(localVarReturnType);
    }

    /**
     * 
     * Fetch a service.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fetchServiceWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        return fetchServiceRequestCreation(database, schema, name);
    }

    /**
     * 
     * Fetch the logs for a given service.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param instanceId ID of the service instance, starting with 0.
     * @param containerName Container name as specified in the service specification file.
     * @param numLines Number of trailing log lines to retrieve.
     * @return FetchServiceLogs200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec fetchServiceLogsRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nonnull Integer instanceId, @javax.annotation.Nonnull String containerName, @javax.annotation.Nullable Integer numLines) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling fetchServiceLogs", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling fetchServiceLogs", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling fetchServiceLogs", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'instanceId' is set
        if (instanceId == null) {
            throw new WebClientResponseException("Missing the required parameter 'instanceId' when calling fetchServiceLogs", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'containerName' is set
        if (containerName == null) {
            throw new WebClientResponseException("Missing the required parameter 'containerName' when calling fetchServiceLogs", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "instanceId", instanceId));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "containerName", containerName));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "numLines", numLines));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<FetchServiceLogs200Response> localVarReturnType = new ParameterizedTypeReference<FetchServiceLogs200Response>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{name}/logs", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * Fetch the logs for a given service.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param instanceId ID of the service instance, starting with 0.
     * @param containerName Container name as specified in the service specification file.
     * @param numLines Number of trailing log lines to retrieve.
     * @return FetchServiceLogs200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<FetchServiceLogs200Response> fetchServiceLogs(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nonnull Integer instanceId, @javax.annotation.Nonnull String containerName, @javax.annotation.Nullable Integer numLines) throws WebClientResponseException {
        ParameterizedTypeReference<FetchServiceLogs200Response> localVarReturnType = new ParameterizedTypeReference<FetchServiceLogs200Response>() {};
        return fetchServiceLogsRequestCreation(database, schema, name, instanceId, containerName, numLines).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * Fetch the logs for a given service.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param instanceId ID of the service instance, starting with 0.
     * @param containerName Container name as specified in the service specification file.
     * @param numLines Number of trailing log lines to retrieve.
     * @return ResponseEntity&lt;FetchServiceLogs200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<FetchServiceLogs200Response>> fetchServiceLogsWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nonnull Integer instanceId, @javax.annotation.Nonnull String containerName, @javax.annotation.Nullable Integer numLines) throws WebClientResponseException {
        ParameterizedTypeReference<FetchServiceLogs200Response> localVarReturnType = new ParameterizedTypeReference<FetchServiceLogs200Response>() {};
        return fetchServiceLogsRequestCreation(database, schema, name, instanceId, containerName, numLines).toEntity(localVarReturnType);
    }

    /**
     * 
     * Fetch the logs for a given service.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param instanceId ID of the service instance, starting with 0.
     * @param containerName Container name as specified in the service specification file.
     * @param numLines Number of trailing log lines to retrieve.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fetchServiceLogsWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nonnull Integer instanceId, @javax.annotation.Nonnull String containerName, @javax.annotation.Nullable Integer numLines) throws WebClientResponseException {
        return fetchServiceLogsRequestCreation(database, schema, name, instanceId, containerName, numLines);
    }

    /**
     * 
     * Fetch the status for a given service. Deprecated - use listServiceContainers instead.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param timeout Number of seconds to wait for the service to reach a steady state (for example, READY) before returning the status. If the service does not reach a steady state within the specified time, Snowflake returns the current state.
     * @return FetchServiceStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     * @deprecated
     */
    @Deprecated
    private ResponseSpec fetchServiceStatusRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Integer timeout) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling fetchServiceStatus", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling fetchServiceStatus", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling fetchServiceStatus", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "timeout", timeout));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<FetchServiceStatus200Response> localVarReturnType = new ParameterizedTypeReference<FetchServiceStatus200Response>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{name}/status", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * Fetch the status for a given service. Deprecated - use listServiceContainers instead.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param timeout Number of seconds to wait for the service to reach a steady state (for example, READY) before returning the status. If the service does not reach a steady state within the specified time, Snowflake returns the current state.
     * @return FetchServiceStatus200Response
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<FetchServiceStatus200Response> fetchServiceStatus(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Integer timeout) throws WebClientResponseException {
        ParameterizedTypeReference<FetchServiceStatus200Response> localVarReturnType = new ParameterizedTypeReference<FetchServiceStatus200Response>() {};
        return fetchServiceStatusRequestCreation(database, schema, name, timeout).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * Fetch the status for a given service. Deprecated - use listServiceContainers instead.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param timeout Number of seconds to wait for the service to reach a steady state (for example, READY) before returning the status. If the service does not reach a steady state within the specified time, Snowflake returns the current state.
     * @return ResponseEntity&lt;FetchServiceStatus200Response&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<FetchServiceStatus200Response>> fetchServiceStatusWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Integer timeout) throws WebClientResponseException {
        ParameterizedTypeReference<FetchServiceStatus200Response> localVarReturnType = new ParameterizedTypeReference<FetchServiceStatus200Response>() {};
        return fetchServiceStatusRequestCreation(database, schema, name, timeout).toEntity(localVarReturnType);
    }

    /**
     * 
     * Fetch the status for a given service. Deprecated - use listServiceContainers instead.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param timeout Number of seconds to wait for the service to reach a steady state (for example, READY) before returning the status. If the service does not reach a steady state within the specified time, Snowflake returns the current state.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec fetchServiceStatusWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Integer timeout) throws WebClientResponseException {
        return fetchServiceStatusRequestCreation(database, schema, name, timeout);
    }

    /**
     * 
     * List all the containers of the service
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return List&lt;ServiceContainer&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec listServiceContainersRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling listServiceContainers", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling listServiceContainers", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling listServiceContainers", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<ServiceContainer> localVarReturnType = new ParameterizedTypeReference<ServiceContainer>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{name}/containers", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * List all the containers of the service
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return List&lt;ServiceContainer&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ServiceContainer> listServiceContainers(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<ServiceContainer> localVarReturnType = new ParameterizedTypeReference<ServiceContainer>() {};
        return listServiceContainersRequestCreation(database, schema, name).bodyToFlux(localVarReturnType);
    }

    /**
     * 
     * List all the containers of the service
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseEntity&lt;List&lt;ServiceContainer&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ServiceContainer>>> listServiceContainersWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<ServiceContainer> localVarReturnType = new ParameterizedTypeReference<ServiceContainer>() {};
        return listServiceContainersRequestCreation(database, schema, name).toEntityList(localVarReturnType);
    }

    /**
     * 
     * List all the containers of the service
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec listServiceContainersWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        return listServiceContainersRequestCreation(database, schema, name);
    }

    /**
     * 
     * List all the instances of the service
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return List&lt;ServiceInstance&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec listServiceInstancesRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling listServiceInstances", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling listServiceInstances", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling listServiceInstances", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<ServiceInstance> localVarReturnType = new ParameterizedTypeReference<ServiceInstance>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{name}/instances", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * List all the instances of the service
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return List&lt;ServiceInstance&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ServiceInstance> listServiceInstances(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<ServiceInstance> localVarReturnType = new ParameterizedTypeReference<ServiceInstance>() {};
        return listServiceInstancesRequestCreation(database, schema, name).bodyToFlux(localVarReturnType);
    }

    /**
     * 
     * List all the instances of the service
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseEntity&lt;List&lt;ServiceInstance&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ServiceInstance>>> listServiceInstancesWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<ServiceInstance> localVarReturnType = new ParameterizedTypeReference<ServiceInstance>() {};
        return listServiceInstancesRequestCreation(database, schema, name).toEntityList(localVarReturnType);
    }

    /**
     * 
     * List all the instances of the service
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec listServiceInstancesWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        return listServiceInstancesRequestCreation(database, schema, name);
    }

    /**
     * 
     * List all the grants of the service role
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param service Name of the service that contains the service role.
     * @param name Identifier (i.e. name) for the resource.
     * @return List&lt;GrantOf&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec listServiceRoleGrantsOfRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String service, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling listServiceRoleGrantsOf", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling listServiceRoleGrantsOf", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'service' is set
        if (service == null) {
            throw new WebClientResponseException("Missing the required parameter 'service' when calling listServiceRoleGrantsOf", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling listServiceRoleGrantsOf", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("service", service);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<GrantOf> localVarReturnType = new ParameterizedTypeReference<GrantOf>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{service}/roles/{name}/grants-of", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * List all the grants of the service role
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param service Name of the service that contains the service role.
     * @param name Identifier (i.e. name) for the resource.
     * @return List&lt;GrantOf&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<GrantOf> listServiceRoleGrantsOf(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String service, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<GrantOf> localVarReturnType = new ParameterizedTypeReference<GrantOf>() {};
        return listServiceRoleGrantsOfRequestCreation(database, schema, service, name).bodyToFlux(localVarReturnType);
    }

    /**
     * 
     * List all the grants of the service role
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param service Name of the service that contains the service role.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseEntity&lt;List&lt;GrantOf&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<GrantOf>>> listServiceRoleGrantsOfWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String service, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<GrantOf> localVarReturnType = new ParameterizedTypeReference<GrantOf>() {};
        return listServiceRoleGrantsOfRequestCreation(database, schema, service, name).toEntityList(localVarReturnType);
    }

    /**
     * 
     * List all the grants of the service role
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param service Name of the service that contains the service role.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec listServiceRoleGrantsOfWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String service, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        return listServiceRoleGrantsOfRequestCreation(database, schema, service, name);
    }

    /**
     * 
     * List all the grants given to the service role
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param service Name of the service that contains the service role.
     * @param name Identifier (i.e. name) for the resource.
     * @return List&lt;ServiceRoleGrantTo&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec listServiceRoleGrantsToRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String service, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling listServiceRoleGrantsTo", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling listServiceRoleGrantsTo", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'service' is set
        if (service == null) {
            throw new WebClientResponseException("Missing the required parameter 'service' when calling listServiceRoleGrantsTo", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling listServiceRoleGrantsTo", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("service", service);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<ServiceRoleGrantTo> localVarReturnType = new ParameterizedTypeReference<ServiceRoleGrantTo>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{service}/roles/{name}/grants", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * List all the grants given to the service role
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param service Name of the service that contains the service role.
     * @param name Identifier (i.e. name) for the resource.
     * @return List&lt;ServiceRoleGrantTo&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ServiceRoleGrantTo> listServiceRoleGrantsTo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String service, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<ServiceRoleGrantTo> localVarReturnType = new ParameterizedTypeReference<ServiceRoleGrantTo>() {};
        return listServiceRoleGrantsToRequestCreation(database, schema, service, name).bodyToFlux(localVarReturnType);
    }

    /**
     * 
     * List all the grants given to the service role
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param service Name of the service that contains the service role.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseEntity&lt;List&lt;ServiceRoleGrantTo&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ServiceRoleGrantTo>>> listServiceRoleGrantsToWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String service, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<ServiceRoleGrantTo> localVarReturnType = new ParameterizedTypeReference<ServiceRoleGrantTo>() {};
        return listServiceRoleGrantsToRequestCreation(database, schema, service, name).toEntityList(localVarReturnType);
    }

    /**
     * 
     * List all the grants given to the service role
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param service Name of the service that contains the service role.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec listServiceRoleGrantsToWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String service, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        return listServiceRoleGrantsToRequestCreation(database, schema, service, name);
    }

    /**
     * 
     * List all the service roles of the service
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return List&lt;ServiceRole&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec listServiceRolesRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling listServiceRoles", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling listServiceRoles", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling listServiceRoles", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<ServiceRole> localVarReturnType = new ParameterizedTypeReference<ServiceRole>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{name}/roles", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * List all the service roles of the service
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return List&lt;ServiceRole&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ServiceRole> listServiceRoles(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<ServiceRole> localVarReturnType = new ParameterizedTypeReference<ServiceRole>() {};
        return listServiceRolesRequestCreation(database, schema, name).bodyToFlux(localVarReturnType);
    }

    /**
     * 
     * List all the service roles of the service
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseEntity&lt;List&lt;ServiceRole&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ServiceRole>>> listServiceRolesWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<ServiceRole> localVarReturnType = new ParameterizedTypeReference<ServiceRole>() {};
        return listServiceRolesRequestCreation(database, schema, name).toEntityList(localVarReturnType);
    }

    /**
     * 
     * List all the service roles of the service
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec listServiceRolesWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        return listServiceRolesRequestCreation(database, schema, name);
    }

    /**
     * List services
     * Lists the services under the database and schema.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param like Parameter to filter the command output by resource name. Uses case-insensitive pattern matching, with support for SQL wildcard characters.
     * @param startsWith Parameter to filter the command output based on the string of characters that appear at the beginning of the object name. Uses case-sensitive pattern matching.
     * @param showLimit Parameter to limit the maximum number of rows returned by a command.
     * @param fromName Parameter to enable fetching rows only following the first row whose object name matches the specified string. Case-sensitive and does not have to be the full name.
     * @return List&lt;Service&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec listServicesRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nullable String like, @javax.annotation.Nullable String startsWith, @javax.annotation.Nullable Integer showLimit, @javax.annotation.Nullable String fromName) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling listServices", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling listServices", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "like", like));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "startsWith", startsWith));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "showLimit", showLimit));
        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "fromName", fromName));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<Service> localVarReturnType = new ParameterizedTypeReference<Service>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List services
     * Lists the services under the database and schema.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param like Parameter to filter the command output by resource name. Uses case-insensitive pattern matching, with support for SQL wildcard characters.
     * @param startsWith Parameter to filter the command output based on the string of characters that appear at the beginning of the object name. Uses case-sensitive pattern matching.
     * @param showLimit Parameter to limit the maximum number of rows returned by a command.
     * @param fromName Parameter to enable fetching rows only following the first row whose object name matches the specified string. Case-sensitive and does not have to be the full name.
     * @return List&lt;Service&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<Service> listServices(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nullable String like, @javax.annotation.Nullable String startsWith, @javax.annotation.Nullable Integer showLimit, @javax.annotation.Nullable String fromName) throws WebClientResponseException {
        ParameterizedTypeReference<Service> localVarReturnType = new ParameterizedTypeReference<Service>() {};
        return listServicesRequestCreation(database, schema, like, startsWith, showLimit, fromName).bodyToFlux(localVarReturnType);
    }

    /**
     * List services
     * Lists the services under the database and schema.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param like Parameter to filter the command output by resource name. Uses case-insensitive pattern matching, with support for SQL wildcard characters.
     * @param startsWith Parameter to filter the command output based on the string of characters that appear at the beginning of the object name. Uses case-sensitive pattern matching.
     * @param showLimit Parameter to limit the maximum number of rows returned by a command.
     * @param fromName Parameter to enable fetching rows only following the first row whose object name matches the specified string. Case-sensitive and does not have to be the full name.
     * @return ResponseEntity&lt;List&lt;Service&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<Service>>> listServicesWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nullable String like, @javax.annotation.Nullable String startsWith, @javax.annotation.Nullable Integer showLimit, @javax.annotation.Nullable String fromName) throws WebClientResponseException {
        ParameterizedTypeReference<Service> localVarReturnType = new ParameterizedTypeReference<Service>() {};
        return listServicesRequestCreation(database, schema, like, startsWith, showLimit, fromName).toEntityList(localVarReturnType);
    }

    /**
     * List services
     * Lists the services under the database and schema.
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param like Parameter to filter the command output by resource name. Uses case-insensitive pattern matching, with support for SQL wildcard characters.
     * @param startsWith Parameter to filter the command output based on the string of characters that appear at the beginning of the object name. Uses case-sensitive pattern matching.
     * @param showLimit Parameter to limit the maximum number of rows returned by a command.
     * @param fromName Parameter to enable fetching rows only following the first row whose object name matches the specified string. Case-sensitive and does not have to be the full name.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec listServicesWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nullable String like, @javax.annotation.Nullable String startsWith, @javax.annotation.Nullable Integer showLimit, @javax.annotation.Nullable String fromName) throws WebClientResponseException {
        return listServicesRequestCreation(database, schema, like, startsWith, showLimit, fromName);
    }

    /**
     * 
     * Resume a service.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param ifExists Parameter that specifies how to handle the request for a resource that does not exist: - &#x60;true&#x60;: The endpoint does not throw an error if the resource does not exist. It returns a 200 success response, but does not take any action on the resource. - &#x60;false&#x60;: The endpoint throws an error if the resource doesn&#39;t exist.
     * @return SuccessResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec resumeServiceRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Boolean ifExists) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling resumeService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling resumeService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling resumeService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "ifExists", ifExists));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{name}:resume", HttpMethod.POST, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * Resume a service.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param ifExists Parameter that specifies how to handle the request for a resource that does not exist: - &#x60;true&#x60;: The endpoint does not throw an error if the resource does not exist. It returns a 200 success response, but does not take any action on the resource. - &#x60;false&#x60;: The endpoint throws an error if the resource doesn&#39;t exist.
     * @return SuccessResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SuccessResponse> resumeService(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Boolean ifExists) throws WebClientResponseException {
        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return resumeServiceRequestCreation(database, schema, name, ifExists).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * Resume a service.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param ifExists Parameter that specifies how to handle the request for a resource that does not exist: - &#x60;true&#x60;: The endpoint does not throw an error if the resource does not exist. It returns a 200 success response, but does not take any action on the resource. - &#x60;false&#x60;: The endpoint throws an error if the resource doesn&#39;t exist.
     * @return ResponseEntity&lt;SuccessResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SuccessResponse>> resumeServiceWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Boolean ifExists) throws WebClientResponseException {
        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return resumeServiceRequestCreation(database, schema, name, ifExists).toEntity(localVarReturnType);
    }

    /**
     * 
     * Resume a service.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param ifExists Parameter that specifies how to handle the request for a resource that does not exist: - &#x60;true&#x60;: The endpoint does not throw an error if the resource does not exist. It returns a 200 success response, but does not take any action on the resource. - &#x60;false&#x60;: The endpoint throws an error if the resource doesn&#39;t exist.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec resumeServiceWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Boolean ifExists) throws WebClientResponseException {
        return resumeServiceRequestCreation(database, schema, name, ifExists);
    }

    /**
     * List the endpoints in a service.
     * Lists the endpoints in a Snowpark Container Services service (or a job service).
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return List&lt;ServiceEndpoint&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec showServiceEndpointsRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling showServiceEndpoints", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling showServiceEndpoints", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling showServiceEndpoints", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<ServiceEndpoint> localVarReturnType = new ParameterizedTypeReference<ServiceEndpoint>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{name}/endpoints", HttpMethod.GET, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * List the endpoints in a service.
     * Lists the endpoints in a Snowpark Container Services service (or a job service).
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return List&lt;ServiceEndpoint&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Flux<ServiceEndpoint> showServiceEndpoints(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<ServiceEndpoint> localVarReturnType = new ParameterizedTypeReference<ServiceEndpoint>() {};
        return showServiceEndpointsRequestCreation(database, schema, name).bodyToFlux(localVarReturnType);
    }

    /**
     * List the endpoints in a service.
     * Lists the endpoints in a Snowpark Container Services service (or a job service).
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseEntity&lt;List&lt;ServiceEndpoint&gt;&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<List<ServiceEndpoint>>> showServiceEndpointsWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        ParameterizedTypeReference<ServiceEndpoint> localVarReturnType = new ParameterizedTypeReference<ServiceEndpoint>() {};
        return showServiceEndpointsRequestCreation(database, schema, name).toEntityList(localVarReturnType);
    }

    /**
     * List the endpoints in a service.
     * Lists the endpoints in a Snowpark Container Services service (or a job service).
     * <p><b>200</b> - successful
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec showServiceEndpointsWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name) throws WebClientResponseException {
        return showServiceEndpointsRequestCreation(database, schema, name);
    }

    /**
     * 
     * Suspend a service.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param ifExists Parameter that specifies how to handle the request for a resource that does not exist: - &#x60;true&#x60;: The endpoint does not throw an error if the resource does not exist. It returns a 200 success response, but does not take any action on the resource. - &#x60;false&#x60;: The endpoint throws an error if the resource doesn&#39;t exist.
     * @return SuccessResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    private ResponseSpec suspendServiceRequestCreation(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Boolean ifExists) throws WebClientResponseException {
        Object postBody = null;
        // verify the required parameter 'database' is set
        if (database == null) {
            throw new WebClientResponseException("Missing the required parameter 'database' when calling suspendService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'schema' is set
        if (schema == null) {
            throw new WebClientResponseException("Missing the required parameter 'schema' when calling suspendService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // verify the required parameter 'name' is set
        if (name == null) {
            throw new WebClientResponseException("Missing the required parameter 'name' when calling suspendService", HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), null, null, null);
        }
        // create path and map variables
        final Map<String, Object> pathParams = new HashMap<String, Object>();

        pathParams.put("database", database);
        pathParams.put("schema", schema);
        pathParams.put("name", name);

        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        queryParams.putAll(apiClient.parameterToMultiValueMap(null, "ifExists", ifExists));

        final String[] localVarAccepts = { 
            "application/json"
        };
        final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
        final String[] localVarContentTypes = { };
        final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

        String[] localVarAuthNames = new String[] { "ExternalOAuth", "KeyPair", "SnowflakeOAuth" };

        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return apiClient.invokeAPI("/api/v2/databases/{database}/schemas/{schema}/services/{name}:suspend", HttpMethod.POST, pathParams, queryParams, postBody, headerParams, cookieParams, formParams, localVarAccept, localVarContentType, localVarAuthNames, localVarReturnType);
    }

    /**
     * 
     * Suspend a service.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param ifExists Parameter that specifies how to handle the request for a resource that does not exist: - &#x60;true&#x60;: The endpoint does not throw an error if the resource does not exist. It returns a 200 success response, but does not take any action on the resource. - &#x60;false&#x60;: The endpoint throws an error if the resource doesn&#39;t exist.
     * @return SuccessResponse
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<SuccessResponse> suspendService(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Boolean ifExists) throws WebClientResponseException {
        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return suspendServiceRequestCreation(database, schema, name, ifExists).bodyToMono(localVarReturnType);
    }

    /**
     * 
     * Suspend a service.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param ifExists Parameter that specifies how to handle the request for a resource that does not exist: - &#x60;true&#x60;: The endpoint does not throw an error if the resource does not exist. It returns a 200 success response, but does not take any action on the resource. - &#x60;false&#x60;: The endpoint throws an error if the resource doesn&#39;t exist.
     * @return ResponseEntity&lt;SuccessResponse&gt;
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public Mono<ResponseEntity<SuccessResponse>> suspendServiceWithHttpInfo(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Boolean ifExists) throws WebClientResponseException {
        ParameterizedTypeReference<SuccessResponse> localVarReturnType = new ParameterizedTypeReference<SuccessResponse>() {};
        return suspendServiceRequestCreation(database, schema, name, ifExists).toEntity(localVarReturnType);
    }

    /**
     * 
     * Suspend a service.
     * <p><b>200</b> - Successful request.
     * <p><b>202</b> - Successfully accepted the request, but it is not completed yet.
     * <p><b>400</b> - Bad Request. The request payload is invalid or malformed. This happens if the application didn&#39;t send the correct request payload. The response body may include the error code and message indicating the actual cause. The application must reconstruct the request body for retry.
     * <p><b>401</b> - Unauthorized. The request is not authorized. This happens if the attached access token is invalid or missing. The response body may include the error code and message indicating the actual cause, e.g., expired, invalid token. The application must obtain a new access token for retry.
     * <p><b>403</b> - Forbidden. The request is forbidden. This can also happen if the request is made even if the API is not enabled.
     * <p><b>404</b> - Not Found. The request endpoint is not valid. This happens if the API endpoint does not exist, or if the API is not enabled.
     * <p><b>405</b> - Method Not Allowed. The request method doesn&#39;t match the supported API. This happens, for example, if the application calls the API with GET method but the endpoint accepts only POST.
     * <p><b>429</b> - Limit Exceeded. The number of requests hit the rate limit. The application must slow down the frequency of hitting the API endpoints.
     * <p><b>500</b> - Internal Server Error. The server hit an unrecoverable system error. The response body may include the error code and message for further guidance. The application owner may need to reach out the customer support.
     * <p><b>503</b> - Service Unavailable. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * <p><b>504</b> - Gateway Timeout. The request was not processed due to server side timeouts. The application may retry with backoff. The jittered backoff is recommended.
     * @param database Identifier (i.e. name) for the database to which the resource belongs. You can use the &#x60;/api/v2/databases&#x60; GET request to get a list of available databases.
     * @param schema Identifier (i.e. name) for the schema to which the resource belongs. You can use the &#x60;/api/v2/databases/{database}/schemas&#x60; GET request to get a list of available schemas for the specified database.
     * @param name Identifier (i.e. name) for the resource.
     * @param ifExists Parameter that specifies how to handle the request for a resource that does not exist: - &#x60;true&#x60;: The endpoint does not throw an error if the resource does not exist. It returns a 200 success response, but does not take any action on the resource. - &#x60;false&#x60;: The endpoint throws an error if the resource doesn&#39;t exist.
     * @return ResponseSpec
     * @throws WebClientResponseException if an error occurs while attempting to invoke the API
     */
    public ResponseSpec suspendServiceWithResponseSpec(@javax.annotation.Nonnull String database, @javax.annotation.Nonnull String schema, @javax.annotation.Nonnull String name, @javax.annotation.Nullable Boolean ifExists) throws WebClientResponseException {
        return suspendServiceRequestCreation(database, schema, name, ifExists);
    }
}
